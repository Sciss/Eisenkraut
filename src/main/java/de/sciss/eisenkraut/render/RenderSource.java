/*
 *  RenderSource.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.render;

import java.util.List;

import de.sciss.io.Span;
import de.sciss.timebased.Trail;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.timeline.Track;

/**
 *  A RenderSource describes the source
 *  data for generating rendering output.
 *  This data is restricted to dynamic
 *  scalar vector data, i.e. data that
 *  changes over time : sense data
 *  (a receiver's sensibility at a point
 *  described by a transmitter trajectory)
 *  and trajectory data of a transmitter.
 */
public class RenderSource {
    public final int		numAudioChannels;
    public final boolean[]	audioTrackMap;

    /**
     *  The blockSpan describes the
     *  current time span of the provided data
     *  in the source rate sense.
     *  Thus, blockSpan.getLength() equals
     *  blockBufLen
     */
    public Span				blockSpan;
    /**
     */
    public float[][]		audioBlockBuf;
    /**
     *  Offset to use when reading data
     *  from blockBuf
     */
    public int				audioBlockBufOff;
    /**
     *  Length to use when reading data
     *  from blockBuf
     */
    public int				audioBlockBufLen;

    public float[][]		clipboardBuf;	// offset + len identical to blockBufOff/Len !

    public final RenderContext	context;

    public Trail			markers;

    public boolean			validAudio;
    public boolean			validMarkers;

    /**
     *  Constructs a new RenderSource, where
     *  the arrays are pre-allocated for the
     *  given number of transmitters and receivers.
     *  Note that the final vectors are not
     *  initialized, i.e. senseBlockBuf will
     *  become new float[numTrns][numRcv][] etc.
     *  All request fields are set to false by default.
     *
     *  @param 	context	...
     */
    public RenderSource(RenderContext context) {
        this.context = context;

        final List<Track.Info> trackInfo	= context.getTrackInfo();
        int				ch			= 0;
        boolean[]		tm			= null;

        for (Track.Info ti : trackInfo) {
            if (ti.trail instanceof AudioTrail) {
                ch = ti.numChannels;
                tm = ti.trackMap;
            }
        }
        numAudioChannels	= ch;
        audioTrackMap		= tm;
        audioBlockBuf 		= new float[numAudioChannels][];
    }
}