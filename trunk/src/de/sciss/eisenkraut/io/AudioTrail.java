/*
 *  AudioTrail.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004 Hanns Holger Rutz. All rights reserved.
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
 *		22-Dec-05	created from MultirateTrackEditor
 */

package de.sciss.eisenkraut.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.sciss.eisenkraut.session.Session;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.common.ProcessingThread;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.BasicTrail;
import de.sciss.timebased.Trail;

/**
 *  This class provides means for automatic multirate handling
 *  of nondestructive nonlinear track editing objects.
 *  It wraps a number (currently 7) of track editors representing
 *  the same signal at different decimation stages where
 *  one file represents fullrate data and each subsampled
 *  file decimates the rate by 4. Thus, if fullrate corresponds
 *  to 1024 Hz sampling rate, the first subsampled file is
 *  a decimation to 256 Hz, the second subsampled file is
 *  a decimation to 64 Hz etc. So, using 6 subsampled files
 *  goes down to 1/4096th of the fullrate. Taking the unusual
 *  case that a user would use audio rate for sense data, say
 *  48 kHz, then the lowest resolution subsample file will run
 *  at about 12 Hz, so if a GUI element request data for a very
 *  long time span, say half an hour, it would have to handle
 *  a buffer of 21094 frames; in the more usual case of a sense
 *  rate of say 4800 Hz (or less), one hour could still be represented
 *  by 4219 frames, thus maintaining low RAM and CPU consumption.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 12-Nov-07
 */
public class AudioTrail
extends BasicTrail
{
//protected static final boolean DEBUG = false;
//
//
//	private final int channels, modelChannels;
//	private final float rate;
//	private final int model;
//	private final int[] decimations;
//
//	// this array of size 'SUBNUM' contains STEs
//	// that keep track of the subsampled versions of
//	// this STE's track data. ste[0] is full rate,
//	// ste[1] is 4x subsampled, ste[2] is 16x subsampled etc.
//	private final DecimatedSampledTrack[] ste;
//	private final int SUBNUM;
//	private final int MAXSHIFT;
//	private final int MAXCOARSE;
//	private final long MAXMASK;
//	private final int MAXCEILADD;
//
//	private final float[][] tmpBuf;
//	private final int tmpBufSize;
//	private final float[][] tmpBuf2;
//	private final int tmpBufSize2;

	// default buffer size (frames per channel)
	private static final int		BUFSIZE				= 8192;
	// for chunks greater or equal than this use a dedicated SilentAudioStake instead of writing zeros to regular AudioStake
	private static final int		MINSILENTSIZE		= 65536;

	private final int[][]			channelMaps;
	private final int				numChannels;
	private final boolean			singleFile;
    private AudioFile[]				tempF				= null;
	
	private final AudioFile[]		audioFiles;

	private int						numDepDec			= 0;
	

	public static AudioTrail newFrom( AudioFile af )
	throws IOException
	{
		final AudioFileDescr	afd			= af.getDescr();
		final int[][]			channelMaps = new int[ 1 ][ afd.channels ];
		final AudioTrail		at;
		final Span				span		= new Span( 0, afd.length );

		for( int i = 0; i < afd.channels; i++ ) {
			channelMaps[ 0 ][ i ] = i;
		}

		at = new AudioTrail( channelMaps, afd.rate, new AudioFile[] { af });
		at.add( null, new InterleavedAudioStake( span, af, span ));
		return at;
	}
	
	public static AudioTrail newFrom( AudioFile[] afs )
	throws IOException
	{
		if( afs.length == 1 ) return newFrom( afs[ 0 ]);
		if( afs.length == 0 ) throw new IllegalArgumentException( "Need at least one audio file" );
		
		final long			length;
		final double		rate;
		AudioFileDescr		afd;
		final int[][]		channelMaps;
		final Span			span;
		final AudioTrail	at;
		final Span[]		fileSpans	= new Span[ afs.length ];
		
		afd			= afs[ 0 ].getDescr();
		length		= afd.length;
		rate		= afd.rate;
		span		= new Span( 0, length );
		channelMaps	= new int[ afs.length ][];
		for( int i = 0; i < afs.length; i++ ) {
			afd		= afs[ i ].getDescr();
			if( (afd.length != length) || (afd.rate != rate) ) {
				throw new IllegalArgumentException( "Invalid mixing of lengths and rates" );
			}
			channelMaps[ i ] = new int[ afd.channels ];
			for( int j = 0; j < channelMaps[ i ].length; j++ ) {
				channelMaps[ i ][ j ] = j;
			}
			fileSpans[ i ] = span;
		}
		
		at	= new AudioTrail( channelMaps, rate, afs );
		at.add( null, new MultiMappedAudioStake( span, afs, fileSpans, channelMaps ));
		return at;
	}
	
	public static AudioTrail newFrom( AudioFileDescr afd )
	{
		final int[][]	channelMaps = new int[ 1 ][ afd.channels ];
	
		for( int i = 0; i < afd.channels; i++ ) {
			channelMaps[ 0 ][ i ] = i;
		}
		return new AudioTrail( channelMaps, afd.rate, new AudioFile[ 1 ]);
	}

	protected BasicTrail createEmptyCopy()
	{
		return new AudioTrail( this.channelMaps, this.getRate(), new AudioFile[ 0 ]);
	}

	private AudioTrail( int[][] channelMaps, double rate, AudioFile[] audioFiles )
	{
		super();
		
		this.audioFiles		= audioFiles;
		this.channelMaps	= channelMaps;
		singleFile			= channelMaps.length == 1;

		int numCh			= 0;
		for( int i = 0; i < channelMaps.length; i++ ) {
			numCh		   += channelMaps[ i ].length;
		}
		this.numChannels	= numCh;
		setRate( rate );
	}
	
//	public File[] createCacheFileNames()
//	{
//		final File[]		cacheFs	= new File[ audioFiles.length ];
//		final CacheManager	cm		= CacheManager.getInstance();
//		for( int i = 0; i < audioFiles.length; i++ ) {
//			cacheFs[ i ] = cm.createCacheFileName( audioFiles[ i ].getFile() );
//		}
//		return cacheFs;
//	}
	
	protected AudioFile[] getAudioFiles()
	{
		return audioFiles;
	}
	
	public void closeAll()
	throws IOException
	{
		for( int i = 0; i < audioFiles.length; i++ ) {
			if( audioFiles[ i ] != null ) audioFiles[ i ].close();
		}
	}
	
	public void exchange( AudioFile af )
	throws IOException
	{
		if( audioFiles.length != 1 ) throw new IllegalStateException();
	
		final AudioFileDescr	afd			= af.getDescr();
		final Span				span		= new Span( 0, afd.length );

		if( afd.channels != channelMaps[ 0 ].length ) throw new IllegalStateException();

//		clear( null );
		clearIgnoreDependants();
		deleteTempFiles();
//		add( null, new InterleavedAudioStake( span, af, span ));
		addIgnoreDependants( new InterleavedAudioStake( span, af, span ));
	}
	
	public void exchange( AudioFile[] afs )
	throws IOException
	{
		if( afs.length == 1 ) {
			exchange( afs[ 0 ]);
			return;
		}

		if( audioFiles.length != afs.length ) throw new IllegalStateException();
		
		final long			length;
		final double		rate;
		AudioFileDescr		afd;
		final Span			span;
		final Span[]		fileSpans	= new Span[ afs.length ];
		
		afd			= afs[ 0 ].getDescr();
		length		= afd.length;
		rate		= afd.rate;
		span		= new Span( 0, length );
		for( int i = 0; i < afs.length; i++ ) {
			afd		= afs[ i ].getDescr();
			if( afd.channels != channelMaps[ i ].length ) throw new IllegalStateException();
			if( (afd.length != length) || (afd.rate != rate) ) {
				throw new IllegalArgumentException( "Invalid mixing of lengths and rates" );
			}
			fileSpans[ i ] = span;
		}
		
//		clear( null );
		clearIgnoreDependants();
		deleteTempFiles();
//		add( null, new MultiMappedAudioStake( span, afs, fileSpans, channelMaps ));
		addIgnoreDependants( new MultiMappedAudioStake( span, afs, fileSpans, channelMaps ));
	}
	
	public void dispose()
	{
		// call this first because dependants might rely on open audio files!
		super.dispose();
		for( int i = 0; i < audioFiles.length; i++ ) {
			if( audioFiles[ i ] != null ) audioFiles[ i ].cleanUp();
		}
		deleteTempFiles();
	}
	
	public int getDefaultTouchMode()
	{
		return TOUCH_SPLIT;
	}
	
	public int[][] getChannelMaps()
	{
		return channelMaps;
	}
	
//	public void insert( Object source, Span span, CompoundEdit ce )
//	{
//		insert( source, span, TOUCH_SPLIT, ce );
//	}
//
//	public void remove( Object source, Span span, CompoundEdit ce )
//	{
//		remove( source, span, TOUCH_SPLIT, ce );
//	}

	public int getChannelNum()
	{
		return numChannels;
	}
	
    public void debugDump()
    {
		AudioStake stake;
	
		for( int i = 0; i < getNumStakes(); i++ ) {
			stake = (AudioStake) get( i, true );
			stake.debugDump();
		}
		AudioStake.debugCheckDisposal();
    }
	
	public AudioStake allocSilent( Span span )
	{
		return new SilentAudioStake( span, numChannels );
	}
	
	public synchronized AudioStake alloc( Span span )
	throws IOException
	{
		long fileStart;
		long fileStop;
		final Span[] fileSpans	= new Span[ channelMaps.length ];
	
		// synchronized because this method is synchronized
		// and no other method calls createTempFiles() !
//		synchronized( this ) {
			if( tempF == null ) {
				createTempFiles();
			}
//		}
		synchronized( tempF ) {
			for( int i = 0; i < tempF.length; i++ ) {
				fileStart		= tempF[ i ].getFrameNum();
				fileStop		= fileStart + span.getLength();
				tempF[ i ].setFrameNum( fileStop );
				fileSpans[ i ]	= new Span( fileStart, fileStop );
			}
		}
		
		if( singleFile ) {
			return new InterleavedAudioStake( span, tempF[ 0 ], fileSpans[ 0 ]);
		} else {
			return new MultiMappedAudioStake( span, tempF, fileSpans, channelMaps );
		}
	}

//	public void addBufferReadMessages( OSCBundle bndl, Span readSpan, Buffer[] bufs, int bufOff )
//	{
//		int idx = indexOf( readSpan.start, true );
//		if( idx < 0 ) idx = -(idx + 2);
//
//		final List	coll	= editGetCollByStart( null );
//		final int				num		= coll.size();
//		AudioStake				stake;
//		int						chunkLen;
//		Span					subSpan;
//		int						len	= (int) readSpan.getLength();
//		
//		while( (len > 0) && (idx < num) ) {
//			stake		= (AudioStake) coll.get( idx );
//			subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
//									Math.min( stake.getSpan().stop, readSpan.stop ));
//			if( subSpan.getLength() > 0 ) {
//				stake.addBufferReadMessages( bndl, subSpan, bufs, bufOff );
//				chunkLen	= (int) subSpan.getLength();
//				bufOff	   += chunkLen;
//				len		   -= chunkLen;
//			}
//			idx++;
//		}
//		if( len > 0 ) {
//			for( int i = 0; i < bufs.length; i++ ) {
//				bndl.addPacket( bufs[ i ].fillMsg(
//					bufOff * bufs[ i ].getNumChannels(), len * bufs[ i ].getNumChannels(), 0.0f ));
//			}
//		}
//	}

	public void addBufferReadMessages( OSCBundle bndl, Span[] readSpans, Buffer[] bufs, int bufOff )
	{
//		int idx = indexOf( readSpan.start, true );
//		if( idx < 0 ) idx = -(idx + 2);

		final List		coll	= editGetCollByStart( null );
		final int		num		= coll.size();
		AudioStake		stake;
		int				chunkLen;
		Span			subSpan, readSpan;
//		int				len		= (int) readSpan.getLength();
		int				len		= 0;
		int				idx;
		
		for( int i = 0; i < readSpans.length; i++ ) {
			readSpan	= readSpans[ i ];
			idx			= indexOf( readSpan.start, true );
//System.err.println( "idx = "+idx );
			if( idx < 0 ) idx = Math.max( 0, -(idx + 2) );
			len		   += (int) readSpan.getLength();
			
			while( (len > 0) && (idx < num) ) {
//System.err.println( "len = "+len+"; idx = "+idx+"; num = "+num );
				stake		= (AudioStake) coll.get( idx );
				subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
										Math.min( stake.getSpan().stop, readSpan.stop ));
				chunkLen	= (int) subSpan.getLength();
				if( chunkLen > 0 ) {
					stake.addBufferReadMessages( bndl, subSpan, bufs, bufOff );
					bufOff	   += chunkLen;
					len		   -= chunkLen;
				}
				idx++;
			}
		}
		if( len > 0 ) {
			for( int i = 0; i < bufs.length; i++ ) {
				bndl.addPacket( bufs[ i ].fillMsg(
					bufOff * bufs[ i ].getNumChannels(), len * bufs[ i ].getNumChannels(), 0.0f ));
			}
		}
	}

	public static final int MODE_INSERT		= Session.EDIT_INSERT;
	public static final int MODE_OVERWRITE	= Session.EDIT_OVERWRITE;
	public static final int MODE_MIX		= Session.EDIT_MIX;
//	private static final int MODE_MIN		= MODE_INSERT;
//	private static final int MODE_MAX		= MODE_MIX;

	/**
	 *	Note: when mode == MODE_INSERT, the caller should have called editInsert on this
	 *	trail before, this is NOT done by this method; this method simply calls editAdd
	 *	with the newly synthesized stake!
	 *
	 *	@param	srcTrail	the trail to read from (null allowed, which means no source tracks)
	 *	@param	copySpan	re source!
	 *	@param	insertPos	such that copySpan.start becomes insertPos in the target
	 *	@param	mode		either MODE_INSERT, MODE_OVERWRITE or MODE_MIX
	 *	@param	trackMap	array of length this.getNumChannels(), where each element is the
	 *						source channel idx mapping to the target channel whose idx is the array idx
	 *						; so to copy channels 0 and 2 of a four channel source to a target stereo trail,
	 *						trackMap would be [ 0, 2 ] for example. A mono to stereo would be [ 0, 0 ].
	 *						index -1 indicates bypass (for MODE_INSERT or clearUnused filled with zeroes)
	 *	@param	clearUnused	whether tracks in source are to be cleared (instead of bypassed) when
	 *						no mapping from target exists. don't combine mode == MODE_MIX and clearUnused == true!
	 *
	 *	@todo	this method should somehow be part of BasicTrail
	 *	@todo	this method has become too complex and should be split up
	 */
	public boolean copyRangeFrom( AudioTrail srcTrail, Span copySpan, long insertPos, int mode,
								  Object source, AbstractCompoundEdit ce, int[] trackMap, BlendContext bcPre, BlendContext bcPost )
	throws IOException
	{
		if( trackMap.length != this.getChannelNum() ) throw new IllegalArgumentException( "trackMap : " + trackMap );
		if( trackMap.length == 0 ) return true;

		final boolean			hasBlend	= (bcPre != null) && (bcPre.getLen() > 0) || (bcPost != null) && (bcPost.getLen() > 0);
		final AudioStake		writeStake;
//		final boolean			result;
		final long				len			= copySpan.getLength();
		final int				bufLen		= (int) Math.min( len, BUFSIZE );
		final double			progWeight	= 1.0 / len;
		
		// throws IOException
		writeStake	= alloc( new Span( insertPos, insertPos + len ));
		
		try {
			switch( mode ) {
			case MODE_INSERT:
				if( hasBlend ) {
					insertRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight, bcPre, bcPost );
				} else {
					insertRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight );
				}
				break;
			
			case MODE_MIX:
				if( hasBlend ) {
					mixRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight, bcPre, bcPost );
				} else {
					mixRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight );
				}
				break;
			
			case MODE_OVERWRITE:
				if( hasBlend ) {
					overwriteRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight, bcPre, bcPost );
				} else {
					overwriteRangeFrom( srcTrail, copySpan.start, writeStake, insertPos, len, bufLen, trackMap, progWeight );
				}
				break;
			
			default:
				throw new IllegalArgumentException( "mode: " + mode );
			}
			
			writeStake.flush();
			this.editAdd( source, writeStake, ce );
			return true;
		}
		catch( InterruptedException e1 ) {	// thrown by ProcessingThread.updateAndCheckCancel()
			writeStake.dispose();
			return false;
		}
		catch( IOException e1 ) {
			writeStake.dispose();
			throw e1;
		}
	}

	private static void setProgression( long len, double progWeight )
	throws ProcessingThread.CancelledException
	{
// System.err.println( "aud prog len " + len + ", p " + (float) (len * progWeight) );
		ProcessingThread.update( (float) (len * progWeight) );
	}
	
	private static void flushProgression()
	{
		ProcessingThread.flushProgression();
	}
	
	private void insertRangeFrom( AudioTrail srcTrail, long srcStart, AudioStake writeStake, long insertPos, long len,
								  int bufLen, int[] trackMap, double progWeight )
	throws IOException, InterruptedException
	{
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			mappedSrcBuf	= new float[ this.getChannelNum() ][];
		float[]					empty			= null;
		boolean					srcUsed			= false;
		int						chunkLen;
		Span					chunkSpan;
		long					newSrcStart, newInsPos;
		
		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
				}
				mappedSrcBuf[ i ] = srcBuf[ trackMap[ i ]];
			} else {
				if( empty == null ) empty = new float[ bufLen ];
				mappedSrcBuf[ i ] = empty;
			}
		}

		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( bufLen, len - framesWritten );
			if( srcUsed ) {
				newSrcStart	= srcStart + chunkLen;
				chunkSpan	= new Span( srcStart, newSrcStart );
				srcTrail.readFrames( srcBuf, 0, chunkSpan );	// null channel bufs allowed!
				srcStart	= newSrcStart;
			}
			newInsPos	= insertPos + chunkLen;
			chunkSpan	= new Span( insertPos, newInsPos );
			writeStake.writeFrames( mappedSrcBuf, 0, chunkSpan );
			framesWritten += chunkLen;
			insertPos	= newInsPos;
			
			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}
		
	private void mixRangeFrom( AudioTrail srcTrail, long srcStart, AudioStake writeStake, long insertPos, long len,
							   int bufLen, int[] trackMap, double progWeight )
	throws IOException, InterruptedException
	{
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			outBuf			= new float[ this.getChannelNum() ][ bufLen ];
		final float[][]			mappedSrcBuf	= new float[ this.getChannelNum() ][];
		boolean					srcUsed			= false;
		int						chunkLen;
		Span					chunkSpan;
		long					newSrcStart, newInsPos;
		
		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
				}
				mappedSrcBuf[ i ] = srcBuf[ trackMap[ i ]];
			}
		}

		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( bufLen, len - framesWritten );
			if( srcUsed ) {
				newSrcStart	= srcStart + chunkLen;
				chunkSpan	= new Span( srcStart, newSrcStart );
				srcTrail.readFrames( srcBuf, 0, chunkSpan );	// null channel bufs allowed!
				srcStart	= newSrcStart;
			}
			newInsPos	= insertPos + chunkLen;
			chunkSpan	= new Span( insertPos, newInsPos );
			this.readFrames( outBuf, 0, chunkSpan );
			if( srcUsed ) {
				for( int i = 0; i < mappedSrcBuf.length; i++ ) {
					if( mappedSrcBuf[ i ] != null ) add( outBuf[ i ], 0, mappedSrcBuf[ i ], 0, chunkLen );
				}
			}
			writeStake.writeFrames( outBuf, 0, chunkSpan );
			framesWritten += chunkLen;
			insertPos	= newInsPos;

			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}

	private void overwriteRangeFrom( AudioTrail srcTrail, long srcStart, AudioStake writeStake, long insertPos, long len,
									 int bufLen, int[] trackMap, double progWeight )
	throws IOException, InterruptedException
	{
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			outBuf			= new float[ this.getChannelNum() ][];
		final float[][]			thisBuf			= new float[ this.getChannelNum() ][];
		boolean					srcUsed			= false;
		boolean					thisUsed		= false;
		int						chunkLen;
		Span					chunkSpan;
		long					newSrcStart, newInsPos;
		
		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed			= true;
				}
				outBuf[ i ]			= srcBuf[ trackMap[ i ]];
			} else {
				thisBuf[ i ]		= new float[ bufLen ];
				outBuf[ i ]			= thisBuf[ i ];
				thisUsed			= true;
			}
		}

		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( bufLen, len - framesWritten );
			if( srcUsed ) {
				newSrcStart	= srcStart + chunkLen;
				chunkSpan	= new Span( srcStart, newSrcStart );
				srcTrail.readFrames( srcBuf, 0, chunkSpan );	// null channel bufs allowed!
				srcStart	= newSrcStart;
			}
			newInsPos	= insertPos + chunkLen;
			chunkSpan	= new Span( insertPos, newInsPos );
			if( thisUsed ) {
				this.readFrames( thisBuf, 0, chunkSpan );
			}
			writeStake.writeFrames( outBuf, 0, chunkSpan );
			framesWritten += chunkLen;
			insertPos	= newInsPos;

			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}

	private void insertRangeFrom( AudioTrail srcTrail, final long srcStart, AudioStake writeStake, final long insertPos, final long len,
								  final int bufLen, int[] trackMap, double progWeight, BlendContext bcPre, BlendContext bcPost )
	throws IOException, InterruptedException
	{
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			mappedSrcBuf	= new float[ this.getChannelNum() ][];
//		final long				blendLen		= bc.getLen();
		final long				preLen			= bcPre == null ?  0L : bcPre.getLen();
		final long				postLen			= bcPost == null ? 0L : bcPost.getLen();
		final float[][]			mixBuf			= new float[ this.getChannelNum() ][ bufLen ];
		final float[][]			srcFadeBuf		= new float[ this.getChannelNum() ][];
		final long				fadeOutOffset	= insertPos - len;
		float[]					empty			= null;
		boolean					srcUsed			= false;
		boolean					writeMix		= false;
		int						chunkLen, chunkLen2, deltaChunk;
		int						cpToMixStart	= 0;
		int						cpToMixStop		= bufLen;
		Span					chunkSpan, chunkSpan2;
		
		// for each chunk there are two scenarios (indicated by the value of writeMix):
		// 1. we are in a chunk that contains a crossfade.
		//    in this case, output is found in mixBuf by reading in mixBuf from this
		//    trail and applying fades, then mixing (adding) the source trail from mappedSrcBuf.
		//    it is possible that both fadeIn and fadeOut are contained in one chunk, hence
		//    the two separate if-blocks below in the main loop and the tracking of the "dry" portion
		//    using variables cpToMixStart and cpToMixStop. Note that we chose not the opposite
		//    way of mixing mixBuf to mappedSrcBuf because we might be duplicating source trail
		//    channels (e.g. paste mono track to stereo file)!
		// 2. we are in a chunk that does not contain fades.
		//    in this case, we simply write mappedSrcBuf as output. we have ensured that this
		//    works by filling unused channels with emptyBuf.
		//
		// to properly deal with channel duplication, we have created a separate buffer
		// srcFadeBuf that prevents the blend context from accidentally fading the
		// same channel twice

		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
					srcFadeBuf[ i ] = srcBuf[ trackMap[ i ]];
				}
				mappedSrcBuf[ i ] = srcBuf[ trackMap[ i ]];
			} else {
				if( empty == null ) empty = new float[ bufLen ];
				mappedSrcBuf[ i ] = empty;
			}
		}

		for( long framesWritten = 0, remaining = len; remaining > 0; ) {
			chunkLen	= (int) Math.min( bufLen, remaining );
			if( srcUsed ) {
//				newSrcStart	= srcStart + chunkLen;
				chunkSpan	= new Span( srcStart + framesWritten, srcStart + framesWritten + chunkLen );
//System.err.println( "A srcTrail.readFrames( srcBuf, 0, " + chunkSpan + " )" );
				srcTrail.readFrames( srcBuf, 0, chunkSpan );	// null channel bufs allowed!
//				srcStart	= newSrcStart;

				if( framesWritten < preLen ) {	// fade source in
//System.err.println( "A bc.fadeIn : srcFadeBuf in [ 0 ... " + ((int) Math.min( chunkLen, preLen - framesWritten )) + " ], offset = "+framesWritten );
					bcPre.fadeIn( framesWritten, srcFadeBuf, 0, srcFadeBuf, 0, (int) Math.min( chunkLen, preLen - framesWritten ));
				}
				if( remaining - chunkLen < postLen ) {	// fade source out
					deltaChunk	= (int) Math.max( 0, remaining - postLen );	// this is the amount of space before the actual fade begins!
					chunkLen2	= chunkLen - deltaChunk;
//System.err.println( "A bc.fadeOut : srcFadeBuf in [ " + deltaChunk + " ... " + chunkLen2 + " ], offset = "+(postLen - remaining + deltaChunk) );
					bcPost.fadeOut( postLen - remaining + deltaChunk, srcFadeBuf, deltaChunk, srcFadeBuf, deltaChunk, chunkLen2 );
				}
			}
//			newInsPos	= insertPos + chunkLen;
//			chunkSpan	= new Span( insertPos, newInsPos );
			chunkSpan	= new Span( insertPos + framesWritten, insertPos + framesWritten + chunkLen );

			if( framesWritten < preLen ) {	// fade this out
				chunkLen2	= (int) Math.min( chunkLen, preLen - framesWritten );
				deltaChunk	= chunkLen - chunkLen2;
				chunkSpan2	= deltaChunk > 0 ? chunkSpan.replaceStop( chunkSpan.stop - deltaChunk ) : chunkSpan;
//System.err.println( "B this.readFrames( mixBuf, 0, " + chunkSpan2 + " )" );
				this.readFrames( mixBuf, 0, chunkSpan2 );
//System.err.println( "B bc.fadeOut : mixBuf in [ 0 ... " + chunkLen2 + " ], offset = "+framesWritten );
				bcPre.fadeOut( framesWritten, mixBuf, 0, mixBuf, 0, chunkLen2 );
				for( int i = 0; i < mixBuf.length; i++ ) {
					if( mappedSrcBuf[ i ] != empty ) add( mixBuf[ i ], 0, mappedSrcBuf[ i ], 0, chunkLen2 );
				}
				cpToMixStart= chunkLen2;
				cpToMixStop	= chunkLen;
				writeMix	= true;
			}
			
//len - framesWritten != blendLen
//framesWritten != len - blendLen;
//fadeOutPos = origInsertPos - blendLen - (len - blendLen) = origInsertPos - len

			// check if after this chunk we have entered the fadein
			if( remaining - chunkLen < postLen ) {	// fade this in
				deltaChunk	= (int) Math.max( 0, remaining - postLen );	// this is the amount of space before the actual fade begins!
				chunkLen2	= chunkLen - deltaChunk;
				chunkSpan2	= new Span( fadeOutOffset + framesWritten + deltaChunk, fadeOutOffset + framesWritten + chunkLen );
//System.err.println( "CCC . framesWritten = "+framesWritten+"; len = "+len+"; postLen = "+postLen+"; insertPos now "+insertPos+ "; remaining = "+remaining+"; deltaChunk = "+deltaChunk+"; chunkLen2 = "+chunkLen2 );				
//System.err.println( "C this.readFrames( mixBuf, " + deltaChunk + ", " + chunkSpan2 + " )" );
				this.readFrames( mixBuf, deltaChunk, chunkSpan2 );
//System.err.println( "C bc.fadeIn : mixBuf in [ " + deltaChunk + " ... " + chunkLen2 + " ], offset = "+(postLen - remaining + deltaChunk) );
				bcPost.fadeIn( postLen - remaining + deltaChunk, mixBuf, deltaChunk, mixBuf, deltaChunk, chunkLen2 );
				for( int i = 0; i < mixBuf.length; i++ ) {
					if( mappedSrcBuf[ i ] != empty ) add( mixBuf[ i ], deltaChunk, mappedSrcBuf[ i ], deltaChunk, chunkLen2 );
				}
				cpToMixStop	= deltaChunk;
				writeMix	= true;
			}

			if( writeMix ) {
				chunkLen2	= cpToMixStop - cpToMixStart;
				if( chunkLen2 > 0 ) {
					for( int i = 0; i < mixBuf.length; i++ ) {
						System.arraycopy( mappedSrcBuf[ i ], cpToMixStart, mixBuf[ i ], cpToMixStart, chunkLen2 );
					}
				}
				writeStake.writeFrames( mixBuf, 0, chunkSpan );
				writeMix	= false;
				cpToMixStart= 0;
				cpToMixStop	= bufLen;
			} else {
				writeStake.writeFrames( mappedSrcBuf, 0, chunkSpan );
			}
			framesWritten += chunkLen;
			remaining	  -= chunkLen;
			
//			insertPos	= newInsPos;
			
			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}

	private void mixRangeFrom( AudioTrail srcTrail, long srcStart, AudioStake writeStake, long insertPos, long len,
							   int bufLen, int[] trackMap, double progWeight, BlendContext bcPre, BlendContext bcPost )
	throws IOException, InterruptedException
	{
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			mappedSrcBuf	= new float[ this.getChannelNum() ][];
//		final long				blendLen		= bc.getBlendLen();
		final long				preLen			= bcPre == null  ? 0L : bcPre.getLen();
		final long				postLen			= bcPost == null ? 0L : bcPost.getLen();
		final float[][]			mixBuf			= new float[ this.getChannelNum() ][ bufLen ];
		final float[][]			srcFadeBuf		= new float[ this.getChannelNum() ][];
		boolean					srcUsed			= false;
		int						chunkLen;
		Span					chunkSpan, chunkSpan2;
		long					newSrcStart, newInsPos;
		
		// src trail is read in mapped buffer mappedSrcBuf. this trail is read into buffer mixBuf.
		// src trail is faded according to srcFadeBuf (which avoid duplicate fading of re-used
		// channels). non-null channels in mappedSrcBuf are mixed (added) to mixBuf, and mixBuf
		// is written out.

		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed			= true;
					srcFadeBuf[ i ] = srcBuf[ trackMap[ i ]];
				}
				mappedSrcBuf[ i ]	= srcBuf[ trackMap[ i ]];
			}
		}

		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( bufLen, len - framesWritten );
			newInsPos	= insertPos + chunkLen;
			chunkSpan	= new Span( insertPos, newInsPos );
			this.readFrames( mixBuf, 0, chunkSpan );
			if( srcUsed ) {
				newSrcStart	= srcStart + chunkLen;
				chunkSpan2	= new Span( srcStart, newSrcStart );
				srcTrail.readFrames( srcBuf, 0, chunkSpan2 );	// null channel bufs allowed!
				srcStart	= newSrcStart;

				if( framesWritten < preLen ) {	// fade source in
					bcPre.fadeIn( framesWritten, srcFadeBuf, 0, srcFadeBuf, 0, (int) Math.min( chunkLen, preLen - framesWritten ));
				}
				if( len - (framesWritten + chunkLen) < postLen ) {	// fade source out
					bcPost.fadeOut( framesWritten - (len - postLen), srcFadeBuf, 0, srcFadeBuf, 0, (int) Math.min( chunkLen, len - framesWritten ));
				}
				for( int i = 0; i < mixBuf.length; i++ ) {
					if( mappedSrcBuf[ i ] != null ) add( mixBuf[ i ], 0, mappedSrcBuf[ i ], 0, chunkLen );
				}
			}
			writeStake.writeFrames( mixBuf, 0, chunkSpan );
			framesWritten += chunkLen;
			insertPos	= newInsPos;
			
			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}

	private void overwriteRangeFrom( AudioTrail srcTrail, long srcStart, AudioStake writeStake, long insertPos, long len,
									 int bufLen, int[] trackMap, double progWeight, BlendContext bcPre, BlendContext bcPost )
	throws IOException, InterruptedException
	{
//System.err.println( "WARNING: blending calculations not yet fixed" );
		final float[][]			srcBuf			= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			mappedSrcBuf	= new float[ this.getChannelNum() ][];
//		final long				blendLen		= bc.getBlendLen();
		final long				preLen			= bcPre == null ?  0L : bcPre.getLen();
		final long				postLen			= bcPost == null ? 0L : bcPost.getLen();
		final float[][]			mixBuf			= new float[ this.getChannelNum() ][ bufLen ];
		final float[][]			srcFadeBuf		= new float[ this.getChannelNum() ][];
		final float[][]			thisDryBuf		= new float[ this.getChannelNum() ][];
		final float[][]			thisFadeBuf		= new float[ this.getChannelNum() ][];
		final float[][]			compositeBuf	= new float[ this.getChannelNum() ][];
		boolean					srcUsed			= false;
//		boolean					thisFadeUsed	= false;
//		boolean					thisDryUsed		= false;
		int						chunkLen, chunkLen2, deltaChunk;
		int						clrMixFadeStart = 0;
		int						clrMixFadeStop	= bufLen;
		Span					chunkSpan; // , chunkSpan2;
		long					newSrcStart, newInsPos, fadeOff;
		boolean					xFadeBegin, xFadeEnd, xFade;
		
		// for each chunk there are two scenarios (indicated by the value of xFade):
		// 1. we are in a chunk that contains a crossfade.
		//    in this case, output is found in mixBuf by reading in mixBuf from this
		//    trail and applying fades to the "wet" parts of this trail (thisFadeBuf),
		//	  then mixing (adding) the source trail from mappedSrcBuf.
		// 2. we are in a chunk that does not contain fades.
		//    in this case, we only read those channels from this trail that are "dry"
		//	  (not overwritten) as specified in thisDryBuf. we then write a composite
		//	  buffer compositeBuf which contains either references to channels from the source
		//	  (mappedSrcBuf) or from this trail (thisDryBuf).
		//
		// to properly deal with channel duplication, we have created a separate buffer
		// srcFadeBuf that prevents the blend context from accidentally fading the
		// same channel twice

		for( int i = 0; i < trackMap.length; i++ ) {
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
					srcFadeBuf[ i ] = srcBuf[ trackMap[ i ]];
				}
				mappedSrcBuf[ i ]	= srcBuf[ trackMap[ i ]];
				compositeBuf[ i ]	= mappedSrcBuf[ i ];
				thisFadeBuf[ i ]	= mixBuf[ i ];
//				thisFadeUsed		= true;
			} else {
				thisDryBuf[ i ]		= mixBuf[ i ];
//				thisDryUsed			= true;
				compositeBuf[ i ]	= mixBuf[ i ];
			}
		}

		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( bufLen, len - framesWritten );
			xFadeBegin	= framesWritten < preLen;
			xFadeEnd	= len - (framesWritten + chunkLen) < postLen;
			xFade		= xFadeBegin || xFadeEnd;
			
			if( srcUsed ) {
				newSrcStart	= srcStart + chunkLen;
				chunkSpan	= new Span( srcStart, newSrcStart );
				srcTrail.readFrames( srcBuf, 0, chunkSpan );	// null channel bufs allowed!
				srcStart	= newSrcStart;

				if( xFadeBegin ) {	// fade source in
//System.err.print( "xFadeBegin. off = "+framesWritten+"; srcFadeBuf = [ " );
//for( int kk = 0; kk < srcFadeBuf.length; kk++ ) {
//	if( kk > 0 ) System.err.print( ", " );
//	System.err.print( srcFadeBuf[ kk ] == null ? "null" : ("float[ " + srcFadeBuf[ kk ].length + " ]") );
//}
//System.err.println( " ]; len = " + ((int) Math.min( chunkLen, blendLen - framesWritten )));
					bcPre.fadeIn( framesWritten, srcFadeBuf, 0, srcFadeBuf, 0, (int) Math.min( chunkLen, preLen - framesWritten ));
				}
				if( xFadeEnd ) {	// fade source out
//System.err.print( "xFadeEnd. off = "+(framesWritten - (len - blendLen))+"; srcFadeBuf = [ " );
//for( int kk = 0; kk < srcFadeBuf.length; kk++ ) {
//	if( kk > 0 ) System.err.print( ", " );
//	System.err.print( srcFadeBuf[ kk ] == null ? "null" : ("float[ " + srcFadeBuf[ kk ].length + " ]") );
//}
//System.err.println( " ]; len = " + ((int) Math.min( chunkLen, len - framesWritten )));
					fadeOff		= framesWritten - (len - postLen);
					if( fadeOff < 0 ) {
						deltaChunk	= (int) -fadeOff;
						fadeOff		= 0;
					} else {
						deltaChunk	= 0;
					}
					chunkLen2	= (int) Math.min( chunkLen, len - framesWritten ) - deltaChunk;
					bcPost.fadeOut( fadeOff, srcFadeBuf, deltaChunk, srcFadeBuf, deltaChunk, chunkLen2 );
				}
			}
			newInsPos	= insertPos + chunkLen;
			chunkSpan	= new Span( insertPos, newInsPos );

			if( xFade ) {
				this.readFrames( mixBuf, 0, chunkSpan );
				if( xFadeBegin ) {	// fade this out
					chunkLen2		= (int) Math.min( chunkLen, preLen - framesWritten );
					deltaChunk		= chunkLen - chunkLen2;
//					chunkSpan2		= deltaChunk > 0 ? chunkSpan.replaceStop( chunkSpan.stop - deltaChunk ) : chunkSpan;
					bcPre.fadeOut( framesWritten, thisFadeBuf, 0, thisFadeBuf, 0, chunkLen2 );
					clrMixFadeStart	= chunkLen2;
					clrMixFadeStop	= chunkLen;
				}
				if( xFadeEnd ) {	// fade this in
//					chunkLen2	= (int) Math.min( chunkLen, len - framesWritten );
//					deltaChunk	= chunkLen - chunkLen2;
					fadeOff		= framesWritten - (len - postLen);
					if( fadeOff < 0 ) {
						deltaChunk	= (int) -fadeOff;
						fadeOff		= 0;
					} else {
						deltaChunk	= 0;
					}
					chunkLen2	= (int) Math.min( chunkLen, len - framesWritten ) - deltaChunk;
					bcPost.fadeIn( fadeOff, thisFadeBuf, deltaChunk, thisFadeBuf, deltaChunk, chunkLen2 );
					clrMixFadeStop	= deltaChunk;
				}
				chunkLen2 = clrMixFadeStop - clrMixFadeStart;
				if( chunkLen2 > 0 ) {
					for( int i = 0; i < thisFadeBuf.length; i++ ) {
						if( thisFadeBuf[ i ] != null ) clear( thisFadeBuf[ i ], clrMixFadeStart, chunkLen2 );
					}
				}
				clrMixFadeStart	= 0;
				clrMixFadeStop	= bufLen;
				for( int i = 0; i < mixBuf.length; i++ ) {
					if( mappedSrcBuf[ i ] != null ) add( mixBuf[ i ], 0, mappedSrcBuf[ i ], 0, chunkLen );
				}
				writeStake.writeFrames( mixBuf, 0, chunkSpan );
			} else {
				this.readFrames( thisDryBuf, 0, chunkSpan );
				writeStake.writeFrames( compositeBuf, 0, chunkSpan );
			}
			framesWritten += chunkLen;
			insertPos	= newInsPos;
			
			setProgression( framesWritten, progWeight );
		}
		writeStake.flush();
	}

	private static void add( float[] bufA, int offA, float[] bufB, int offB, int len )
	{
		for( int stop = offA + len; offA < stop; ) {
			bufA[ offA++ ] += bufB[ offB++ ];
		}
	}

	private static void clear( float[] buf, int off, int len )
	{
		for( int stop = off + len; off < stop; ) {
			buf[ off++ ] = 0f;
		}
	}

/*	
		final float[][]			srcBuf		= new float[ srcTrail == null ? 0 : srcTrail.getChannelNum() ][];
		final float[][]			outBuf		= new float[ this.getChannelNum() ][];
		final float[][]			mixBuf		= new float[ this.getChannelNum() ][];
		final float[][]			bypassBuf	= new float[ this.getChannelNum() ][];
		final ProcessingThread	pt			= ProcessingThread.currentThread();
		final long				len			= copySpan.getLength();
		final double			progWeight	= 1.0 / len;
		final int				bufLen		= (int) Math.min( len, BUFSIZE );
//		final boolean			mixMode		= (mode == MODE_MIX) || (blendLen > 0);
//		final boolean			mixMode		= (mode == MODE_MIX); // XXX
		final boolean			needsMix	= (mode == MODE_MIX) || (blendLen > 0) || ((mode == MODE_OVERWRITE) && !clearUnused);
		AudioStake				writeStake	= null;
		float[]					empty		= null;
		float[]					temp, temp2;
		boolean					srcUsed		= false;
		boolean					mixUsed		= false;
		boolean					dirty		= false;
//		boolean					copySrc		= false;
		int						chunkLen, chunkLen2, deltaChunk;
		long					n;
		Span					readSpan, readSpan2;
		
//System.err.println( "trackMap : " );


		for( int i = 0; i < trackMap.length; i++ ) {
			if( needsMix ) {
				mixBuf[ i ] = new float[ bufLen ];
				mixUsed		= true;
			}
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
				}
				if( needsMix ) {
					outBuf[ i ] = new float[ bufLen ];
				} else {
					outBuf[ i ] = srcBuf[ trackMap[ i ]];
				}
			} else {
				if( mode == MODE_INSERT
			
				if( (mode == MODE_INSERT) || clearUnused ) {
					if( empty == null ) empty = new float[ bufLen ];
					outBuf[ i ] = empty;
				} else {
					if( mixBuf[ i ] == null ) {
						mixBuf[ i ] = new float[ bufLen ];
						mixUsed		= true;
					}
					outBuf[ i ] = mixBuf[ i ];
				}
			}
		}


		for( int i = 0; i < trackMap.length; i++ ) {
//System.err.println( "  "+trackMap[ i ]);
//			if( mode == MODE_MIX ) {
			if( needsMix ) {
				mixBuf[ i ] = new float[ bufLen ];
				mixUsed		= true;
			}
			if( trackMap[ i ] >= 0 ) {
				if( srcBuf[ trackMap[ i ]] == null ) {
					srcBuf[ trackMap[ i ]] = new float[ bufLen ];
					srcUsed	= true;
					outBuf[ i ] = srcBuf[ trackMap[ i ]];
				} else if( mixUsed ) {
					outBuf[ i ] = new float[ bufLen ];
//					copySrc = true;
				} else {
					outBuf[ i ] = srcBuf[ trackMap[ i ]];
				}
			} else {
//				if( (mode == MODE_INSERT) || (clearUnused && !needsMix) ) {
				if( (mode == MODE_INSERT) || clearUnused ) {
					if( empty == null ) empty = new float[ bufLen ];
//					empty = new float[ bufLen ];
					outBuf[ i ] = empty;
				} else {
					if( mixBuf[ i ] == null ) {
						mixBuf[ i ] = new float[ bufLen ];
						mixUsed		= true;
					}
					outBuf[ i ] = mixBuf[ i ];
				}
			}
		}
		
// CHECK CONTRACT! XXX
//		if( !srcUsed && (mode != MODE_INSERT) && !clearUnused ) return;	// nothing da do
		
		try {
			writeStake	= alloc( new Span( insertPos, insertPos + len ));
		
			for( long framesWritten = 0; framesWritten < len; ) {
				chunkLen	= (int) Math.min( bufLen, len - framesWritten );
				if( srcUsed ) {
					n			= copySpan.start + framesWritten;
					readSpan	= new Span( n, n + chunkLen );
					srcTrail.readFrames( srcBuf, 0, readSpan );	// null channel bufs allowed!
					if( bc != null ) {
						if( framesWritten < blendLen ) {
							bc.fadeIn( framesWritten, outBuf, 0, outBuf, 0, (int) Math.min( chunkLen, blendLen - framesWritten ));
						}
						if( len - (framesWritten + chunkLen) < blendLen ) {
							bc.fadeOut( framesWritten - (len - blendLen), outBuf, 0, outBuf, 0, (int) Math.min( chunkLen, len - framesWritten ));
						}
					}
					for( int i = 0; i < outBuf.length; i++ ) {
						if( trackMap[ i ] < 0 ) continue;
						temp	= srcBuf[ trackMap[ i ]];
						temp2	= outBuf[ i ];
						if( temp != temp2) {
							System.arraycopy( temp, 0, temp2, 0, chunkLen );
						}
					}
				}
				n			= insertPos + framesWritten;
				readSpan	= new Span( n, n + chunkLen );
				if( mixUsed ) {
					
					// ------------------------------------- Mix Mode
				
					if( mode == MODE_MIX ) {
						this.readFrames( mixBuf, 0, readSpan );
						for( int i = 0; i < mixBuf.length; i++ ) {
							temp	= mixBuf[ i ];
							temp2	= outBuf[ i ];
							if( (temp != temp2) && (temp != null) ) {
								for( int j = 0; j < chunkLen; j++ ) {
									temp2[ j ] += temp[ j ];
								}
							}
						}

					// ------------------------------------- Overwrite Mode w/ Blending
											
					} else if( mode == MODE_OVERWRITE ) {
						if( dirty ) {
							for( int i = 0; i < mixBuf.length; i++ ) {
								temp	= mixBuf[ i ];
								if( temp == outBuf[ i ]) {
									for( int j = 0; j < chunkLen; j++ ) {
										temp[ j ] = 0f;
									}
								}
							}
							dirty = false;
						}
					
						if( framesWritten < blendLen ) {	// fade out
							chunkLen2	= (int) Math.min( chunkLen, blendLen - framesWritten );
							deltaChunk	= chunkLen - chunkLen2;
							readSpan2	= readSpan.replaceStop( readSpan.stop - deltaChunk );
//System.err.println( "fade out. framesWritten = "+framesWritten+"; len = "+len+"; chunkLen = "+chunkLen+"; chunkLen2 = "+chunkLen2+"; blendLen = "+blendLen+" ; read span = "+readSpan2+"; span length = "+readSpan2.getLength() );
							this.readFrames( mixBuf, 0, readSpan2 );
							bc.fadeOut( framesWritten, mixBuf, 0, mixBuf, 0, chunkLen2 );
							for( int i = 0; i < mixBuf.length; i++ ) {
								temp	= mixBuf[ i ];
								temp2	= outBuf[ i ];
								if( (temp != temp2) && (temp != null) ) {
									for( int j = 0; j < chunkLen2; j++ ) {
										temp2[ j ] += temp[ j ];
									}
//								} else {
//									for( int j = chunkLen2; j < chunkLen; j++ ) {
//										temp2[ j ] = 0f;
//									}
								}
//								for( int j = 0; j < chunkLen2; j++ ) {
//temp2[ j ] = -0.5f;
//								}
							}
							dirty = true;
						}
						if( len - (framesWritten + chunkLen) < blendLen ) {	// fade in
							chunkLen2	= (int) Math.min( chunkLen, (framesWritten + chunkLen) - (len - blendLen) );
							deltaChunk	= chunkLen - chunkLen2;
							readSpan2	= readSpan.replaceStart( readSpan.start + deltaChunk );
//System.err.println( "fade in. framesWritten = "+framesWritten+"; len = "+len+"; chunkLen = "+chunkLen+"; chunkLen2 = "+chunkLen2+"; blendLen = "+blendLen+" ; read span = "+readSpan2+"; span length = "+readSpan2.getLength() );
//							this.readFrames( mixBuf, 0, readSpan2 );
							this.readFrames( mixBuf, deltaChunk, readSpan2 );
							bc.fadeIn( framesWritten - (len - blendLen) + deltaChunk, mixBuf, deltaChunk, mixBuf, deltaChunk, chunkLen2 );
							for( int i = 0; i < mixBuf.length; i++ ) {
								temp	= mixBuf[ i ];
								temp2	= outBuf[ i ];
								if( (temp != temp2) && (temp != null) ) {
									for( int j = 0, k = deltaChunk; j < chunkLen2; j++, k++ ) {
//										temp2[ k ] += temp[ j ];
										temp2[ k ] += temp[ k ];
									}
//								} else {
//								
								}
							}
							dirty = true;
						}
						
					// ------------------------------------- Insert Mode w/ Blending

					} else if( mode == MODE_INSERT ) {
						if( framesWritten < blendLen ) {	// fade out
							chunkLen2	= (int) Math.min( chunkLen, blendLen - framesWritten );
							deltaChunk	= chunkLen - chunkLen2;
							readSpan2	= readSpan.replaceStop( readSpan.stop - deltaChunk );
							this.readFrames( mixBuf, 0, readSpan2 );
							bc.fadeOut( framesWritten, mixBuf, 0, mixBuf, 0, chunkLen2 );
							for( int i = 0; i < mixBuf.length; i++ ) {
								temp	= mixBuf[ i ];
								temp2	= outBuf[ i ];
								if( (temp != temp2) && (temp != null) ) {
									for( int j = 0; j < chunkLen2; j++ ) {
										temp2[ j ] += temp[ j ];
									}
								}
							}
						}
						if( len - (framesWritten + chunkLen) < blendLen ) {	// fade in
							chunkLen2	= (int) Math.min( chunkLen, len - framesWritten );
							deltaChunk	= chunkLen - chunkLen2;
							// alora, dis one fade in is pre-insert pos!
							n			= insertPos - (len - framesWritten);
							readSpan2	= new Span( n, n + chunkLen2 );
							this.readFrames( mixBuf, 0, readSpan2 );
							bc.fadeIn( framesWritten - (len - blendLen), mixBuf, 0, mixBuf, 0, chunkLen2 );
							for( int i = 0; i < mixBuf.length; i++ ) {
								if( (mixBuf[ i ] != outBuf[ i ]) && (mixBuf[ i ] != null) ) {
									for( int j = 0, k = deltaChunk; j < chunkLen2; j++, k++ ) {
										outBuf[ i ][ k ] += mixBuf[ i ][ j ];
									}
								}
							}
						}
					
					} else {
						assert false : mode;
					}
				}
				writeStake.writeFrames( outBuf, 0, readSpan );
				framesWritten += chunkLen;

				if( pt != null ) {
					pt.setProgression( (float) (framesWritten * progWeight) );
					if( pt.shouldCancel() ) {
						writeStake.dispose();
						return false;
					}
				}
			}
			
			writeStake.flush();
		}
		catch( IOException e1 ) {
			if( writeStake != null ) writeStake.dispose();
			throw e1;
		}

		// XXX mode
		this.editAdd( source, writeStake, ce );
		return true;
	}
*/
	private static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	public void clearRange( Span clearSpan, int mode, Object source, AbstractCompoundEdit ce, boolean[] trackMap, BlendContext bc )
	throws IOException
	{
		if( trackMap.length != this.getChannelNum() ) throw new IllegalArgumentException( trackMap.toString() );
		if( trackMap.length == 0 ) return;

		switch( mode ) {
		case MODE_INSERT:
			clearRangeIns( clearSpan, mode, source, ce, trackMap, bc );
			break;
		case MODE_OVERWRITE:
			clearRangeOvr( clearSpan, mode, source, ce, trackMap, bc );
			break;
		default:
			throw new IllegalArgumentException( String.valueOf( mode ));
		}
	}

	public void addDependant( Trail sub )
	{
		super.addDependant( sub );
		if( sub instanceof DecimatedWaveTrail ) {
			numDepDec++;
		}
	}

	public void removeDependant( Trail sub )
	{
		super.removeDependant( sub );
		if( sub instanceof DecimatedWaveTrail ) {
			numDepDec--;
		}
	}

	/*
	 *	contract: trackMap.length > 0
	 */
	private void clearRangeIns( Span clearSpan, int mode, Object source, AbstractCompoundEdit ce, boolean[] trackMap, BlendContext bc )
	throws IOException
	{
		final long			blendLen	= bc == null ? 0L : bc.getLen(); // .getBlendLen();
		
		if( blendLen == 0L ) return;	// nothing da do
		
		final boolean		t1			= trackMap[ 0 ];
		for( int i = 1; i < trackMap.length; i++ ) {
			if( t1 != trackMap[ i ]) throw new IllegalStateException( getResourceString( "errAudioWillLooseSync" ));
		}
		
		final double		perDecProgRatio	= 0.9;	// this to one decimtrail
		final double		progRatio		= 1.0 / (1.0 + ((1.0 - perDecProgRatio) / perDecProgRatio) * numDepDec);
		final double		progWeight		= progRatio / blendLen;

		final long			left			= bc.getLeftLen();
		final long			right			= bc.getRightLen();
		final Span			fadeInSpan		= new Span( clearSpan.stop - left, clearSpan.stop + right );
		final Span			fadeOutSpan		= new Span( clearSpan.start - left, clearSpan.start + right );
		final int			bufLen			= (int) Math.min( blendLen, BUFSIZE );
		final Span			writeSpan		= fadeOutSpan; // new Span( clearSpan.start, clearSpan.start + blendLen );
		final int			numCh			= this.getChannelNum();
		final float[][]		bufA			= new float[ numCh ][ bufLen ];
		final float[][]		bufB			= new float[ numCh ][ bufLen ];
		
//System.err.println( "fadeInSpan = " + fadeInSpan + "; fadeOutSpan = " + fadeOutSpan );

		AudioStake			writeStake	= null;
//		float[]				temp, temp2;
		int					chunkLen;
		long				n;
		Span				chunkSpan;
		boolean				success	= false;
		
//		final long time1 = System.currentTimeMillis();
		
		try {
			flushProgression();
			writeStake		= alloc( writeSpan );
			
			for( long framesWritten = 0; framesWritten < blendLen; ) {
				chunkLen	= (int) Math.min( bufLen, blendLen - framesWritten );
				n			= fadeOutSpan.start + framesWritten;
				chunkSpan	= new Span( n, n + chunkLen );
				this.readFrames( bufA, 0, chunkSpan );
				n			= fadeInSpan.start + framesWritten;
				chunkSpan	= new Span( n, n + chunkLen );
				this.readFrames( bufB, 0, chunkSpan );
				bc.blend( framesWritten, bufA, 0, bufB, 0, bufA, 0, chunkLen );
				n			= writeSpan.start + framesWritten;
				chunkSpan	= new Span( n, n + chunkLen );
				writeStake.writeFrames( bufA, 0, chunkSpan );
				framesWritten += chunkLen;
				
				setProgression( framesWritten, progWeight );
			}
			writeStake.flush();
			success = true;
		}
		finally {
			if( !success && (writeStake != null) ) writeStake.dispose();
		}

//		final long time2 = System.currentTimeMillis();
		// XXX mode
//System.err.println( "add "+writeSpan );
		this.editAdd( source, writeStake, ce );
//		final long time3 = System.currentTimeMillis();
		
//		System.out.println( "ratio " + (double) (time2 - time1) / (time3 - time1) );
	}

	/*
	 *	contract: trackMap.length > 0
	 *
	 *	@todo	should be able to blend fadeIn / fadeOut, so maxBlendLen = clearSpan.length not clearSpan.length >> 1
	 *			; this requires a lot of changes though ;-(
	 */
	private void clearRangeOvr( Span clearSpan, int mode, Object source, AbstractCompoundEdit ce, boolean[] trackMap, BlendContext bc )
	throws IOException
	{
		final long			blendLen	= bc == null ? 0L : bc.getLen();
		
		boolean				sync		= true;
		boolean				success		= false;
		final boolean		t1			= trackMap[ 0 ];
		for( int i = 1; i < trackMap.length; i++ ) {
			if( t1 != trackMap[ i ]) {
				sync	= false;
				break;
			}
		}
		// now sync is true if all tracks are selected or all are unselected

		final List collStakes	= new ArrayList( 3 );
		
//final long time1 = System.currentTimeMillis();
		try {
			if( sync && !t1 ) return;			// all tracks unselected
//			if( (blendLen == 0L) && sync ) {	// no fades, all tracks selected, let's just add a SilentAudioStake
//				writeStake2 = allocSilent( clearSpan );
//				this.editAdd( source, writeStake2, ce );
//				return;
//			}
		
			final double		perDecProgRatio	= 0.9;	// this to one decimtrail
			final double		progRatio		= 1.0 / (1.0 + ((1.0 - perDecProgRatio) / perDecProgRatio) * numDepDec);

			final boolean		hasBlend		= blendLen > 0L;
			final long			blendLen2		= blendLen << 1;
			final int			numCh			= this.getChannelNum();
//			final float[][]		srcBuf			= new float[ numCh ][];
//			final float[][]		outBuf			= new float[ numCh ][];
//			final float[][]		mixBuf			= new float[ numCh ][];
			final float[][]		readWriteBufF	= new float[ numCh ][];		// reading and writing during fade
			final float[][]		fadeBuf;									// channels which are faded
			final float[][]		readBufS;									// reading during middle part
			final float[][]		writeBufS;									// writing during middle part
			final Span			silentSpan		= new Span( clearSpan.start + blendLen, clearSpan.stop - blendLen );
			final long			silentLen		= silentSpan.getLength();
			final int			bufLen			= (int) Math.min( clearSpan.getLength(), BUFSIZE );
			final boolean		useSilentStake	= sync && (silentLen >= MINSILENTSIZE);
			final AudioStake	writeStake1, writeStake2, writeStake3;
			final double		progWeight;
			float[]				empty		= null;
			float[]				temp;
			int					chunkLen;
			long				n;
			long				totalFramesWritten	= 0;
			Span				chunkSpan;

			flushProgression();

			// buffer regarding fade in / out
			if( hasBlend ) {
				fadeBuf	= new float[ numCh ][];
				for( int i = 0; i < trackMap.length; i++ ) {
					readWriteBufF[ i ]	= new float[ bufLen ];
					if( trackMap[ i ]) fadeBuf[ i ] = readWriteBufF[ i ];
				}
			} else {
				fadeBuf = null;
			}

			// buffer regarding middle part
			if( !useSilentStake ) {
				readBufS	= new float[ numCh ][];
				writeBufS	= new float[ numCh ][];
				for( int i = 0; i < trackMap.length; i++ ) {
					if( trackMap[ i ]) {	// need to silence
						if( empty == null ) empty = new float[ bufLen ];
						writeBufS[ i ] = empty;
					}
					if( !trackMap[ i ]) {	// need to copy (bypass)
						if( readWriteBufF[ i ] != null ) {	// can reuse
							readBufS[ i ]	= readWriteBufF[ i ];	
						} else {
							readBufS[ i ]	= new float[ bufLen ];
						}
						writeBufS[ i ] = readBufS[ i ];
					}
				}
			} else {
				readBufS	= null;
				writeBufS	= null;
			}

			// ---- define stakes ----
			if( useSilentStake ) {		// ok, let's put a silent stake in dem middle
				if( hasBlend ) {
					writeStake1 = alloc( new Span( clearSpan.start, silentSpan.start ));
					collStakes.add( writeStake1 );
				} else {
					writeStake1	= null;
				}
				writeStake2	= allocSilent( silentSpan );
				collStakes.add( writeStake2 );
				if( hasBlend ) {
					writeStake3	= alloc( new Span( silentSpan.stop, clearSpan.stop ));
					collStakes.add( writeStake3 );
				} else {
					writeStake3	= null;
				}
				
				final double progRatio2		= 1.0 / (1.0 + numDepDec);  // inf:1 for silentLen
				final double w				= (double) blendLen2 / (blendLen2 + silentLen); // weight of progRatio versus progRatio2
				progWeight					= (progRatio*w + progRatio2*(1.0-w)) / blendLen2;
			
			} else {
				writeStake2	= alloc( clearSpan );
				writeStake1	= writeStake2;
				writeStake3	= writeStake2;
				collStakes.add( writeStake2 );
				progWeight	= progRatio / (blendLen2 + silentLen);
//				progWeight	= 9.0 / ((blendLen2 + silentLen) * (numDepDec + 9));
			}

			// ---- fade out part ----
			for( long framesWritten = 0; framesWritten < blendLen; ) {
				chunkLen	= (int) Math.min( bufLen, blendLen - framesWritten );
				n			= clearSpan.start + framesWritten;
				chunkSpan	= new Span( n, n + chunkLen );
				this.readFrames( readWriteBufF, 0, chunkSpan );	// d.h. alle kanaele lesen
				bc.fadeOut( framesWritten, fadeBuf, 0, fadeBuf, 0, chunkLen );		// only fade selected tracks
				writeStake1.writeFrames( readWriteBufF, 0, chunkSpan );
				framesWritten += chunkLen;
				totalFramesWritten += chunkLen;
				setProgression( totalFramesWritten, progWeight );
			}
			
			// ---- middle part ----
			if( !useSilentStake ) {
				// make sure writeBufF is cleared for unselected tracks now, since if hasBlend == true, it was used before 
				if( hasBlend ) { 
					for( int i = 0; i < numCh; i++ ) {
						temp = writeBufS[ i ];
						if( temp != empty ) {
							for( int j = 0; j < bufLen; j++ ) {
								temp[ j ] = 0f;
							}
						}
					}
				}
				
				for( long framesWritten = 0; framesWritten < silentLen; ) {
					chunkLen	= (int) Math.min( bufLen, silentLen - framesWritten );
					n			= silentSpan.start + framesWritten;
					chunkSpan	= new Span( n, n + chunkLen );
					if( !sync ) {	// not all channels are empty
						this.readFrames( readBufS, 0, chunkSpan );
					}
					writeStake2.writeFrames( writeBufS, 0, chunkSpan );
					framesWritten += chunkLen;
					totalFramesWritten += chunkLen;
					setProgression( totalFramesWritten, progWeight );
				}
			}

			// ---- fade in part ----
			for( long framesWritten = 0; framesWritten < blendLen; ) {
				chunkLen	= (int) Math.min( bufLen, blendLen - framesWritten );
				n			= silentSpan.stop + framesWritten;
				chunkSpan	= new Span( n, n + chunkLen );
				this.readFrames( readWriteBufF, 0, chunkSpan );	// d.h. alle kanaele lesen
				bc.fadeIn( framesWritten, fadeBuf, 0, fadeBuf, 0, chunkLen );		// only fade selected tracks
				writeStake3.writeFrames( readWriteBufF, 0, chunkSpan );
				framesWritten += chunkLen;
				totalFramesWritten += chunkLen;
				setProgression( totalFramesWritten, progWeight );
			}
			
			// ---- flushing ----
			if( useSilentStake ) {
				if( writeStake1 != null ) writeStake1.flush();
				if( writeStake3 != null ) writeStake3.flush();
			} else {
				writeStake2.flush();
			}
			success = true;
		}
		finally {
			if( !success ) {
				for( int i = 0; i < collStakes.size(); i++ ) {
					((AudioStake) collStakes.get( i )).dispose();
				}
			}
		}

//final long time2 = System.currentTimeMillis();
		// XXX mode
		this.editAddAll( source, collStakes, ce );
//final long time3 = System.currentTimeMillis();
//System.out.println( "ratio " + (double) (time2 - time1) / (time3 - time1) );
	}

	public static void readFrames( List stakesByStart, float[][] data, int dataOffset, Span readSpan )
    throws IOException
	{
		final int		num		= stakesByStart.size();
		int				idx		= Collections.binarySearch( stakesByStart, new Long( readSpan.start ), startComparator );
		if( idx < 0 )	idx		= Math.max( 0, -(idx + 2) );
//		int				len		= (int) readSpan.getLength();
		int				dataStop= (int) readSpan.getLength() + dataOffset;

		AudioStake	stake;
		int			chunkLen;
		Span		subSpan;
		
		while( (dataOffset < dataStop) && (idx < num) ) {
			stake		= (AudioStake) stakesByStart.get( idx );
			subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
									Math.min( stake.getSpan().stop, readSpan.stop ));
			chunkLen	= stake.readFrames( data, dataOffset, subSpan );
			dataOffset += chunkLen;
//			len		   -= chunkLen;
			idx++;
		}
		if( dataOffset < dataStop ) {
System.err.println( "WARNING: trying to read beyond the trail's stop" );
			for( int ch = 0; ch < data.length; ch++ ) {
				if( data[ ch ] != null ) {
					for( int i = dataOffset; i < dataStop; i++ ) {
						data[ ch ][ i ] = 0f;
					}
				}
			}
		}
	}
	
	protected void readFrames( float[][] data, int dataOffset, Span readSpan, AbstractCompoundEdit ce )
    throws IOException
    {
		AudioTrail.readFrames( editGetCollByStart( ce ), data, dataOffset, readSpan );
	}

    public void readFrames( float[][] data, int dataOffset, Span readSpan )
    throws IOException
    {
		readFrames( data, dataOffset, readSpan, null );
	}

//    private InterleavedStreamFile prepareTempFile()
//    throws IOException
//    {
//        if( tempF == null ) {
//			tempF = createTempFile();
//        } else {
//            tempF.seekFrame( tempF.getFrameNum() );
//        }
//        return tempF;
//    }
    
 	private void createTempFiles()
    throws IOException
	{
		final AudioFileDescr afd	= new AudioFileDescr();
		afd.type					= AudioFileDescr.TYPE_AIFF;
		afd.rate					= getRate();
		afd.bitsPerSample			= 32;
		afd.sampleFormat			= AudioFileDescr.FORMAT_FLOAT;

		if( singleFile ) {
			afd.channels			= getChannelNum();
			afd.file				= IOUtil.createTempFile();
			tempF					= new AudioFile[] { AudioFile.openAsWrite( afd )};
		} else {
			AudioFileDescr afd2;
			final AudioFile[] tempF2 = new AudioFile[ channelMaps.length ];
			for( int i = 0; i < channelMaps.length; i++ ) {
				afd2				= new AudioFileDescr( afd );
				afd2.channels		= channelMaps[ i ].length;
				afd2.file			= IOUtil.createTempFile();
				tempF2[ i ]			= AudioFile.openAsWrite( afd2 );
			}
			// real assignment here coz tempF will remain null if error occurs in the loop
			tempF					= tempF2;
		}
	}

	private void deleteTempFiles()
	{
		if( tempF != null ) {
			for( int i = 0; i < tempF.length; i++ ) {
				if( tempF[ i ] != null ) {
					tempF[ i ].cleanUp();
					tempF[ i ].getFile().delete();
				}
			}
		}
		tempF = null;
	}

//	/**
//	 *  Wrapper method for calling the fullrate
//	 *  editor's flatten method.
//	 *
//	 *  @param  f   the file into which the flattened data is written
//	 *  @throws IOException if a read or write error occurs
//	 *  @see	NondestructiveTrackEditor#flatten( InterleavedFloatFile )
//	 *
//	 *	@todo	subsampled files should be flatten as well?
//	 */
//	public void flatten( InterleavedStreamFile f, ProcessingThread pt, float progOff, float progWeight,
//						 SyncCompoundEdit edit )
//    throws IOException
//	{
//		Span	totalSpan	= new Span( 0, ste[0].getFrameNum() );
//		long	offset		= f.getFramePosition();
// 	
//		ste[0].flatten( f, pt, progOff, progWeight );
//		ste[0].clear();
//		ste[0].insert( f, offset, totalSpan );
//	}

	public void flatten( InterleavedStreamFile f, Span span, int[] channelMap )
    throws IOException
	{
		final Span			fileSpan	= new Span( f.getFramePosition(), span.getLength() );
		final AudioStake	stake		= new InterleavedAudioStake( span, f, fileSpan );
		
		try {
			flatten( stake, span, channelMap );
		}
		finally {
			stake.dispose();
		}
	}

	public void flatten( InterleavedStreamFile[] fs, Span span, int[] channelMap )
    throws IOException
	{
		if( fs.length == 1 ) {
			flatten( fs[ 0 ], span, channelMap );	// more efficient
			return;
		}
	
		final Span[]		fileSpans	= new Span[ fs.length ];
		for( int i = 0; i < fileSpans.length; i++ ) {
			fileSpans[ i ]				= new Span( fs[ i ].getFramePosition(), span.getLength() );
		}
		final AudioStake	stake		= new MultiMappedAudioStake( span, fs, fileSpans );
		
		try {
			flatten( stake, span, channelMap );
		}
		finally {
			stake.dispose();
		}
	}
	
	private void flatten( AudioStake target, Span span, int[] channelMap )
    throws IOException
	{
//		if( target.getChannelNum() != numChannels ) {
//			throw new IllegalArgumentException( "Wrong # of channels (required: " + numChannels +
//				" / got: " + target.getChannelNum() );
//		}
		if( span.isEmpty() ) return;

		final int outChannels = target.getChannelNum();
		// build or verify channelMap
		if( channelMap == null ) {
			if( outChannels != numChannels ) throw new IllegalArgumentException();
			channelMap = new int[ outChannels ];
			for( int i = 0; i < channelMap.length; i++ ) {
				channelMap[ i ] = i;
			}
		} else {
			if( outChannels != channelMap.length ) throw new IllegalArgumentException();
			for( int i = 0; i < channelMap.length; i++ ) {
				if( (channelMap[ i ] < 0) || (channelMap[ i ] >= numChannels) ) throw new IllegalArgumentException();
			}
		}

//		final ProcessingThread	pt			= ProcessingThread.currentThread();
//		final float[][]			data		= new float[ numChannels ][ BUFSIZE ];
		final double			progWeight	= 1.0 / span.getLength();
		final int				num			= getNumStakes();
		final float[][]			outBuf		= new float[ outChannels ][ BUFSIZE ];
		final float[][]			inBuf		= new float[ numChannels ][];
//		int						idx			= Collections.binarySearch( collStakesByStart, new Long( span.start ), startComparator );
		int						idx			= indexOf( span.start, true );
		if( idx < 0 )			idx			= Math.max( 0, -(idx + 2) );
		long					readOff		= span.start;
		AudioStake				source;
		int						chunkLen;
		Span					sourceSpan, subSpan;
		long					readStop	= span.start;
		
		for( int i = 0; i < channelMap.length; i++ ) {
			inBuf[ channelMap[ i ]] = outBuf[ i ];
		}
		
		while( (readStop < span.stop) && (idx < num) ) {
			source		= (AudioStake) get( idx, true );
			sourceSpan	= source.getSpan();
			readStop	= Math.min( sourceSpan.stop, span.stop );
			while( readOff < readStop ) {
				chunkLen = (int) Math.min( BUFSIZE, readStop - readOff );
				subSpan	 = new Span( readOff, readOff + chunkLen );
				source.readFrames( inBuf, 0, subSpan );
				target.writeFrames( outBuf, 0, subSpan );
				readOff += chunkLen;
				setProgression( readOff - span.start, progWeight );
//if( true ) throw new IOException( "FAIL TEST" );
			}
			idx++;
		}
		if( readStop < span.stop ) {
System.err.println( "WARNING: trying to flatten beyond the trail's stop" );
			for( int ch = 0; ch < outBuf.length; ch++ ) {
				for( int i = 0; i < outBuf[ ch ].length; i++ ) {
					outBuf[ ch ][ i ] = 0f;
				}
			}
			while( readOff < span.stop ) {
				chunkLen = (int) Math.min( BUFSIZE, span.stop - readOff );
				subSpan	 = new Span( readOff, readOff + chunkLen );
				target.writeFrames( outBuf, 0, subSpan );
				readOff += chunkLen;
				setProgression( readOff - span.start, progWeight );
			}
		}
	}
} // class AudioTrail