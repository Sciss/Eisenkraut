/*
 *  RenderSource.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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
 *		14-Jul-05   created from de.sciss.meloncillo.render.RenderSource
 *		31-Aug-05	adds clipboard support (quick + dirty)
 */

package de.sciss.eisenkraut.render;

import java.util.List;

import de.sciss.io.Span;
import de.sciss.timebased.Trail;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.timeline.Track;

/**
 *  A RenderSource describes the source
 *  data for generating rendering output.
 *  This data is restricted to dynamic
 *  scalar vector data, i.e. data that
 *  changes over time : sense data
 *  (a receiver's sensibility at a point
 *  described by a transmitter trajectory)
 *  and trajectory data of a transmitter.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 *
 *	@todo		clipboard shit should be removed, use a separate RenderSource instead
 */
public class RenderSource
{
	public final int		numAudioChannels;
	public final boolean[]	audioTrackMap;
	
	/**
	 *  The blockSpan describes the
	 *  current time span of the provided data
	 *  in the source rate sense.
	 *  Thus, blockSpan.getLength() equals
	 *  blockBufLen
	 *
	 *	@todo	check what happens when resampling is active
	 */
	public Span				blockSpan;
	/**
	 */
	public float[][]		audioBlockBuf;
	/**
	 *  Offset to use when reading data
	 *  from blockBuf
	 */
	public int				audioBlockBufOff;
	/**
	 *  Length to use when reading data
	 *  from blockBuf
	 */
	public int				audioBlockBufLen;
	
	public float[][]		clipboardBuf;	// offset + len identical to blockBufOff/Len !
	
	public final RenderContext	context;
	
	public Trail			markers;
	
	public boolean			validAudio;
	public boolean			validMarkers;
	
	/**
	 *  Constructs a new RenderSource, where
	 *  the arrays are preallocated for the
	 *  given number of transmitters and receivers.
	 *  Note that the final vectors are not
	 *  initialized, i.e. senseBlockBuf will
	 *  become new float[numTrns][numRcv][] etc.
	 *  All request fields are set to false by default.
	 */
//	public RenderSource( int numChannels )
//	{
//		this.numChannels	= numChannels;
//		blockBuf			= new float[ numChannels ][];
//	}
	public RenderSource( RenderContext context )
	{
		this.context		= context;
		
		final List		trackInfos	= context.getTrackInfos();
		int				ch			= 0;
		Track.Info		ti;
		boolean[]		tm			= null;
		
		for( int i = 0; i < trackInfos.size(); i++ ) {
			ti = (Track.Info) trackInfos.get( i );
			if( ti.trail instanceof AudioTrail ) {
				ch = ti.numChannels;
				tm	= ti.trackMap;
			}
		}
		numAudioChannels	= ch;
		audioTrackMap		= tm;
		audioBlockBuf		= new float[ numAudioChannels ][];
	}
	
	/**
	 *  Constructs a new RenderSource by
	 *  copying a template. Note that the
	 *  vectors themselves are not allocated
	 *  just like in the RenderSource( int numTrns, int numRcv )
	 *  constructor! However, the requests
	 *  are copied 1:1 to the new RenderSource.
	 *
	 *	@param	template	a template request whose dimensions
	 *						and requests are copied to the newly
	 *						created render source
	 */
//	public RenderSource( RenderSource template )
//	{
//		this.numChannels	= template.numChannels;
//		blockBuf			= new float[ numChannels ][];
//	}
}