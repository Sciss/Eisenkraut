/*
 *  Track.java
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
 *		13-May-05   created from de.sciss.meloncillo.transmitter.AbstractTransmitter
 *		15-Jan-06	audio specific stuff moved to AudioTrack class
 */

package de.sciss.eisenkraut.timeline;

import java.util.*;

import de.sciss.eisenkraut.io.*;
import de.sciss.eisenkraut.session.*;

import de.sciss.timebased.*;

/**
 *  A simple implementation of the <code>Transmitter</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all transmitters.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getTrackEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 */
public abstract class Track
extends AbstractSessionObject
{
	/**
	 *  Constructs a new empty transmitter.
	 *  Basic initialization is achieved by
	 *  adding a preexisting file to the track editor,
	 *  calling <code>setName</code> etc. methods.
	 */
	protected Track()
	{
		super();
	}
	
	public abstract Trail getTrail();
	
	public void clear( Object source )
	{
		getTrail().clear( source );
	}

	public static java.util.List getInfos( java.util.List selectedTracks, java.util.List allTracks )
	{
		Track					track;
		Trail					trail;
		Track.Info				ti;
		int						chan;
		final Map				mapInfos	= new HashMap();
		final java.util.List	collInfos	= new ArrayList();
		
		for( int i = 0; i < allTracks.size(); i++ ) {
			track	= (Track) allTracks.get( i );
//System.err.println( "for track "+track.getClass().getName()+" ..." );
			trail	= track.getTrail();
//System.err.println( "... trail = "+trail.getClass().getName() );
			ti		= (Track.Info) mapInfos.get( trail.getClass() );		
			if( ti == null ) {
				ti	= new Info( trail );
				mapInfos.put( ti.trail.getClass(), ti );
				collInfos.add( ti );
			}
			if( track instanceof AudioTrack ) {
				chan	= ((AudioTrack) track).getChannelIndex();
			} else {
				chan	= 0;
			}
			if( selectedTracks.contains( track )) {
				ti.selected			= true;
				ti.trackMap[ chan ] = true;
				ti.numTracks++;
			}
		}
		
		return collInfos;
	}
	
// -------------- internal classes --------------

	public static class Info
	{
		public final Trail				trail;
		public boolean					selected	= false;
		public final boolean[]			trackMap;
		public final int				numChannels;
		public int						numTracks	= 0;
	
		private Info( Trail trail )
		{
			this.trail		= trail;
			
			if( trail instanceof AudioTrail ) {
				numChannels	= ((AudioTrail) trail).getChannelNum();
			} else {
				numChannels	= 1;
			}
			
			trackMap = new boolean[ numChannels ];
		}
		
		public boolean getChannelSync()
		{
			if( numChannels == 0 ) return true;
			
			final boolean first = trackMap[ 0 ];
			for( int i = 1; i < numChannels; i++ ) {
				if( trackMap[ i ] != first ) return false;
			}
			return true;
		}
		
		public int[] createChannelMap( int numChannels, int offset, boolean skipUnused )
		{
			final int[] chanMap = new int[ numChannels ];
			int i, j;
			for( i = 0, j = offset; (i < this.numChannels) && (j < numChannels); i++ ) {
				if( trackMap[ i ]) {
					chanMap[ j++ ] = i;
				} else if( skipUnused ) {
					chanMap[ j++ ] = -1;
				}
			}
			while( j < numChannels ) {
				chanMap[ j++ ] = -1;
			}
			return chanMap;
		}
	}
}