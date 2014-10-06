/*
 *  InterleavedAudioStake.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		22-Dec-05	created
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
public class InterleavedAudioStake
extends AudioStake
{
	private final InterleavedStreamFile f;
	private final Span					fileSpan;
	private final Span					maxFileSpan;
	
	private final String				fileName;

	public InterleavedAudioStake( Span span, InterleavedStreamFile f, Span fileSpan )
	{
		this( span, f, fileSpan, fileSpan, getFileName( f ));
	}
	
	private InterleavedAudioStake( Span span, InterleavedStreamFile f, Span fileSpan, Span maxFileSpan, String fileName )
	{
		super( span );
	
		this.f				= f;
		this.fileSpan		= fileSpan;
		this.maxFileSpan	= maxFileSpan;
		this.fileName		= fileName;
	}
	
	public void close()
	throws IOException
	{
		f.close();
	}
	
	public void cleanUp()
	{
		try { close(); } catch( IOException e1 ) { /* ignore */ }
	}
	
	private static String getFileName( InterleavedStreamFile f )
	{
		return fileNameNormalizer.normalize( f.getFile().getAbsolutePath(), new StringBuffer() ).toString();
	}

	public Stake duplicate()
	{
		return new InterleavedAudioStake( span, f, fileSpan, maxFileSpan, fileName );
	}

	public Stake replaceStart( long newStart )
	{
		final Span newFileSpan	= fileSpan.replaceStart( fileSpan.start + newStart - span.start );
		final Span newSpan		= span.replaceStart( newStart );
		if( (newSpan.getLength() < 0) || (newFileSpan.getLength() < 0) || !maxFileSpan.contains( newFileSpan )) {
			throw new IllegalArgumentException( String.valueOf( newStart ));
		}
	
		return new InterleavedAudioStake( newSpan, f, newFileSpan, maxFileSpan, fileName );
	}
	
	public Stake replaceStop( long newStop )
	{
		final Span newFileSpan	= fileSpan.replaceStop( fileSpan.stop + newStop - span.stop );
		final Span newSpan		= span.replaceStop( newStop );
		if( (newSpan.getLength() < 0) || (newFileSpan.getLength() < 0) || !maxFileSpan.contains( newFileSpan )) {
			throw new IllegalArgumentException( String.valueOf( newStop ));
		}
	
		return new InterleavedAudioStake( newSpan, f, newFileSpan, maxFileSpan, fileName );
	}
	
	public Stake shiftVirtual( long delta )
	{
		return new InterleavedAudioStake( span.shift( delta ), f, fileSpan, maxFileSpan, fileName );
	}
	
	public int readFrames( float[][] data, int dataOffset, Span readSpan )
	throws IOException
	{
		final int	len				= (int) readSpan.getLength();
		if( len == 0 ) return 0;
		final long	fOffset			= fileSpan.start + readSpan.start - span.start;

		if( (fOffset < fileSpan.start) || ((fOffset + len) > fileSpan.stop) ) {
			throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpan.toString() );
		}

		synchronized( f ) {
			if( f.getFramePosition() != fOffset ) {
				f.seekFrame( fOffset );
			}
			f.readFrames( data, dataOffset, len );
		}		
		return len;
	}

	public int writeFrames( float[][] data, int dataOffset, Span writeSpan )
	throws IOException
	{
		final int	len			= (int) writeSpan.getLength();
		if( len == 0 ) return 0;
		final long	fOffset		= fileSpan.start + writeSpan.start - span.start;
	
		if( (fOffset < fileSpan.start) || ((fOffset + len) > fileSpan.stop) ) {
			throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpan.toString() );
		}

		synchronized( f ) {
			if( f.getFramePosition() != fOffset ) {
				f.seekFrame( fOffset );
			}
			f.writeFrames( data, dataOffset, len );
		}
		return len;
	}

	public long copyFrames( InterleavedStreamFile target, Span readSpan )
	throws IOException
	{
		final long	len			= readSpan.getLength();
		if( len == 0 ) return 0;
		final long	fOffset		= fileSpan.start + readSpan.start - span.start;
	
		if( (fOffset < fileSpan.start) || ((fOffset + len) > fileSpan.stop) ) {
			throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpan.toString() );
		}

		synchronized( f ) {
			if( f.getFramePosition() != fOffset ) {
				f.seekFrame( fOffset );
			}
			f.copyFrames( target, len );
		}
		return len;
	}

	public void addBufferReadMessages( OSCBundle bndl, Span readSpan, Buffer[] bufs, int bufOff )
	{
		final int	len			= (int) readSpan.getLength();
		if( len == 0 ) return;
		final long	fOffset		= fileSpan.start + readSpan.start - span.start;

		if( (fOffset < fileSpan.start) || ((fOffset + len) > fileSpan.stop) ) {
			throw new IllegalArgumentException( fOffset + " ... " + (fOffset + len) + " not within " + fileSpan.toString() );
		}
		
		if( (bufs.length != 1) || (bufs[ 0 ].getNumChannels() != f.getChannelNum()) ) {
			throw new IllegalArgumentException( "Wrong # of buffers / channels (required: 1 / " + f.getChannelNum() );
		}

		bndl.addPacket( bufs[ 0 ].readMsg( fileName, fOffset, len, bufOff ));
	}

	public int getChannelNum()
	{
		return f.getChannelNum();
	}

	public void flush()
	throws IOException
	{
		synchronized( f ) {
			f.flush();
		}
	}
	
	public void addToCache( CacheManager cm )
	{
		cm.addFile( f.getFile() );
	}

	public void debugDump()
	{
		debugDumpBasics();
		System.err.println( " ; f = " + f.getFile().getName() + " (file span " + fileSpan.toString() + " )" );
	}
}