/*
 *  SilentAudioStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2007 Hanns Holger Rutz. All rights reserved.
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
 *		28-Jan-06	created
 */

package de.sciss.eisenkraut.io;

import java.io.*;

import de.sciss.io.CacheManager;
import de.sciss.io.InterleavedStreamFile;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.net.OSCBundle;
import de.sciss.timebased.*;

/**
 *	A fake silent audio stake that occupies no disk space.
 *	Thanks to scsynth's /b_fill command, this works harmonically
 *	with the supercollider player.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 */
public class SilentAudioStake
extends AudioStake
{
	private final int numChannels;

	/**
	 */
	protected SilentAudioStake( Span span, int numChannels )
	{
		super( span );
		
		this.numChannels = numChannels;
	}

	public void close()
	throws IOException
	{
		// well ...
	}

	public void cleanUp()
	{
		// well ...
	}

	public Stake duplicate()
	{
		return new SilentAudioStake( span, numChannels );
	}

	public Stake replaceStart( long newStart )
	{
		return new SilentAudioStake( span.replaceStart( newStart ), numChannels );
	}
	
	public Stake replaceStop( long newStop )
	{
		return new SilentAudioStake( span.replaceStop( newStop ), numChannels );
	}
	
	public Stake shiftVirtual( long delta )
	{
		return new SilentAudioStake( span.shift( delta ), numChannels );
	}
		
	public int readFrames( float[][] data, int dataOffset, Span readSpan )
	throws IOException
	{
		final int	len		= (int) readSpan.getLength();
		final int	stop	= dataOffset + len;
		float[]		temp;
		
		for( int i = 0; i < numChannels; i++ ) {
			temp = data[ i ];
			if( temp == null ) continue;
			for( int j = dataOffset; j < stop; j++ ) {
				temp[ j ] = 0.0f;
			}
		}
		
		return len;
	}
	
	public int writeFrames( float[][] data, int dataOffset, Span writeSpan )
	throws IOException
	{
		throw new IOException( "Not allowed" );
	}
	
	public long copyFrames( InterleavedStreamFile target, Span readSpan )
	throws IOException
	{
		final long		len		= readSpan.getLength();
		final int		bufLen	= (int) Math.min( 8192, readSpan.getLength() );
		final float[]	empty	= new float[ bufLen ];
		final float[][]	buf		= new float[ numChannels ][];
		int				chunkLen;
		
		for( int i = 0; i < buf.length; i++ ) {
			buf[ i ] = empty;
		}
		
		for( long framesWritten = 0; framesWritten < len; ) {
			chunkLen	= (int) Math.min( len - framesWritten, bufLen );
			target.writeFrames( buf, 0, chunkLen );
			framesWritten += chunkLen;
		}
		return len;
	}
	
	public int getChannelNum()
	{
		return numChannels;
	}
	
	public void flush()
	throws IOException
	{}
	
	public void addToCache( CacheManager cm )
	{}
	
	public void addBufferReadMessages( OSCBundle bndl, Span span, Buffer[] bufs, int bufOff )
	{
		final int	len			= (int) span.getLength();
		if( len == 0 ) return;

		for( int i = 0; i < bufs.length; i++ ) {
			bndl.addPacket( bufs[ i ].fillMsg(
				bufOff * bufs[ i ].getNumChannels(), len * bufs[ i ].getNumChannels(), 0.0f ));
		}
	}

	public void debugDump()
	{
		super.debugDumpBasics();
		System.err.println( "  (silent)" );
	}
} // class SilentAudioStake
