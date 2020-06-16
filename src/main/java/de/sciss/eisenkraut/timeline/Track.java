/*
 *  Track.java
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

package de.sciss.eisenkraut.timeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.session.AbstractSessionObject;
import de.sciss.eisenkraut.session.SessionObject;
import de.sciss.timebased.Trail;

/**
 *  A simple implementation of the <code>Transmitter</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all transmitters.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getTrackEditor</code>.
 */
public abstract class Track
        extends AbstractSessionObject {
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

    public void clear(Object source) {
        getTrail().clear(source);
    }

    public static List<Info> getInfo(List<SessionObject> selectedTracks, List<SessionObject> allTracks) {
        Trail					trail;
        Track.Info				ti;
        int						chan;
        final Map<Class<?>, Info> mapInfo   = new HashMap<Class<?>, Info>();
        final List<Info>          collInfo  = new ArrayList<Info>();

        for (SessionObject so: allTracks) {
            Track track = (Track) so;
            trail = track.getTrail();
            ti = mapInfo.get(trail.getClass());
            if (ti == null) {
                ti = new Info(trail);
                mapInfo.put(ti.trail.getClass(), ti);
                collInfo.add(ti);
            }
            if (track instanceof AudioTrack) {
                chan = ((AudioTrack) track).getChannelIndex();
            } else {
                chan = 0;
            }
            if (selectedTracks.contains(track)) {
                ti.selected = true;
                ti.trackMap[chan] = true;
                ti.numTracks++;
            }
        }

        return collInfo;
    }

// -------------- internal classes --------------

    public static class Info
    {
        public final Trail				trail;
        public boolean					selected	= false;
        public final boolean[]			trackMap;
        public final int				numChannels;
        public int						numTracks	= 0;

        protected Info( Trail trail )
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

        public int[] createChannelMap( int numCh2, int offset, boolean skipUnused )
        {
            final int[] chanMap = new int[ numCh2 ];
            int i, j;
            for( i = 0, j = offset; (i < this.numChannels) && (j < numCh2); i++ ) {
                if( trackMap[ i ]) {
                    chanMap[ j++ ] = i;
                } else if( skipUnused ) {
                    chanMap[ j++ ] = -1;
                }
            }
            while( j < numCh2 ) {
                chanMap[ j++ ] = -1;
            }
            return chanMap;
        }
    }
}