/*
 *  MultiMappedAudioStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import de.sciss.io.CacheManager;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.Stake;

import java.io.IOException;
import java.util.Arrays;

public class MultiMappedAudioStake extends AudioStake {

    private final InterleavedStreamFile[]   fs;
    private final Span[]                    fileSpans;
    private final Span[]                    maxFileSpans;
    private final int[][]                   channelMaps;
    private final float[][][]               mappedData;
    private final int                       numChannels;

    private final String[]                  fileNames;

    private final static int BUF_SIZE = 8192;

    public MultiMappedAudioStake(Span span, InterleavedStreamFile[] fs, Span[] fileSpans) {
        this(span, fs, fileSpans, createChannelMaps(fs));
    }

    // make sure channelMaps is never modified!
    public MultiMappedAudioStake(Span span, InterleavedStreamFile[] fs, Span[] fileSpans, int[][] channelMaps) {
        this(span, fs, fileSpans, fileSpans, channelMaps, getFileNames(fs));
    }

    private MultiMappedAudioStake(Span span, InterleavedStreamFile[] fs, Span[] fileSpans,
                                  Span[] maxFileSpans, int[][] channelMaps, String[] fileNames) {
        super(span);

        int numCh = 0;

        this.fs             = fs;
        this.fileSpans      = fileSpans;
        this.maxFileSpans   = maxFileSpans;
        this.channelMaps    = channelMaps;
        mappedData = new float[fs.length][][];
        for (int i = 0; i < fs.length; i++) {
            mappedData[i] = new float[fs[i].getChannelNum()][];
            numCh += channelMaps[i].length;
        }
        this.numChannels    = numCh;
        this.fileNames      = fileNames;
    }

    public void close()
            throws IOException {
        for (InterleavedStreamFile f : fs) f.close();
    }

    public void cleanUp() {
        for (InterleavedStreamFile f : fs) {
            try {
                f.close();
            } catch (IOException e1) { /* ignore */ }
        }
    }

    private static String[] getFileNames(InterleavedStreamFile[] fs) {
        final String[] fileNames = new String[fs.length];
        for (int i = 0; i < fs.length; i++) {
            final String s = fs[i].getFile().getAbsolutePath();
            fileNames[i] = s; // fileNameNormalizer.normalize(s, sb);
        }
        return fileNames;
    }

    // consider result read-only!!!
    public int[][] getChannelMaps() {
        return channelMaps;
    }

    private static int[][] createChannelMaps(InterleavedStreamFile[] fs) {
        final int[][] channelMaps = new int[fs.length][];
        int[] channelMap;

        for (int i = 0; i < fs.length; i++) {
            channelMap = new int[fs[i].getChannelNum()];
            for (int j = 0; j < channelMap.length; j++) {
                channelMap[j] = j;
            }
            channelMaps[i] = channelMap;
        }

        return channelMaps;
    }

    public Stake duplicate() {
        return new MultiMappedAudioStake(span, fs, fileSpans, maxFileSpans, channelMaps, fileNames);
    }

    public Stake replaceStart(long newStart) {
        final long delta = newStart - span.start;
        final Span[] newFileSpans = new Span[fileSpans.length];
        final Span newSpan = span.replaceStart(newStart);

        if (newSpan.getLength() < 0) throw new IllegalArgumentException(String.valueOf(newStart));

        for (int i = 0; i < fileSpans.length; i++) {
            newFileSpans[i] = fileSpans[i].replaceStart(fileSpans[i].start + delta);
            if ((newFileSpans[i].getLength() < 0) || !maxFileSpans[i].contains(newFileSpans[i])) {
                throw new IllegalArgumentException(String.valueOf(newStart));
            }
        }
        return new MultiMappedAudioStake(newSpan, fs, newFileSpans, maxFileSpans, channelMaps, fileNames);
    }

    public Stake replaceStop(long newStop) {
        final long delta = newStop - span.stop;
        final Span newSpan = span.replaceStop(newStop);
        final Span[] newFileSpans = new Span[fileSpans.length];

        if (newSpan.getLength() < 0) throw new IllegalArgumentException(String.valueOf(newStop));

        for (int i = 0; i < fileSpans.length; i++) {
            newFileSpans[i] = fileSpans[i].replaceStop(fileSpans[i].stop + delta);
            if ((newFileSpans[i].getLength() < 0) || !maxFileSpans[i].contains(newFileSpans[i])) {
                throw new IllegalArgumentException(String.valueOf(newStop));
            }
        }
        return new MultiMappedAudioStake(newSpan, fs, newFileSpans, maxFileSpans, channelMaps, fileNames);
    }

    public Stake shiftVirtual(long delta) {
        return new MultiMappedAudioStake(span.shift(delta), fs, fileSpans, maxFileSpans, channelMaps, fileNames);
    }

    public int readFrames(float[][] data, int offset, Span readSpan)
            throws IOException {

        final int len = (int) readSpan.getLength();
        if (len == 0) return 0;

        InterleavedStreamFile   f;
        long                    fOffset;
        int[]                   channelMap;
        float[][]               mappedData;

        synchronized (fs) {    // protects mappedDatas
            for (int i = 0, j = 0; i < fs.length; i++) {
                f           = fs[i];
                channelMap  = channelMaps[i];
                mappedData  = this.mappedData[i];
                fOffset     = fileSpans[i].start + readSpan.start - span.start;

                if ((fOffset < fileSpans[i].start) || ((fOffset + len) > fileSpans[i].stop)) {
                    throw new IllegalArgumentException(fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[i].toString());
                }

                synchronized (f) {
                    if (f.getFramePosition() != fOffset) {
                        f.seekFrame(fOffset);
                    }
                    for (int k = 0; k < channelMap.length; k++, j++) {
                        mappedData[channelMap[k]] = data[j];
                    }
                    f.readFrames(mappedData, offset, len);
                }
            }
            clearMappedData();	// avoid memory footprint
        }
        return len;
    }

    public int writeFrames(float[][] data, int offset, Span writeSpan)
            throws IOException {

        final int len = (int) writeSpan.getLength();
        if (len == 0) return 0;

        InterleavedStreamFile   f;
        long                    fOffset;
        int[]                   channelMap;
        float[][]               mappedData;

        synchronized (fs) {    // protects mappedDatas
            for (int i = 0, j = 0; i < fs.length; i++) {
                f           = fs[i];
                channelMap  = channelMaps[i];
                mappedData  = this.mappedData[i];
                fOffset     = fileSpans[i].start + writeSpan.start - span.start;

                if ((fOffset < fileSpans[i].start) || ((fOffset + len) > fileSpans[i].stop)) {
                    throw new IllegalArgumentException(fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[i].toString());
                }

                synchronized (f) {
                    if (f.getFramePosition() != fOffset) {
                        f.seekFrame(fOffset);
                    }
                    for (int k = 0; k < channelMap.length; k++, j++) {
                        mappedData[channelMap[k]] = data[j];
                    }
                    f.writeFrames(mappedData, offset, len);
                }
            }
            clearMappedData();	// avoid memory footprint
        }
        return len;
    }

    public long copyFrames(InterleavedStreamFile target, Span readSpan)
            throws IOException {

        final long len = readSpan.getLength();
        if (len == 0) return 0;

        InterleavedStreamFile   f;
        long                    fOffset;
        int[]                   channelMap;
        float[][]               mappedData;
        int                     chunkLen;
        float[][]               data            = new float[numChannels][(int) Math.min(BUF_SIZE, len)];
        long                    framesCopied    = 0;

        do {
            synchronized (fs) {    // protects mappedDatas
                for (int i = 0, j = 0; i < fs.length; i++) {
                    f           = fs[i];
                    channelMap  = channelMaps[i];
                    mappedData  = this.mappedData[i];
                    fOffset     = fileSpans[i].start + readSpan.start - span.start;

                    if ((fOffset < fileSpans[i].start) || ((fOffset + len) > fileSpans[i].stop)) {
                        throw new IllegalArgumentException(fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[i].toString());
                    }

                    chunkLen = (int) Math.min(BUF_SIZE, len - framesCopied);
                    synchronized (f) {
                        if (f.getFramePosition() != fOffset) {
                            f.seekFrame(fOffset);
                        }
                        for (int k = 0; k < channelMap.length; k++, j++) {
                            mappedData[channelMap[k]] = data[j];
                        }
                        f.readFrames(mappedData, 0, chunkLen);
                    }
                    target.writeFrames(data, 0, chunkLen);
                    framesCopied += chunkLen;
                }
            }
            clearMappedData();    // minimize memory footprint
        } while (framesCopied < len);

        return len;
    }

    public void addBufferReadMessages(OSCBundle bndl, Span readSpan, Buffer[] bufs, int bufOff) {
        final int len = (int) readSpan.getLength();
        if (len == 0) return;

        long fOffset;

        if (bufs.length != fs.length) {
            throw new IllegalArgumentException("Wrong # of buffers (" + bufs.length + " != " + fs.length + ")");
        }

        for (int i = 0; i < fs.length; i++) {
            fOffset = fileSpans[i].start + readSpan.start - span.start;

            if ((fOffset < fileSpans[i].start) || ((fOffset + len) > fileSpans[i].stop)) {
                throw new IllegalArgumentException(fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[i].toString());
            }

            if ((bufs[i].getNumChannels() != fs[i].getChannelNum())) {
                throw new IllegalArgumentException("Channel mismatch (" + bufs[i].getNumChannels() + " != " + fs[i].getChannelNum());
            }

            bndl.addPacket(bufs[i].readMsg(fileNames[i], fOffset, len, bufOff));
        }
    }

    // synchronization : caller must have sync on fs
    private void clearMappedData() {
        for (int i = 0; i < mappedData.length; i++) {
            Arrays.fill(mappedData[i], null);
        }
    }

    public void flush()
            throws IOException {
        for (int i = 0; i < fs.length; i++) {
            final InterleavedStreamFile f           = fs[i];
            final Span                  fileSpan    = fileSpans[i];
            synchronized (f) {
                // this is important because AudioTrail will otherwise assume
                // that the next available file span overlaps with the current one!
                // (https://github.com/Sciss/Eisenkraut/issues/7)
                if (f.getFramePosition() != fileSpan.getStop()) {
                    f.seekFrame(fileSpan.getStop());
                }
                f.flush();
            }
        }
    }

    public void addToCache(CacheManager cm) {
        for (InterleavedStreamFile f : fs) {
            cm.addFile(f.getFile());
        }
    }

    public int getChannelNum() {
        return numChannels;
    }

    public void debugDump() {
        debugDumpBasics();
        System.err.print(" ; fs = [ ");
        for (int i = 0; i < fs.length; i++) {
            System.err.print((i > 0 ? ", " : "") + fs[i].getFile().getName() + " (file span " + fileSpans[i].toString() + " )");
        }
        System.err.println(" ]");
    }
}