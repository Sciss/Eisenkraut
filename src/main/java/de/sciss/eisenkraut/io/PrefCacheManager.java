/*
 *  PrefCacheManager.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
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

public class PrefCacheManager
        extends CacheManager
        implements PreferenceChangeListener {

    /**
     *	Convenient name for preferences node
     */
    public static final String DEFAULT_NODE		= "cache";

    public static final String KEY_ACTIVE		= "active";		// boolean
    public static final String KEY_FOLDER		= "folder";		// String
    public static final String KEY_CAPACITY		= "capacity";	// Param

    private final Preferences prefs;

    private static final Param DEFAULT_CAPACITY = new Param( 100, ParamSpace.ABS);

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

    public void setActive(boolean onOff) {
        super.setActive(onOff);
        prefs.putBoolean(KEY_ACTIVE, onOff);
    }

    public void setFolderAndCapacity(File folder, int capacity) {
        super.setFolderAndCapacity(folder, capacity);
        prefs.put(KEY_FOLDER, folder.getPath());
        prefs.put(KEY_CAPACITY, new Param(capacity, ParamSpace.ABS).toString());
    }

// ------- PreferenceChangeListener interface -------

    public void preferenceChange( PreferenceChangeEvent e )
    {
        final String	key = e.getKey();

        switch (key) {
            case KEY_FOLDER:
                final File f = new File(e.getNewValue());
                if ((getFolder() == null) || !(getFolder().equals(f))) {
                    setFolder(f);
                }
                break;
            case KEY_CAPACITY:
                final int c = (int) Param.fromPrefs(prefs, key, DEFAULT_CAPACITY).val;
                if (getCapacity() != c) {
                    setCapacity(c);
                }
                break;
            case KEY_ACTIVE:
                final boolean b = Boolean.valueOf(e.getNewValue());
                if (isActive() != b) {
                    setActive(b);
                }
                break;
        }
    }
}