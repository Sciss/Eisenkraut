/*
 *  DecimatedWaveStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import java.io.IOException;

import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;

import de.sciss.timebased.BasicStake;
import de.sciss.timebased.Stake;
import de.sciss.util.MutableInt;
import de.sciss.util.MutableLong;

public class DecimatedStake extends BasicStake {
	private final InterleavedStreamFile[]	fs;
	private final Span[]					fileSpans;
	private final Span[]					maxFileSpans;
	private final MutableLong[]				framesWritten;
	private final Span[]					biasedSpans;
	private final DecimationHelp[]			decimations;
	private final int						SUBNUM;

	public DecimatedStake(Span span, InterleavedStreamFile[] fs, Span[] fileSpans, Span[] biasedSpans,
						  DecimationHelp[] decimations) {
		this(span, fs, fileSpans, fileSpans, null, biasedSpans, decimations);
	}
	
	private DecimatedStake( Span span, InterleavedStreamFile[] fs, Span[] fileSpans,
								Span[] maxFileSpans, MutableLong[] framesWritten, Span[] biasedSpans,
								DecimationHelp[] decimations )
	{
		super( span );

		this.fs				= fs;
		this.fileSpans		= fileSpans;
		this.maxFileSpans	= maxFileSpans;
		if( framesWritten == null ) {
			this.framesWritten = new MutableLong[ fs.length ];
			for( int i = 0; i < fs.length; i++ ) this.framesWritten[ i ] = new MutableLong( 0L );
		} else {
			this.framesWritten	= framesWritten;
		}
		this.biasedSpans	= biasedSpans;
		this.decimations	= decimations;
		
		SUBNUM				= decimations.length;
	}

	public void dispose() {
		// XXX
		super.dispose();
	}

	public Stake duplicate()
	{
		return new DecimatedStake( span, fs, fileSpans, maxFileSpans, framesWritten, biasedSpans, decimations );
	}

	public Stake replaceStart( long newStart )
	{
		final Span[] newBiasedSpans	= new Span[ SUBNUM ];
		final Span[] newFileSpans	= new Span[ SUBNUM ];
		long testBias, newBiasedStart, delta;
		DecimationHelp decim;
		
		for( int i = 0; i < SUBNUM; i++ ) {
			decim				= decimations[ i ];
			testBias			= biasedSpans[ i ].start + ((newStart - span.start + decim.roundAdd) & decim.mask) - newStart;
			newBiasedStart		= newStart + (testBias < -decim.roundAdd ? testBias + decim.factor :
											 (testBias > decim.roundAdd ? testBias - decim.factor : testBias));
			delta				= (newBiasedStart - biasedSpans[ i ].start) >> decim.shift;
			newBiasedSpans[ i ]	= biasedSpans[ i ].replaceStart( newBiasedStart );
			newFileSpans[ i ]	= fileSpans[ i ].replaceStart( fileSpans[ i ].start + delta );
			// XXX modify framesWritten ?
		}
		return new DecimatedStake( span.replaceStart( newStart ), fs, newFileSpans, maxFileSpans, framesWritten, newBiasedSpans, decimations );
	}

	public Stake replaceStop( long newStop )
	{
		final Span[] newBiasedSpans	= new Span[ SUBNUM ];
		final Span[] newFileSpans	= new Span[ SUBNUM ];
		long testBias, newBiasedStop; // , delta;
		int	startBias;
		DecimationHelp decim;

		for( int i = 0; i < SUBNUM; i++ ) {
			decim				= decimations[ i ];
			startBias			= (int) (biasedSpans[ i ].start - span.start);
			testBias			= (int) (((startBias + newStop + decim.roundAdd) & decim.mask) - newStop);
			newBiasedStop		= newStop + (testBias < -decim.roundAdd ? testBias + decim.factor :
											(testBias > decim.roundAdd ? testBias - decim.factor : testBias));
			newBiasedSpans[ i ]	= biasedSpans[ i ].replaceStop( newBiasedStop );
			newFileSpans[ i ]	= fileSpans[ i ].replaceStop( fileSpans[ i ].start + newBiasedSpans[ i ].getLength() ); // XXX richtig?
		}
		return new DecimatedStake( span.replaceStop( newStop ), fs, newFileSpans, maxFileSpans, framesWritten, newBiasedSpans, decimations );
	}

	public Stake shiftVirtual( long delta )
	{
		final Span[] newBiasedSpans	= new Span[ SUBNUM ];

		for( int i = 0; i < SUBNUM; i++ ) {
			newBiasedSpans[ i ]	= biasedSpans[ i ].shift( delta );
		}
		return new DecimatedStake( span.shift( delta ), fs, fileSpans, maxFileSpans, framesWritten, newBiasedSpans, decimations );
	}

	public void readFrames( int sub, float[][] data, int dataOffset, Span readSpan, MutableInt framesRead, MutableInt framesBusy )
	throws IOException
	{
		if( data.length == 0 ) {
			framesRead.set( 0 );
			framesBusy.set( 0 );
			return;
		}
		
		final DecimationHelp decim	= decimations[ sub ];
		final int	startBias		= (int) (biasedSpans[ sub ].start - span.start);
		final int	newStartBias	= (int) (((readSpan.start + decim.roundAdd) & decim.mask) - readSpan.start) + startBias;
		final long	newBiasedStart	= readSpan.start + (newStartBias < -decim.roundAdd ? newStartBias + decim.factor :
													   (newStartBias > decim.roundAdd ? newStartBias - decim.factor : newStartBias));
		final long	fOffset			= fileSpans[ sub ].start + ((newBiasedStart - (span.start + startBias)) >> decim.shift);
		final int	newStopBias		= (int) (((startBias + readSpan.stop + decim.roundAdd) & decim.mask) - readSpan.stop);
		final long	newBiasedStop	= readSpan.stop + (newStopBias < -decim.roundAdd ? newStopBias + decim.factor :
													  (newStopBias > decim.roundAdd ? newStopBias - decim.factor : newStopBias));
		final int	len				= (int) Math.min( data[0].length - dataOffset, (newBiasedStop - newBiasedStart) >> decim.shift );
		final int	readyLen;
		
		if( len <= 0 ) {
			framesRead.set( 0 );
			framesBusy.set( 0 );
			return;
		}

		synchronized( fs ) {
			readyLen = (int) Math.min( len, Math.max( 0, fileSpans[ sub ].start + framesWritten[ sub ].value() - fOffset ));
			if( readyLen > 0 ) {
				
				if( fs[ sub ].getFramePosition() != fOffset ) {
					fs[ sub ].seekFrame( fOffset );
				}
	// XXX TEST
	//			fs[ sub ].readFrames( data, dataOffset, len );
	//			readyLen = (int) Math.min( len, framesWritten[ sub ].value() - fOffset );
				fs[ sub ].readFrames( data, dataOffset, readyLen );
			}
		}
		
		framesRead.set( readyLen );
		framesBusy.set( len - readyLen );
	}

	public boolean readFrame(int sub, float[][] data, int dataOffset, long pos)
			throws IOException {

		final DecimationHelp decim	= decimations[ sub ];
		final int	startBias		= (int) (biasedSpans[ sub ].start - span.start);
		final int	newStartBias	= (int) (((pos + decim.roundAdd) & decim.mask) - pos) + startBias;
		final long	newBiasedStart	= pos + (newStartBias < -decim.roundAdd ? newStartBias + decim.factor :
										    (newStartBias > decim.roundAdd ? newStartBias - decim.factor : newStartBias));
		final long	fOffset			= fileSpans[ sub ].start + ((newBiasedStart - (span.start + startBias)) >> decim.shift);
		final int	readyLen;
	
		synchronized( fs ) {
			readyLen = (int) Math.min( 1, fileSpans[ sub ].start + framesWritten[ sub ].value() - fOffset );
			if( readyLen == 1 ) {
				if( fs[ sub ].getFramePosition() != fOffset ) {
					fs[ sub ].seekFrame( fOffset );
				}
				fs[ sub ].readFrames( data, dataOffset, 1 );
				return true;
			} else {
				return false;
			}
		}
	}

	public void continueWrite(int sub, float[][] data, int dataOffset, int len)
			throws IOException {
		if (len == 0) return; // return 0;

		synchronized (fs) {
			final long fOffset = fileSpans[sub].start + framesWritten[sub].value();

			if( (fOffset < fileSpans[ sub ].start) || ((fOffset + len) > fileSpans[ sub ].stop) ) {
				throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[ sub ].toString() );
			}

			if( fs[ sub ].getFramePosition() != fOffset ) {
				fs[ sub ].seekFrame( fOffset );
			}
			fs[ sub ].writeFrames( data, dataOffset, len );
			
			framesWritten[ sub ].add( len );
		}
	}

	public void flush()
			throws IOException {
		synchronized (fs) {
			for (InterleavedStreamFile f : fs) {
				f.flush();
			}
		}
	}

	public void debugDump()
	{
		debugDumpBasics();
		for( int i = 0; i < SUBNUM; i++ ) {
			System.err.println( "  decim "+decimations[i].factor+" biased span "+biasedSpans[i].toString()+
				"; f = " + fs[i].getFile().getName() + " (file span " + fileSpans[i].toString() + " )" );
		}
	}
	
	protected void debugDumpBasics()
	{
		System.err.println( "Span " + span.toString() );
	}
}