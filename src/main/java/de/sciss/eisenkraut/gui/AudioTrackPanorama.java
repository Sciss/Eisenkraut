/*
 *  AudioTrackPanorama.java
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

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JPopupMenu;
import javax.swing.WindowConstants;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.gui.CoverGrowBox;
import de.sciss.common.AppWindow;
import de.sciss.util.Disposable;

public class AudioTrackPanorama implements Disposable {
    private final AudioTrack                    t;
    private final ActionListener                actionListener;
    private final SessionCollection.Listener    scListener;
    private final PreferenceChangeListener      prefListener;
    private final Preferences                   audioPrefs;
    private final SessionCollection             tracks;

    protected PanoramaPanel                     pan         = null;
    private AppWindow                           palette     = null;
    private JPopupMenu                          popup       = null;

    private boolean                             listening   = false;

    public AudioTrackPanorama(final AudioTrack t, SessionCollection tracks) {
        final Application app = AbstractApplication.getApplication();

        audioPrefs = app.getUserPrefs().node(PrefsUtil.NODE_AUDIO);

        this.t      = t;
        this.tracks = tracks;

        actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (pan != null) {
                    t.getMap().putValue(AudioTrackPanorama.this, AudioTrack.MAP_KEY_PAN_AZIMUTH, pan.getAzimuth());
                    t.getMap().putValue(AudioTrackPanorama.this, AudioTrack.MAP_KEY_PAN_SPREAD, pan.getSpread());
                }
            }
        };

        scListener = new SessionCollection.Listener() {
            public void sessionCollectionChanged(SessionCollection.Event e) {
                if (e.collectionContains(t) &&
                        (e.getModificationType() == SessionCollection.Event.ACTION_REMOVED)) {

                    dispose();
                }
            }

            public void sessionObjectMapChanged(SessionCollection.Event e) {
                if ((e.getSource() != AudioTrackPanorama.this) && (pan != null) && e.collectionContains(t) &&
                        (e.setContains(AudioTrack.MAP_KEY_PAN_AZIMUTH) || e.setContains(AudioTrack.MAP_KEY_PAN_SPREAD))) {

                    setAzimuthAndSpread();
                }
            }

            public void sessionObjectChanged(SessionCollection.Event e) { /* empty */ }
        };

        prefListener = new PreferenceChangeListener() {
            public void preferenceChange(PreferenceChangeEvent e) {
                if (e.getKey().equals(PrefsUtil.KEY_OUTPUTCONFIG)) {
                    createPanPan();
                }
            }
        };
    }

    private void createPalette() {

        if (palette != null) return;
        if (popup   != null) destroyPopup();

        final Application app = AbstractApplication.getApplication();

        palette = new AppWindow(AbstractWindow.PALETTE);
        palette.setTitle(app.getResourceString("palettePanorama") + " : " + t.getName());
        createPanPan();
        palette.getContentPane().add(CoverGrowBox.create(), BorderLayout.SOUTH);
        palette.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        palette.addListener(new AbstractWindow.Adapter() {
            public void windowClosing(AbstractWindow.Event e) {
                dispose();
            }
        });
        palette.init();
    }

    private void createPopup(boolean disposeOnMouseRelease) {

        if (popup   != null) return;
        if (palette != null) destroyPalette();

        popup = new JPopupMenu();
        createPanPan();
        if (disposeOnMouseRelease) {
            pan.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    dispose();
                }
            });
        }
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent e) {
                dispose();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { /* empty */ }

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) { /* empty */ }
        });
    }

    public void showPalette() {
        createPalette();
        startListening();
        palette.setVisible(true);
        palette.toFront();
    }

    public void showPopup(Component invoker, int x, int y, boolean beginDragging) {
        createPopup(beginDragging);
        startListening();
        popup.show(invoker, x, y);
        if (beginDragging) pan.beginDragging();
    }

    private void startListening() {
        if (!listening) {
            tracks.addListener(scListener);
            audioPrefs.addPreferenceChangeListener(prefListener);
            listening = true;
        }
    }

    private void stopListening() {
        if (listening) {
            tracks.removeListener(scListener);
            audioPrefs.removePreferenceChangeListener(prefListener);
            listening = false;
        }
    }

    public void dispose() {
        stopListening();
        destroyPalette();
        destroyPopup();
        destroyPanPan();
    }

    private void destroyPalette() {
        if (palette == null) return;

        palette.dispose();
        palette = null;
    }

    private void destroyPopup() {
        if (popup == null) return;

        popup.setVisible(false);
        popup = null;
    }

    protected void destroyPanPan() {
        if (pan == null) return;

        pan.getParent().remove(pan);
        pan.removeActionListener(actionListener);
        pan = null;
    }

    protected void createPanPan() {
        final String cfgName = audioPrefs.get(PrefsUtil.KEY_OUTPUTCONFIG, null);
        RoutingConfig oCfg;

        destroyPanPan();

        try {
            if (cfgName != null && audioPrefs.node(PrefsUtil.NODE_OUTPUTCONFIGS).nodeExists(cfgName)) {
                oCfg = new RoutingConfig(audioPrefs.node(PrefsUtil.NODE_OUTPUTCONFIGS).node(cfgName));
                pan = new PanoramaPanel(oCfg.numChannels, oCfg.startAngle);
                setAzimuthAndSpread();
                pan.addActionListener(actionListener);
                if (palette != null) {
                    palette.getContentPane().add(pan, BorderLayout.CENTER);
                    palette.revalidate();
                } else {
                    popup.add(pan, BorderLayout.CENTER);
                    popup.revalidate();
                }
            }
        } catch (BackingStoreException e1) {
            System.err.println("Create pan:");
            e1.printStackTrace();
        }
    }

    protected void setAzimuthAndSpread() {
        final double azi, spread;
        Object o;

        o = t.getMap().getValue(AudioTrack.MAP_KEY_PAN_AZIMUTH);
        if ((o instanceof Number)) {
            azi = ((Number) o).doubleValue();
        } else {
            azi = pan.getAzimuth();
        }
        o = t.getMap().getValue(AudioTrack.MAP_KEY_PAN_SPREAD);
        if ((o instanceof Number)) {
            spread = ((Number) o).doubleValue();
        } else {
            spread = pan.getSpread();
        }
        pan.setAzimuthAndSpread(azi, spread);
    }
}