/*
 *  Invert.java
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
 *		15-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;

/**
 *	@version	0.70, 26-Sep-07
 */
public class Invert
extends AbstractRenderPlugIn
{
	public boolean producerRender( RenderSource source )
	throws IOException
	{
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			if( !source.audioTrackMap[ ch ]) continue;
			for( int i = 0, j = source.audioBlockBufOff; i < source.audioBlockBufLen; i++, j++ ) {
				source.audioBlockBuf[ ch ][ j ] *= -1;
			}
		}
//		return source.context.getConsumer().consumerRender( source );
		return super.producerRender( source );
	}

	public String getName()
	{
		return getResourceString( "plugInInvert" );
	}
}
