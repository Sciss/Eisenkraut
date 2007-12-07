/*
 *  RoutineConfig.java
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
 *		24-Jul-05	created
 *		12-May-06	uses name field separate from ID
 */

package de.sciss.eisenkraut.io;

import java.util.StringTokenizer;
import java.util.prefs.Preferences;

/**
 *	@version	0.70, 07-Dec-07
 *	@author		Hanns Holger Rutz
 */
public class RoutingConfig
{
	/*
	 *  Preferences key. Value: Integer representing the number
	 *  of channels for this configuration
	 */
	public static final String KEY_RC_NUMCHANNELS	= "numchannels";
	/*
	 *  Preferences key. Value: Float representing the start angle (in degrees)
	 *  for the first mapped channel fo this configuration
	 */
	private static final String KEY_RC_STARTANGLE	= "startangle";
	/*
	 *  Preferences key. Value: String which is space separated list of
	 *  integers representing the audio interface channels (first channel = 0)
	 *	for this configuration
	 */
	private static final String KEY_RC_MAPPING		= "mapping";
	/**
	 *  Preferences key. Value: String representing the driver
	 *  name for this configuration
	 */
	public static final String KEY_NAME			= "name";

	public final String		id;
	public final String		name;
	public final int		numChannels;
	public final int[]		mapping;
	public final float		startAngle;
	
	public RoutingConfig( String id, String name )
	{
		this.id				= id;
		this.name			= name;
		this.numChannels	= 0;
		this.mapping		= new int[ 0 ];
		this.startAngle		= 0f;
	}

	public RoutingConfig( String id, String name, int[] mapping, float startAngle )
	{
		this.id				= id;
		this.name			= name;
		this.numChannels	= mapping.length;
		this.mapping		= mapping;
		this.startAngle		= startAngle;
	}

	public RoutingConfig( Preferences cfgPrefs )
	throws NumberFormatException
	{
		final StringTokenizer tok = new StringTokenizer( cfgPrefs.get( KEY_RC_MAPPING, "" ));

		id			= cfgPrefs.name();
		name		= cfgPrefs.get( KEY_NAME, id );
		numChannels	= cfgPrefs.getInt( KEY_RC_NUMCHANNELS, 0 );
		startAngle	= cfgPrefs.getFloat( KEY_RC_STARTANGLE, 0.0f );
		mapping		= new int[ numChannels ];

		for( int j = 0; (j < numChannels) && tok.hasMoreTokens(); j++ ) {
			mapping[ j ] = Integer.parseInt( tok.nextToken() );
		}
	}
	
	public void toPrefs( Preferences cfgPrefs )
	{
		final StringBuffer sb = new StringBuffer();

		cfgPrefs.put( KEY_NAME, name );
		cfgPrefs.putInt( KEY_RC_NUMCHANNELS, numChannels );
		cfgPrefs.putFloat( KEY_RC_STARTANGLE, startAngle );
		if( numChannels > 0 ) {
			sb.append( mapping[ 0 ]);
		}
		for( int j = 1; j < numChannels; j++ ) {
			sb.append( ' ' );
			sb.append( mapping[ j ]);
		}
		cfgPrefs.put( KEY_RC_MAPPING, sb.toString() );
	}
}
