/*
 *  AbstractRenderPlugIn.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JComponent;

import de.sciss.app.AbstractApplication;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;

public abstract class AbstractRenderPlugIn implements RenderPlugIn {
    private List<AudioFile> collTempFiles	= null;	// lazy creation

    protected Preferences	prefs = null;

    /**
     *	Default behaviour : POLICY_MODIFY
     */
    public int getAudioPolicy()
    {
        return POLICY_MODIFY;
    }

    /**
     *	Default behaviour : POLICY_BYPASS
     */
    public int getUnselectedAudioPolicy()
    {
        return POLICY_MODIFY;
    }

    /**
     *	Default behaviour : POLICY_BYPASS
     */
    public int getMarkerPolicy()
    {
        return POLICY_BYPASS;
    }

    /**
     *	Default behaviour : POLICY_BYPASS
     */
    public int getLengthPolicy()
    {
        return POLICY_BYPASS;
    }

    /**
     *	Default behaviour : no user parameters (false)
     */
    public boolean hasUserParameters()
    {
        return false;
    }

    /**
     *	Default behaviour : shouldn't display parameters (false)
     */
    public boolean shouldDisplayParameters()
    {
        return false;
    }

    /**
     *	Sub-classes should call super.init() !
     */
    public void init( Preferences p )
    {
        this.prefs	= p;
    }

    /**
     *	Sub-classes should call super.init() !
     */
    public void dispose()
    {
        /* empty */
    }

    /**
     *	Default behaviour : returns null (no GUI)
     */
    public JComponent getSettingsView( RenderContext context )
    {
        return null;
    }

    /**
     *	Default behaviour : simply calls consumer.consumerBegin()
     */
    public boolean producerBegin( RenderSource source )
    throws IOException
    {
        return source.context.getConsumer().consumerBegin( source );
    }

    /**
     *	Default behaviour : simply calls consumer.consumerRender(), i.e. bypass
     */
    public boolean producerRender( RenderSource source )
    throws IOException
    {
        return source.context.getConsumer().consumerRender( source );
    }

    /**
     *	Default behaviour : simply calls consumer.consumerFinish() and
     *	delete all temp files
     */
    public boolean producerFinish( RenderSource source )
    throws IOException
    {
        try {
            return source.context.getConsumer().consumerFinish( source );
        }
        finally {
            deleteAllTempFiles();
        }
    }

    /**
     * Default behaviour : simply calls consumer.consumerCancel() and
     * delete all temp files
     */
    public void producerCancel(RenderSource source)
            throws IOException {
        try {
            source.context.getConsumer().consumerCancel(source);
        } finally {
            deleteAllTempFiles();
        }
    }

    private void deleteAllTempFiles() {
        if (this.collTempFiles != null) {
            while (!this.collTempFiles.isEmpty()) {
                deleteTempFile(this.collTempFiles.get(0));
            }
        }
    }

    protected void deleteTempFile(AudioFile /* InterleavedStreamFile */ f) {
        if (this.collTempFiles != null) this.collTempFiles.remove(f);
        try {
            f.close();
        } catch (IOException e1) { /* ignore */ }
        f.getFile().delete();
    }

    protected AudioFile createTempFile(int numChannels, double rate)
            throws IOException {
        final AudioFileDescr afd = new AudioFileDescr();
        AudioFile af;

        afd.type			= AudioFileDescr.TYPE_AIFF;
        afd.channels		= numChannels;
        afd.rate			= rate;
        afd.bitsPerSample	= 32;
        afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
        afd.file			= IOUtil.createTempFile( "fsc", ".aif" );
        af					= AudioFile.openAsWrite( afd );

        if (this.collTempFiles == null) {
            this.collTempFiles = new ArrayList<AudioFile>();
        }
        this.collTempFiles.add(af);
        return af;
    }

    protected static void fill(float[][] buf, int off, int len, float value) {
        for (float[] chBuf : buf) {
            for (int i = off, j = off + len; i < j; ) {
                chBuf[i++] = value;
            }
        }
    }

    protected static void multiply(float[][] buf, int off, int len, float value) {
        for (float[] chBuf : buf) {
            for (int i = off, j = off + len; i < j; ) {
                chBuf[i++] *= value;
            }
        }
    }

    public static void multiply(float[] buf, int off, int len, float value) {
        for (int i = off + len; off < i; ) {
            buf[off++] *= value;
        }
    }

    protected static void add(float[][] bufA, int offA, float[][] bufB, int offB, int len) {
        float[] chBuf1, chBuf2;

        for (int ch = 0; ch < bufA.length; ch++) {
            chBuf1 = bufA[ch];
            chBuf2 = bufB[ch];
            for (int i = offA, j = offB, k = offA + len; i < k; ) {
                chBuf1[i++] += chBuf2[j++];
            }
        }
    }

    public static void add(float[] bufA, int offA, float[] bufB, int offB, int len) {
        for (int i = offA + len; offA < i; ) {
            bufA[offA++] += bufB[offB++];
        }
    }

    protected static void subtract(float[][] bufA, int offA, float[][] bufB, int offB, int len) {
        float[] chBuf1, chBuf2;

        for (int ch = 0; ch < bufA.length; ch++) {
            chBuf1 = bufA[ch];
            chBuf2 = bufB[ch];
            for (int i = offA, j = offB, k = offA + len; i < k; ) {
                chBuf1[i++] -= chBuf2[j++];
            }
        }
    }

    protected static String getResourceString(String key) {
        return AbstractApplication.getApplication().getResourceString(key);
    }
}