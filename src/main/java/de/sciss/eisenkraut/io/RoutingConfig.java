/*
 *  RoutineConfig.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import java.util.StringTokenizer;
import java.util.prefs.Preferences;

public class RoutingConfig {
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

    public static final String[] KEYS = { KEY_NAME, KEY_RC_NUMCHANNELS, KEY_RC_MAPPING, KEY_RC_STARTANGLE };

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

    public boolean equals( Object o )
    {
        if( (o == null) || !o.getClass().equals( getClass() )) return false;

        final RoutingConfig cfg2 = (RoutingConfig) o;
        if( id.equals( cfg2.id ) &&
            name.equals( cfg2.name ) &&
            (numChannels == cfg2.numChannels) &&
            (mapping.length == cfg2.mapping.length) &&
            (startAngle == cfg2.startAngle )) {

            for( int i = 0; i < mapping.length; i++ ) {
                if( mapping[ i ] != cfg2.mapping[ i ]) return false;
            }
            return true;
        }
        return false;
    }

    public int hashCode() {
        int hash = id.hashCode() ^ name.hashCode() ^ numChannels ^ Float.floatToRawIntBits(startAngle);
        for (int i = 0; i < mapping.length; i++) {
            hash ^= mapping[i] << i;
        }
        return hash;
    }

    public void toPrefs(Preferences cfgPrefs) {
        final StringBuilder sb = new StringBuilder();

        cfgPrefs.put(KEY_NAME, name);
        cfgPrefs.putInt(KEY_RC_NUMCHANNELS, numChannels);
        cfgPrefs.putFloat(KEY_RC_STARTANGLE, startAngle);
        if (numChannels > 0) {
            sb.append(mapping[0]);
        }
        for (int j = 1; j < numChannels; j++) {
            sb.append(' ');
            sb.append(mapping[j]);
        }
        cfgPrefs.put(KEY_RC_MAPPING, sb.toString());
    }

    public String toString() {
        final StringBuilder bMap = new StringBuilder("[ ");
        for (int i = 0; i < mapping.length; i++) {
            if (i > 0) bMap.append(", ");
            bMap.append(String.valueOf(mapping[i]));
        }
        bMap.append(" ]");
        return "RoutingConfig( \"" + id + "\", \"" + name + "\", " + bMap.toString() + ", " + startAngle + " )";
    }
}