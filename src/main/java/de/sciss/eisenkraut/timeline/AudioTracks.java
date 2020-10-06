/*
 *  AudioTracks.java
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

package de.sciss.eisenkraut.timeline;

import java.util.List;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.session.SessionCollection;

import de.sciss.app.AbstractApplication;
import de.sciss.common.ProcessingThread;
import de.sciss.timebased.Trail;
import de.sciss.util.Flag;

public class AudioTracks
        extends SessionCollection
        implements OSCRouter {

    private static final String OSC_AUDIO = "audio";

    private final OSCRouterWrapper	osc;
    private final Session			doc;

    private AudioTrail				trail;

    public AudioTracks( Session doc )
    {
        super();

        this.doc	= doc;
        osc			= new OSCRouterWrapper( doc, this );
    }

    public void setTrail( AudioTrail t )
    {
        trail	= t;
    }

    public Trail getTrail()
    {
        return trail;
    }

    public boolean isSelected(AudioTrack t) {
        return doc.selectedTracks.contains(t);
    }

    public static boolean checkSyncedAudio(List<Track.Info> tis, boolean changesTimeline, ProcessingThread context, Flag hasSelectedAudio) {
        hasSelectedAudio.set(false);

        for (Track.Info ti : tis) {
            if (changesTimeline && !ti.getChannelSync()) {
                if (context != null)
                    context.setException(new IllegalStateException(AbstractApplication.getApplication().getResourceString("errAudioWillLooseSync")));
                return false;
            }
            if ((ti.trail instanceof AudioTrail) && ti.selected) {
                hasSelectedAudio.set(true);
            }
        }
        return true;
    }

    // ------------- OSCRouter interface -------------

    public String oscGetPathComponent()
    {
        return OSC_AUDIO;
    }

    public void oscRoute( RoutedOSCMessage rom )
    {
        osc.oscRoute( rom );
    }

    public void oscAddRouter( OSCRouter subRouter )
    {
        osc.oscAddRouter( subRouter );
    }

    public void oscRemoveRouter( OSCRouter subRouter )
    {
        osc.oscRemoveRouter( subRouter );
    }

    public Object oscQuery_count()
    {
        return size();
    }
}