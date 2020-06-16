/*
 *	FlagsPanel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */
 
package de.sciss.eisenkraut.session;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.eisenkraut.util.MapManager;

@SuppressWarnings("serial")
public class FlagsPanel
        extends AbstractFlagsPanel
        implements DynamicListening, SessionCollection.Listener {

    private final SessionCollection sc;
    protected SessionObject so;

    public FlagsPanel(SessionObject so, SessionCollection sc) {
        this(sc);

        this.so = so;
        setOpaque(false);
        new DynamicAncestorAdapter(this).addTo(this);
    }

    protected FlagsPanel(SessionCollection sc) {
        super();

        this.sc = sc;
    }

    // sync : attempt exclusive on doors
    protected void setFlags(int mask, boolean set) {
        int				flags, flagsNew;
        Object			o;
        MapManager		map;
        boolean			soloChange;

        map		= so.getMap();
        o		= map.getValue( SessionObject.MAP_KEY_FLAGS );
        flags	= o == null ? 0 : (Integer) o;

        if( set ) {
            flagsNew	= flags | mask;
        } else {
            flagsNew	= flags & ~mask;
        }
        soloChange = (mask & SessionObject.FLAGS_SOLO) != 0;
        if( soloChange || (mask & SessionObject.FLAGS_SOLO_SAFE) != 0  ) {
            if( set ) {
                flagsNew &= ~SessionObject.FLAGS_VIRTUAL_MUTE;
            } else if( (flagsNew & SessionObject.FLAGS_SOLO) == 0 &&
                       isAny( SessionObject.FLAGS_SOLO, true )) {

                flagsNew |= SessionObject.FLAGS_VIRTUAL_MUTE;
            }
        }
        if( flags != flagsNew ) {
            map.putValue( this, SessionObject.MAP_KEY_FLAGS, flagsNew);
        }
        if( soloChange ) broadcastFlags( 0, true );
    }

    // sync : attempt exclusive on doors
    protected void broadcastFlags(int mask, boolean set) {
        int				i, flags, flagsNew;
        SessionObject	so2;
        Object			o;
        MapManager		map;
        boolean			virtualMute	= false;

        if ((mask & SessionObject.FLAGS_SOLO) == 0 &&
                !((mask & SessionObject.FLAGS_SOLO_SAFE) != 0 && set)) {

            virtualMute = isAny(SessionObject.FLAGS_SOLO, true);
        }
        for (i = 0; i < sc.size(); i++) {
            so2		= sc.get( i );
            map		= so2.getMap();
            o		= map.getValue( SessionObject.MAP_KEY_FLAGS );
            flags	= o == null ? 0 : (Integer) o;

            if( set ) {
                flagsNew	= flags | mask;
            } else {
                flagsNew	= flags & ~mask;
            }
            if( virtualMute && (flagsNew & (SessionObject.FLAGS_SOLO | SessionObject.FLAGS_SOLO_SAFE)) == 0 ) {
                flagsNew |= SessionObject.FLAGS_VIRTUAL_MUTE;
            } else {
                flagsNew &= ~SessionObject.FLAGS_VIRTUAL_MUTE;
            }

            if( flags != flagsNew ) {
                map.putValue( this, SessionObject.MAP_KEY_FLAGS, flagsNew);
            }
        }
    }

    protected boolean isAny(int mask, boolean set) {
        int i, flags;
        SessionObject so2;
        Object o;
        MapManager map;

        for (i = 0; i < sc.size(); i++) {
            so2 = sc.get(i);
            map = so2.getMap();
            o = map.getValue(SessionObject.MAP_KEY_FLAGS);
            flags = o == null ? 0 : (Integer) o;

            if (set) {
                if ((flags & mask) != 0) return true;
            } else {
                if ((flags & mask) == 0) return true;
            }
        }

        return false;
    }

    private void updateButtons() {
        final Object o;
        final int flags;

        o = so.getMap().getValue(SessionObject.MAP_KEY_FLAGS);
        flags = o == null ? 0 : (Integer) o;
        updateButtons(flags);
    }

// ---------------- DynamicListening interface ---------------- 

    public void startListening() {
        updateButtons();
        sc.addListener(this);
    }

    public void stopListening() {
        sc.removeListener(this);
    }

// ---------------- SessionCollection.Listener interface ---------------- 

    public void sessionCollectionChanged( SessionCollection.Event e )
    {
        updateButtons();
    }

    public void sessionObjectChanged( SessionCollection.Event e ) { /* ignore */ }

    public void sessionObjectMapChanged( SessionCollection.Event e )
    {
        if( e.setContains( SessionObject.MAP_KEY_FLAGS )) {
            updateButtons();
        }
    }
}