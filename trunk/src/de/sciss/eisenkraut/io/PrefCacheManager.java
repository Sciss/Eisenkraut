/*
 *  PrefCacheManager.java
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
 *		16-Jul-05	created
 *		23-Sep-05	fixes a problem of cache folder not automatically been generated
 *		28-Jul-07	refactored from de.sciss.eisenkraut.io.CacheManager
 */

package de.sciss.eisenkraut.io;

import java.io.File;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import de.sciss.app.AbstractApplication;
import de.sciss.io.CacheManager;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 28-Jul-07
 */
public class PrefCacheManager
extends CacheManager
implements PreferenceChangeListener
{
	/**
	 *	Convenient name for preferences node
	 */
	public static final String DEFAULT_NODE		= "cache";

	public static final String KEY_ACTIVE		= "active";		// boolean
	public static final String KEY_FOLDER		= "folder";		// String
	public static final String KEY_CAPACITY		= "capacity";	// Param

	private final Preferences prefs;
	
//	private static final int DEFAULT_CAPACITY	= 100;		// 100 MB
	private static final Param DEFAULT_CAPACITY = new Param( 100, ParamSpace.NONE | ParamSpace.ABS );

	private static PrefCacheManager instance;

	public PrefCacheManager( Preferences prefs )
	{
		super();
		
		if( instance != null ) throw new IllegalStateException( "Only one instance allowed" );
		
		instance		= this;
		this.prefs		= prefs;
		
		final int capacity;
		final File folder;
		final boolean active;
		
		if( prefs.get( KEY_CAPACITY, null ) == null ) {	// create defaults
			capacity = (int) DEFAULT_CAPACITY.val ;
			folder = new File( new File( System.getProperty( "user.home" ),
				AbstractApplication.getApplication().getName() ), "cache" );
			active = false;
		} else {
			capacity = (int) Param.fromPrefs( prefs, KEY_CAPACITY, DEFAULT_CAPACITY ).val;
			folder = new File( prefs.get( KEY_FOLDER, "" ));
			active = prefs.getBoolean( KEY_ACTIVE, false );
		}
		setFolderAndCapacity( folder, capacity );
		setActive( active );
		prefs.addPreferenceChangeListener( this );
	}
	
	public static PrefCacheManager getInstance()
	{
		return instance;
	}
	
	public Preferences getPreferences()
	{
		return prefs;
	}
	
	public void setActive( boolean onOff )
	{
		super.setActive( onOff );
		prefs.putBoolean( KEY_ACTIVE, onOff );
	}
		
	public void setFolderAndCapacity( File folder, int capacity )
	{
		super.setFolderAndCapacity( folder, capacity );
		prefs.put( KEY_FOLDER, folder.getPath() );
		prefs.put( KEY_CAPACITY, new Param( capacity, ParamSpace.NONE | ParamSpace.ABS ).toString() );
	}

// ------- PreferenceChangeListener interface -------
		
	public void preferenceChange( PreferenceChangeEvent e )
	{
		final String	key = e.getKey();

		if( key.equals( KEY_FOLDER )) {
			final File f = new File( e.getNewValue() );
			if( (getFolder() == null) || ! (getFolder().equals( f ))) {
				setFolder( f );
			}
        } else if( key.equals( KEY_CAPACITY )) {
			final int c = (int) Param.fromPrefs( prefs, key, DEFAULT_CAPACITY ).val;
			if( getCapacity() != c ) {
				setCapacity( c );
			}
		} else if( key.equals( KEY_ACTIVE )) {
			final boolean b = Boolean.valueOf( e.getNewValue() ).booleanValue();
			if( isActive() != b ) {
				setActive( b );
			}
		}
 	}
}