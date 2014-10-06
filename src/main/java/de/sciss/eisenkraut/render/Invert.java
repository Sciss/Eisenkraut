/*
 *  Invert.java
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
