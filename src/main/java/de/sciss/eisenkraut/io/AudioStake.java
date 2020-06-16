/*
 *  AudioStake.java
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

package de.sciss.eisenkraut.io;

import de.sciss.io.CacheManager;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.BasicStake;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  @see		de.sciss.io.Span
 */
public abstract class AudioStake
        extends BasicStake {

    private static final boolean			DEBUG				= false;

    private boolean							disposed			= false;

    private static final List<AudioStake> allStakes			= new ArrayList<AudioStake>();

    private StackTraceElement[]				debugTrace;

    protected AudioStake(Span span) {
        super(span);
        if (DEBUG) {
            allStakes.add(this);
            debugTrace = new Throwable().getStackTrace();
        }
    }

    public void dispose() {
        disposed = true;
        if (DEBUG) allStakes.remove(this);
        super.dispose();
    }

    public static void debugCheckDisposal() {
        if (DEBUG) {
            System.err.println("======= There are " + allStakes.size() + " un-disposed stakes. ======= dump:");
            for (AudioStake allStake : allStakes) {
                allStake.debugDump();
            }
        } else {
            System.err.println("AudioStake.debugCheckDisposal() : not possible (set DEBUG to true!)");
        }
    }

    // similar to InterleavedStreamFile
    public abstract int readFrames( float[][] data, int dataOffset, Span readSpan ) throws IOException;
    // XXX writeSpan should be replaced by framesWritten internally for simplicity
    public abstract int writeFrames( float[][] data, int dataOffset, Span writeSpan ) throws IOException;
    public abstract long copyFrames( InterleavedStreamFile target, Span readSpan ) throws IOException;
    public abstract int getChannelNum();
    public abstract void flush() throws IOException;
    public abstract void addBufferReadMessages( OSCBundle bndl, Span s, Buffer[] bufs, int bufOff );

    public abstract void debugDump();

    public abstract void close() throws IOException;
    public abstract void cleanUp();

    public abstract void addToCache( CacheManager cm );

    protected void debugDumpBasics()
    {
        System.err.print( "Span " + span.getStart() + " ... " + span.getStop() + "; disposed ? " + disposed );
        if( (debugTrace != null) && (debugTrace.length > 2) ) {
            System.err.println( "; created : " );
            for( int i = 2; i < debugTrace.length; i++ ) {
                System.err.println( "  " + debugTrace[ i ]);
            }
        }
    }

    public static Action getDebugDumpAction()
    {
        return new ActionDebugDump();
    }

    @SuppressWarnings("serial")
    private static class ActionDebugDump
            extends AbstractAction {
        protected ActionDebugDump()
        {
            super( "Dump Undisposed Audio Stakes" );
        }

        public void actionPerformed( ActionEvent e )
        {
            debugCheckDisposal();
        }
    }

} // class AudioStake
