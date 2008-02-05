/*
 *  AudioFileCacheInfo.java
 *  de.sciss.io package
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		28-Jul-07	extracted from de.sciss.eisenkraut.io.DecimatedTrail
 */

package de.sciss.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 26-Sep-07
 */
public class AudioFileCacheInfo
{
	private static final int VERSION = 1;
	
	private final int		model, numChannels;
	private final long		lastModified, numFrames;
	private final String	name;
	
	private AudioFileCacheInfo( String name, long lastModified, int model, int numChannels, long numFrames )
	{
		this.name			= name;
		this.lastModified	= lastModified;
		this.model			= model;
		this.numChannels	= numChannels;
		this.numFrames		= numFrames;
	}
	
	public AudioFileCacheInfo( InterleavedStreamFile f, int model, long numFrames )
	throws IOException
	{
		this( f.getFile().getName(), f.getFile().lastModified(), model, f.getChannelNum(), numFrames );
	}
	
	public static AudioFileCacheInfo decode( byte[] appCode )
	{
		final long				lastModified, numFrames;
		final int				numChannels, model;
		final String			name;
		final DataInputStream	dis;
	
		try {
			dis				= new DataInputStream( new ByteArrayInputStream( appCode ));
			if( dis.readInt() != VERSION ) return null;
			name			= dis.readUTF();
			lastModified	= dis.readLong();
			model			= dis.readInt();
			numChannels		= dis.readInt();
			numFrames		= dis.readLong();
			return new AudioFileCacheInfo( name, lastModified, model, numChannels, numFrames );
		}
		catch( IOException e1 ) {
			return null;
		}
	}
	
	public long getNumFrames()
	{
		return numFrames;
	}
	
	public long getNumChannels()
	{
		return numChannels;
	}
	
	public boolean equals( Object o )
	{
		if( o instanceof AudioFileCacheInfo ) {
			AudioFileCacheInfo ci = (AudioFileCacheInfo) o;
			return( this.name.equals( ci.name ) && (this.lastModified == ci.lastModified) &&
					(this.numChannels == ci.numChannels) && (this.numFrames == ci.numFrames) &&
					(this.model == ci.model));
		}
		return false;
	}
	
	public int hashCode()
	{
		return( name.hashCode() ^ model ^ -numChannels ^ (int) lastModified ^ (int) numFrames );
	}
	
	public byte[] encode()
	throws IOException
	{
		final ByteArrayOutputStream	baos	= new ByteArrayOutputStream( 64 );
		final DataOutputStream		dos		= new DataOutputStream( baos );
		
		dos.writeInt( VERSION );
		dos.writeUTF( name );
		dos.writeLong( lastModified );
		dos.writeInt( model );
		dos.writeInt( numChannels );
		dos.writeLong( numFrames );
		while( (dos.size() & 3) != 0 ) dos.write( 0 ); // zero pad to 4-byte boundary
		
		return baos.toByteArray();
	}
}