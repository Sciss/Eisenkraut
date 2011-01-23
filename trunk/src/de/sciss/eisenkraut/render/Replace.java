/*
 *  Replace.java
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
 *		19-May-09	created
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;

import de.sciss.io.InterleavedStreamFile;

/**
 * 	@author		Hanns Holger Rutz
 *	@version	0.70, 19-May-08
 */
public class Replace
extends AbstractRenderPlugIn
{
	private final InterleavedStreamFile	isf;
	
	private float[][]		prBuf;
	private int				prMinChans;
	
	public Replace( InterleavedStreamFile isf )
	{
		this.isf = isf;
	}
	
	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		prMinChans = Math.min( source.numAudioChannels, isf.getChannelNum() );
		prBuf = new float[ prMinChans ][];
		return super.producerBegin( source );
	}

	public boolean producerRender( RenderSource source )
	throws IOException
	{
		for( int ch = 0; ch < prMinChans; ch++ ) {
			if( source.audioTrackMap[ ch ]) {
				prBuf[ ch ] = source.audioBlockBuf[ ch ];
			}
		}
		isf.readFrames( prBuf, source.audioBlockBufOff, source.audioBlockBufLen );
		return super.producerRender( source );
	}
	
	public boolean producerFinish( RenderSource source )
	throws IOException
	{
		return super.producerFinish( source );
	}

	public String getName()
	{
		return getResourceString( "plugInReplace" );
	}
}
