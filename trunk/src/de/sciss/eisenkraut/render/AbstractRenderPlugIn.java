/*
 *  AbstractRenderPlugIn.java
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
 *		15-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 12-Oct-06
 */
public abstract class AbstractRenderPlugIn
implements RenderPlugIn
{
	private java.util.List	collTempFiles	= null;	// lazy creation

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
	public void init( Preferences prefs )
	{
		this.prefs	= prefs;
	}

	/**
	 *	Sub-classes should call super.init() !
	 */
	public void dispose()
	{
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
	 *	Default behaviour : simply calls consumer.consumerCancel() and
	 *	delete all temp files
	 */
	public void producerCancel( RenderSource source )
	throws IOException
	{
		try {
			source.context.getConsumer().consumerCancel( source );
		}
		finally {
			deleteAllTempFiles();
		}
	}

	private void deleteAllTempFiles()
	{
		if( collTempFiles != null ) {
			while( !collTempFiles.isEmpty() ) {
				deleteTempFile( (AudioFile) collTempFiles.get( 0 ));
			}
		}
	}

	protected void deleteTempFile( InterleavedStreamFile f )
	{
		if( collTempFiles != null ) collTempFiles.remove( f );
		try {
			f.close();
		}
		catch( IOException e1 ) {}
		f.getFile().delete();
	}

	protected InterleavedStreamFile createTempFile( int numChannels, double rate )
	throws IOException
	{
		final AudioFileDescr afd = new AudioFileDescr();
		AudioFile af;
		
		afd.type			= AudioFileDescr.TYPE_AIFF;
		afd.channels		= numChannels;
		afd.rate			= rate;
		afd.bitsPerSample	= 32;
		afd.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afd.file			= IOUtil.createTempFile( "fsc", ".aif" );
		af					= AudioFile.openAsWrite( afd );
		
		if( collTempFiles == null ) {
			collTempFiles	= new ArrayList();
		}
		collTempFiles.add( af );
		return af;
	}

	protected static void fill( float[][] buf, int off, int len, float value )
	{
		float[] convBuf;
	
		for( int ch = 0; ch < buf.length; ch++ ) {
			convBuf = buf[ ch ];
			for( int i = off, j = off + len; i < j; ) {
				convBuf[ i++ ] = value;
			}
		}
	}

	protected static void multiply( float[][] buf, int off, int len, float value )
	{
		float[] convBuf;
	
		for( int ch = 0; ch < buf.length; ch++ ) {
			convBuf = buf[ ch ];
			for( int i = off, j = off + len; i < j; ) {
				convBuf[ i++ ] *= value;
			}
		}
	}

	protected static void multiply( float[] buf, int off, int len, float value )
	{
		for( int i = off + len; off < i; ) {
			buf[ off++ ] *= value;
		}
	}

	protected static void add( float[][] bufA, int offA, float[][] bufB, int offB, int len )
	{
		float[] convBuf1, convBuf2;
	
		for( int ch = 0; ch < bufA.length; ch++ ) {
			convBuf1 = bufA[ ch ];
			convBuf2 = bufB[ ch ];
			for( int i = offA, j = offB, k = offA + len; i < k; ) {
				convBuf1[ i++ ] += convBuf2[ j++ ];
			}
		}
	}

	protected static void add( float[] bufA, int offA, float[] bufB, int offB, int len )
	{
		for( int i = offA + len; offA < i; ) {
			bufA[ offA++ ] += bufB[ offB++ ];
		}
	}

	protected static void subtract( float[][] bufA, int offA, float[][] bufB, int offB, int len )
	{
		float[] convBuf1, convBuf2;
	
		for( int ch = 0; ch < bufA.length; ch++ ) {
			convBuf1 = bufA[ ch ];
			convBuf2 = bufB[ ch ];
			for( int i = offA, j = offB, k = offA + len; i < k; ) {
				convBuf1[ i++ ] -= convBuf2[ j++ ];
			}
		}
	}
	
	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
}