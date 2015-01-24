/*
 *  AudioBoxConfig.java
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

package de.sciss.eisenkraut.io;

import java.util.prefs.Preferences;

/**
 *	@version	0.70, 27-Mar-07
 *	@author		Hanns Holger Rutz
 */
public class AudioBoxConfig
implements Comparable
{
	public static final String ID_DEFAULT			= "default";

	/*
	 *  Preferences key. Value: Integer representing the number
	 *  of input channels for this configuration
	 */
	private static final String KEY_NUMINPUTS	= "numinputs";
	/*
	 *  Preferences key. Value: Integer representing the number
	 *  of output channels for this configuration
	 */
	private static final String KEY_NUMOUTPUTS	= "numoutputs";
	/**
	 *  Preferences key. Value: Boolean indicating whether
	 *  this box is currently available
	 */
	public static final String KEY_ACTIVE		= "active";
	/**
	 *  Preferences key. Value: String representing the driver
	 *  name for this configuration
	 */
	public static final String KEY_NAME			= "name";

	public final String		id;
	public final String		name;
	public final int		numInputChannels, numOutputChannels;
	public final boolean	active;
	
	public AudioBoxConfig( String id, String name )
	{
		this.id					= id;
		this.name				= name;
		this.numInputChannels	= 0;
		this.numOutputChannels	= 0;
		this.active				= true;
	}

	public AudioBoxConfig( String id, String name, int numInputChannels, int numOutputChannels, boolean active )
	{
		this.id					= id;
		this.name				= name;
		this.numInputChannels	= numInputChannels;
		this.numOutputChannels	= numOutputChannels;
		this.active				= active;
	}

	public AudioBoxConfig( Preferences cfgPrefs )
	throws NumberFormatException
	{
		id					= cfgPrefs.name();
		name				= cfgPrefs.get( KEY_NAME, id );
		numInputChannels	= cfgPrefs.getInt( KEY_NUMINPUTS, 8 );
		numOutputChannels	= cfgPrefs.getInt( KEY_NUMOUTPUTS, 8 );
		active				= cfgPrefs.getBoolean( KEY_ACTIVE, true );
	}
	
	public void toPrefs( Preferences cfgPrefs )
	{
		cfgPrefs.putInt( KEY_NUMINPUTS, numInputChannels );
		cfgPrefs.putInt( KEY_NUMOUTPUTS, numOutputChannels );
		cfgPrefs.put( KEY_NAME, name );
		cfgPrefs.putBoolean( KEY_ACTIVE, active );
	}
	
	public AudioBoxConfig changeID( String newID )
	{
		return new AudioBoxConfig( newID, name, numInputChannels, numOutputChannels, active );
	}

	public AudioBoxConfig changeName( String newName )
	{
		return new AudioBoxConfig( id, newName, numInputChannels, numOutputChannels, active );
	}

	public AudioBoxConfig changeChannels( int newInputChannels, int newOutputChannels )
	{
		return new AudioBoxConfig( id, name, newInputChannels, newOutputChannels, active );
	}

	public AudioBoxConfig changeActive( boolean newActive )
	{
		return new AudioBoxConfig( id, name, numInputChannels, numOutputChannels, newActive );
	}
	
	public String toString()
	{
		return name;
	}
	
	// ----------- Comparable interface -----------

	public int compareTo( Object o )
	{
		if( (o instanceof AudioBoxConfig) ) {
			return name.compareTo( ((AudioBoxConfig) o).name );
		} else {
			throw new ClassCastException();
		}
	}	
}