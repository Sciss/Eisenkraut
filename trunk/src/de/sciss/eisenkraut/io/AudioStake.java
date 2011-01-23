/*
 *  AudioStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		22-Dec-05	created from TrackSpan
 */

package de.sciss.eisenkraut.io;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.unicode.Normalizer;

import de.sciss.io.CacheManager;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.BasicStake;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 *
 *  @see		de.sciss.io.Span
 */
public abstract class AudioStake
extends BasicStake
{
	private static final boolean			DEBUG				= true;

	private boolean							disposed			= false;
	
	private static final List				allStakes			= new ArrayList();
	
	protected static final Normalizer		fileNameNormalizer	= new Normalizer( Normalizer.C, false );
		
	private StackTraceElement[]				debugTrace;
		
	/**
	 */
	protected AudioStake( Span span )
	{
		super( span );
		if( DEBUG ) {
			allStakes.add( this );
			debugTrace = new Throwable().getStackTrace();
		}
	}

	public void dispose()
	{
		disposed	= true;
		if( DEBUG ) allStakes.remove( this );
		super.dispose();
	}
	
	public static void debugCheckDisposal()
	{
		if( !DEBUG ) {
			System.err.println( "AudioStake.debugCheckDisposal() : not possible (set DEBUG to true!)" );
			return;
		}
		System.err.println( "======= There are " + allStakes.size() + " undisposed stakes. ======= dump:" );
		for( int i = 0; i < allStakes.size(); i++ ) {
			((AudioStake) allStakes.get( i )).debugDump();
		}
	}
	
//	public abstract Stake replaceStart( long newStart );
//	public abstract Stake replaceStop( long newStop );
//	public abstract Stake shiftVirtual( long delta );
		
	// in anlehnung an InterleavedStreamFile
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
	
	private static class ActionDebugDump
	extends AbstractAction
	{
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
