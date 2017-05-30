/*  PrefListModel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

 package de.sciss.eisenkraut.gui;

import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
//import java.util.prefs.NodeChangeEvent;
//import java.util.prefs.NodeChangeListener;
import java.util.prefs.Preferences;

import javax.swing.AbstractListModel;
//import javax.swing.ListModel;
//import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import de.sciss.app.PreferenceNodeSync;
import de.sciss.util.Disposable;

/**
 *  Warning: Because java.util.Preferences is broken (childAdded never called)
 *  it is forbidden to change the preferences concurrently, since it's
 *  not monitored. Only commit changes by calling any of the method
 *  of the list model
 */
public abstract class PrefListModel
        extends AbstractListModel
        implements PreferenceNodeSync, Disposable {

    private Preferences prefs;
    private final List<Object> collData = new ArrayList<Object>();

    public PrefListModel() {
         /* empty */
    }

    public void dispose() {
        if (prefs != null) {
            prefs = null;
        }
        collData.clear();
        final ListDataListener[] l = getListDataListeners();
        for (int i = l.length - 1; i >= 0; i--) {
            removeListDataListener(l[i]);
        }
    }

    public void setPreferences(Preferences prefs) {
        collData.clear();
        if (prefs != null) {
            final String[] names;
            try {
                names = prefs.childrenNames();
            } catch (BackingStoreException e1) {
                e1.printStackTrace();
                return;
            }
            Arrays.sort(names);
            for (int i = 0; i < names.length; i++) {
                if (!names[i].equals(String.valueOf(i))) {
                    collData.clear();
                    new IllegalStateException("Illegal preferences node '" + names[i] + "'").printStackTrace();
                    return;
                }
                collData.add(dataFromNode(prefs.node(names[i])));
            }
        }
        this.prefs = prefs;
        fireContentsChanged(this, 0, collData.size());
    }

    public Object getElementAt( int index )
    {
        return collData.get( index );
    }

    public int getSize()
    {
        return collData.size();
    }

    public void remove( int index )
    throws BackingStoreException
    {
        if( (index < 0) || (index >= collData.size()) ) throw new ArrayIndexOutOfBoundsException();
        final Preferences node = prefs.node( String.valueOf( collData.size() - 1 ));
        node.removeNode();
        collData.remove( index );
        for( int i = index; i < collData.size(); i++ ) {
            dataToNode( i );
        }
    }

    public void add(Object o) {
        add(collData.size(), o);
    }

    public void add(int index, Object o) {
        collData.add(index, o);
        for (int i = collData.size() - 1; i >= index; i--) {
            dataToNode(i);
        }
        fireIntervalAdded(this, index, index + 1);
    }

    private void dataToNode(int index) {
        if (prefs == null) return;

        final Object data = collData.get(index);
        final String name = String.valueOf(index);
        final boolean existed;

        try {
            existed = prefs.nodeExists(name);
            if (existed) {
                prefs.node(name).clear();
            }
        } catch (BackingStoreException e1) {
            e1.printStackTrace();
        }
        dataToNode(data, prefs.node(name));
    }

    protected abstract Object dataFromNode(Preferences node);

    protected abstract void dataToNode(Object data, Preferences node);

    public Object get(int index) {
        return getElementAt(index);
    }
}
