/*
 *  GroupSync.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import de.sciss.net.OSCBundle;
import de.sciss.util.Disposable;

import java.util.ArrayList;
import java.util.List;

public class GroupSync
        implements Disposable {
    private final List<Child> collChildren = new ArrayList<Child>();
    protected boolean active = false;

    public GroupSync() {
        /* empty */
    }

    public void addChild(Child s) {
        collChildren.add(s);
    }

    public void removeChild(Child s) {
        collChildren.remove(s);
    }

    public boolean isActive() {
        return active;
    }

    public void activate(OSCBundle bndl) {
        active = true;
        for (Child collChild : collChildren) {
            collChild.groupActivate(bndl);
        }
    }

    public void deactivate(OSCBundle bndl) {
        active = false;
        for (Child collChild : collChildren) {
            collChild.groupDeactivate(bndl);
        }
    }

    // -------------- Disposable interface --------------

    public void dispose() {
        collChildren.clear();
        active = false;
    }

    // -------------- internal interfaces --------------

    public static interface Child {
        public void groupActivate(OSCBundle bndl);

        public void groupDeactivate(OSCBundle bndl);
    }
}
