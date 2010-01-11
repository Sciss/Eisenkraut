/*
 *  MultiMappedAudioStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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
 *		14-Jan-06	created
 */

package de.sciss.eisenkraut.io;

import java.io.IOException;

import de.sciss.io.CacheManager;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.Stake;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class MultiMappedAudioStake
extends AudioStake
{
	private final InterleavedStreamFile[]	fs;
	private final Span[]					fileSpans;
	private final Span[]					maxFileSpans;
	private final int[][]					channelMaps;
	private final float[][][]				mappedDatas;
	private final int						numChannels;
	
	private final String[]					fileNames;

	private final static int				BUFSIZE	= 8192;

	public MultiMappedAudioStake( Span span, InterleavedStreamFile[] fs, Span[] fileSpans )
	{
		this( span, fs, fileSpans, createChannelMaps( fs ));
	}
	
	// make sure channelMaps is never modified!
	public MultiMappedAudioStake( Span span, InterleavedStreamFile[] fs, Span[] fileSpans, int[][] channelMaps )
	{
		this( span, fs, fileSpans, fileSpans, channelMaps, getFileNames( fs ));
	}
	
	private MultiMappedAudioStake( Span span, InterleavedStreamFile[] fs, Span[] fileSpans,
								   Span[] maxFileSpans, int[][] channelMaps, String[] fileNames )
	{
		super( span );
	
		int numCh			= 0;

		this.fs				= fs;
		this.fileSpans		= fileSpans;
		this.maxFileSpans	= maxFileSpans;
		this.channelMaps	= channelMaps;
		mappedDatas			= new float[ fs.length ][][];
		for( int i = 0; i < fs.length; i++ ) {
			mappedDatas[ i ]	= new float[ fs[ i ].getChannelNum() ][];
			numCh			   += channelMaps[ i ].length;
		}
		this.numChannels	= numCh;
		this.fileNames		= fileNames;
	}

	public void close()
	throws IOException
	{
		for( int i = 0; i < fs.length; i++ ) fs[ i ].close();
	}

	public void cleanUp()
	{
		for( int i = 0; i < fs.length; i++ ) {
			try { fs[ i ].close(); } catch( IOException e1 ) { /* ignore */ }
		}
	}

	private static String[] getFileNames( InterleavedStreamFile[] fs )
	{
		final String[]		fileNames	= new String[ fs.length ];
		final StringBuffer	sb			= new StringBuffer();
		for( int i = 0; i < fs.length; i++ ) {
			fileNames[ i ] = fileNameNormalizer.normalize( fs[ i ].getFile().getAbsolutePath(), sb ).toString();
			sb.setLength( 0 );
		}
		return fileNames;
	}

	// consider result read-only!!!
	public int[][] getChannelMaps()
	{
		return channelMaps;
	}

	private static int[][] createChannelMaps( InterleavedStreamFile[] fs )
	{
		final int[][] channelMaps = new int[ fs.length ][];
		int[] channelMap;
		
		for( int i = 0; i < fs.length; i++ ) {
			channelMap			= new int[ fs[ i ].getChannelNum() ];
			for( int j = 0; j < channelMap.length; j++ ) {
				channelMap[ j ]	= j;
			}
			channelMaps[ i ]	= channelMap;
		}
		
		return channelMaps;
	}

	public Stake duplicate()
	{
		return new MultiMappedAudioStake( span, fs, fileSpans, maxFileSpans, channelMaps, fileNames );
	}

	public Stake replaceStart( long newStart )
	{
		final long delta			= newStart - span.start;
		final Span[] newFileSpans	= new Span[ fileSpans.length ];
		final Span newSpan			= span.replaceStart( newStart );

		if( newSpan.getLength() < 0 ) throw new IllegalArgumentException( String.valueOf( newStart ));
		
		for( int i = 0; i < fileSpans.length; i++ ) {
			newFileSpans[ i ]	= fileSpans[ i ].replaceStart( fileSpans[ i ].start + delta );
			if( (newFileSpans[ i ].getLength() < 0) || !maxFileSpans[ i ].contains( newFileSpans[ i ] )) {
				throw new IllegalArgumentException( String.valueOf( newStart ));
			}
		}
		return new MultiMappedAudioStake( newSpan, fs, newFileSpans, maxFileSpans, channelMaps, fileNames );
	}
	
	public Stake replaceStop( long newStop )
	{
		final long delta			= newStop - span.stop;
		final Span newSpan			= span.replaceStop( newStop );
		final Span[] newFileSpans	= new Span[ fileSpans.length ];

		if( newSpan.getLength() < 0 ) throw new IllegalArgumentException( String.valueOf( newStop ));

		for( int i = 0; i < fileSpans.length; i++ ) {
			newFileSpans[ i ]	= fileSpans[ i ].replaceStop( fileSpans[ i ].stop + delta );
			if( (newFileSpans[ i ].getLength() < 0) || !maxFileSpans[ i ].contains( newFileSpans[ i ])) {
				throw new IllegalArgumentException( String.valueOf( newStop ));
			}
		}
		return new MultiMappedAudioStake( newSpan, fs, newFileSpans, maxFileSpans, channelMaps, fileNames );
	}
	
	public Stake shiftVirtual( long delta )
	{
		return new MultiMappedAudioStake( span.shift( delta ), fs, fileSpans, maxFileSpans, channelMaps, fileNames );
	}
	
	public int readFrames( float[][] data, int offset, Span readSpan )
	throws IOException
	{
		final int				len		= (int) readSpan.getLength();
		if( len == 0 ) return 0;

		InterleavedStreamFile	f;
		long					fOffset;
		int[]					channelMap;
		float[][]				mappedData;
	
		synchronized( fs ) {	// protects mappedDatas
			for( int i = 0, j = 0; i < fs.length; i++ ) {
				f			= fs[ i ];
				channelMap	= channelMaps[ i ];
				mappedData	= mappedDatas[ i ];
				fOffset		= fileSpans[ i ].start + readSpan.start - span.start;

				if( (fOffset < fileSpans[ i ].start) || ((fOffset + len) > fileSpans[ i ].stop) ) {
					throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[ i ].toString() );
				}

				synchronized( f ) {
					if( f.getFramePosition() != fOffset ) {
						f.seekFrame( fOffset );
					}
					for( int k = 0; k < channelMap.length; k++, j++ ) {
						mappedData[ channelMap[ k ]] = data[ j ];
					}
					f.readFrames( mappedData, offset, len );
				}
			}
			clearMappedData();	// avoid memory footprint
		}
		return len;
	}

	public int writeFrames( float[][] data, int offset, Span writeSpan )
	throws IOException
	{
		final int				len			= (int) writeSpan.getLength();
		if( len == 0 ) return 0;

		InterleavedStreamFile	f;
		long					fOffset;
		int[]					channelMap;
		float[][]				mappedData;
		
		synchronized( fs ) {	// protects mappedDatas
			for( int i = 0, j = 0; i < fs.length; i++ ) {
				f			= fs[ i ];
				channelMap	= channelMaps[ i ];
				mappedData	= mappedDatas[ i ];
				fOffset		= fileSpans[ i ].start + writeSpan.start - span.start;

				if( (fOffset < fileSpans[ i ].start) || ((fOffset + len) > fileSpans[ i ].stop) ) {
					throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[ i ].toString() );
				}

				synchronized( f ) {
					if( f.getFramePosition() != fOffset ) {
						f.seekFrame( fOffset );
					}
					for( int k = 0; k < channelMap.length; k++, j++ ) {
						mappedData[ channelMap[ k ]] = data[ j ];
					}
					f.writeFrames( mappedData, offset, len );
				}
			}
			clearMappedData();	// avoid memory footprint
		}
		return len;
	}

	public long copyFrames( InterleavedStreamFile target, Span readSpan )
	throws IOException
	{
		final long	len			= readSpan.getLength();
		if( len == 0 ) return 0;
	
		InterleavedStreamFile	f;
		long					fOffset;
		int[]					channelMap;
		float[][]				mappedData;
		int						chunkLen;
		float[][]				data			= new float[ numChannels ][ (int) Math.min( BUFSIZE, len )];
		long					framesCopied	= 0;
		
		do {
			synchronized( fs ) {	// protects mappedDatas
				for( int i = 0, j = 0; i < fs.length; i++ ) {
					f			= fs[ i ];
					channelMap	= channelMaps[ i ];
					mappedData	= mappedDatas[ i ];
					fOffset		= fileSpans[ i ].start + readSpan.start - span.start;
	
					if( (fOffset < fileSpans[ i ].start) || ((fOffset + len) > fileSpans[ i ].stop) ) {
						throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[ i ].toString() );
					}
					
					chunkLen	= (int) Math.min( BUFSIZE, len - framesCopied );
					synchronized( f ) {
						if( f.getFramePosition() != fOffset ) {
							f.seekFrame( fOffset );
						}
						for( int k = 0; k < channelMap.length; k++, j++ ) {
							mappedData[ channelMap[ k ]] = data[ j ];
						}
						f.readFrames( mappedData, 0, chunkLen );
					}
					target.writeFrames( data, 0, chunkLen );
					framesCopied += chunkLen;
				}
			}
			clearMappedData();	// minimize memory footprint
		} while( framesCopied < len );
		
		return len;
	}
	
	public void addBufferReadMessages( OSCBundle bndl, Span readSpan, Buffer[] bufs, int bufOff )
	{
		final int	len			= (int) readSpan.getLength();
		if( len == 0 ) return;
		long		fOffset;
		
		if( bufs.length != fs.length ) {
			throw new IllegalArgumentException( "Wrong # of buffers (" + bufs.length + " != " + fs.length + ")" );
		}

		for( int i = 0; i < fs.length; i++ ) {
			fOffset = fileSpans[ i ].start + readSpan.start - span.start;

			if( (fOffset < fileSpans[ i ].start) || ((fOffset + len) > fileSpans[ i ].stop) ) {
				throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpans[ i ].toString() );
			}
		
			if( (bufs[ i ].getNumChannels() != fs[ i ].getChannelNum()) ) {
				throw new IllegalArgumentException( "Channel mismatch (" + bufs[ i ].getNumChannels() + " != " + fs[ i ].getChannelNum() );
			}

			bndl.addPacket( bufs[ i ].readMsg( fileNames[ i ], fOffset, len, bufOff ));
		}
	}

	// synchronization : caller must have sync on fs
	private void clearMappedData()
	{
		for( int i = 0; i < mappedDatas.length; i++ ) {
			for( int j = 0; j < mappedDatas[ i ].length; j++ ) {
				mappedDatas[ i ][ j ] = null;
			}
		}
	}

	public void flush()
	throws IOException
	{
		InterleavedStreamFile	f;

		for( int i = 0; i < fs.length; i++ ) {
			f = fs[ i ];
			synchronized( f ) {
				f.flush();
			}
		}
	}
	
	public void addToCache( CacheManager cm )
	{
		for( int i = 0; i < fs.length; i++ ) {
			cm.addFile( fs[ i ].getFile() );
		}
	}

	public int getChannelNum()
	{
		return numChannels;
	}

	public void debugDump()
	{
		debugDumpBasics();
		System.err.print( " ; fs = [ " );
		for( int i = 0; i < fs.length; i++ ) {
			System.err.print( (i > 0 ? ", " : "" ) + fs[i].getFile().getName() + " (file span " + fileSpans[i].toString() + " )" );
		}
		System.err.println( " ]" );
	}
}