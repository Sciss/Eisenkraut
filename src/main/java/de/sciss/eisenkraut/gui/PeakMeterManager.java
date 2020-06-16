/*
 *  PeakMeterManager.java
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

package de.sciss.eisenkraut.gui;

import java.awt.EventQueue;
import javax.swing.JComponent;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.Server;
import de.sciss.net.OSCBundle;

import de.sciss.eisenkraut.net.GroupSync;
import de.sciss.eisenkraut.net.GroupAnySync;
import de.sciss.eisenkraut.net.MeterManager;
import de.sciss.gui.PeakMeterView;

public class PeakMeterManager
        implements PeakMeterView, DynamicListening, GroupSync.Child {

    private final MeterManager mm;
    private final DynamicAncestorAdapter daa;

    private PeakMeterView view = null;
    private boolean added;                        // registered with the MeterManager and NodeWatcher
    private Server s = null;
    private Group g = null;
    private int[] channels = null;
    private boolean task = false;    // whether currently metering
    private boolean showing = false;    // whether level meters are displayed
    private final GroupAnySync anySync;

    public PeakMeterManager(MeterManager mm) {
        this.mm = mm;
        daa = new DynamicAncestorAdapter(this);
        anySync = new GroupAnySync();
        anySync.addChild(this);
    }

    // ----------------- PeakMeterView interface -----------------

    public int getNumChannels()
    {
        return (view != null) ? view.getNumChannels() : 0;
    }

    public boolean meterUpdate( float[] peakRMSPairs, int offset, long time ) {
        final boolean dirty;

        dirty = view != null && view.meterUpdate(peakRMSPairs, offset, time);

        if (!task && !dirty) {
            checkStopTasking();
        }

        return dirty;
    }

    public void dispose() {
        if (view != null) {
            view.dispose();
            view = null;
        }
        anySync.dispose();
        showing = false;
        if (daa.getComponent() != null) daa.remove();
        if (added) {
            mm.removeListener(this);
            added = false;
        }
        s = null;
        g = null;
        channels = null;
    }

    public void clearMeter()
    {
        if( view != null ) view.clearMeter();
    }

    protected void checkStopTasking() {
        if (added && !task) {
            mm.setListenerTask(this, false, null);
        }
    }

    public void setDynamicComponent(JComponent c) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        if (daa.getComponent() != null) {
            daa.remove();
        }
        daa.addTo(c);
    }

    public void setGroup(Group g) {
        this.g = g;
    }

    public void clearInputs() {
        setInputs(null, null);
    }

    public void setInputs(Bus b) {
        final int[] newChannels = new int[b.getNumChannels()];

        for (int i = 0, j = b.getIndex(); i < newChannels.length; ) {
            newChannels[i++] = j++;
        }

        setInputs(b.getServer(), newChannels);
    }

    public void setInputs(Server s, int[] channels) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        if (added) {
            mm.removeListener(this);
            added = false;
        }

        this.s = s;
        this.channels = channels;
        checkAdded();
    }

    private void checkAdded() {
        if (added) {
            if ((s == null) || !showing) {
                mm.removeListener(this);
                added = false;
                clearMeter();
            }
        } else {
            if ((s != null) && showing) {
                mm.addListener(this, s, channels, g, task);
                added = true;
            }
        }
    }

    public void setView(PeakMeterView view) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        this.view = view;
    }

    public void addTaskSync(GroupSync n) {
        anySync.addSync(n);
    }

    public void removeTaskSync(GroupSync n) {
        anySync.removeSync(n);
    }

    public void clearTaskSyncs() {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        anySync.clearSyncs();
        updateTask(null);
    }

    private void updateTask(OSCBundle bndl) {
        final boolean newTask = anySync.isActive();
        if (newTask != task) {
            task = newTask;
            if (added) {
                if (task) {
                    mm.setListenerTask(this, true, bndl);
                }
            }
        }
    }

    // -------------- GroupSync.Child interface --------------

    public void groupActivate(OSCBundle bndl) {
        updateTask(bndl);
    }

    public void groupDeactivate(OSCBundle bndl) {
        updateTask(bndl);
    }

    // -------------- DynamicListening interface --------------

    public void startListening() {
        showing = true;
        updateTask(null);
        checkAdded();
    }

    public void stopListening() {
        showing = false;
        checkAdded();
        updateTask(null);
    }
}