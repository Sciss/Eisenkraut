/*
 *  Replace.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;

import de.sciss.io.InterleavedStreamFile;

public class Replace
		extends AbstractRenderPlugIn {

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
