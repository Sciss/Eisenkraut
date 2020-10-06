/*
 *  SyncCompoundSessionObjEdit.java
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

package de.sciss.eisenkraut.edit;

import java.util.ArrayList;
import java.util.List;

import de.sciss.eisenkraut.session.SessionObject;

import de.sciss.app.AbstractCompoundEdit;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is used
 *  to synchronize an Undo or Redo operation of compound
 *  transmitter (or trajectory) modifying edits.
 *  The synchronization is provided by waiting exclusively
 *  for a given door.
 *
 *  @see            UndoManager
 */
@SuppressWarnings("serial")
public class CompoundSessionObjEdit extends AbstractCompoundEdit {

    private Object                      source;
    private final List<SessionObject>   collSessionObjects;
    private final int                   ownerModType;
    private final Object                ownerModParam, ownerUndoParam;

    /**
     *  Creates a <code>CompoundEdit</code> object, whose Undo/Redo
     *  actions are synchronized. When the edit gets finished
     *  by calling the <code>end</code> method, the
     *  <code>transmitterCollection.modifiedAll</code> method is called,
     *  thus dispatching a <code>transmitterCollectionEvent</code>.
     *
     *  @param  source              Event-Source for <code>doc.transmitterCollection.modified</code>.
     *                              Gets discarded upon undo / redo invocation.
     *  @param  collSessionObjects  list of transmitters to be edited.
     *  @param  ownerModType        XXX
     *  @param  ownerModParam       XXX
     *  @param  ownerUndoParam      XXX
     *  @param  representationName   ...
     *
     *  @see    de.sciss.eisenkraut.session.SessionCollection
     *  @see    de.sciss.eisenkraut.session.SessionCollection.Event
     */
    public CompoundSessionObjEdit(Object source, List<SessionObject> collSessionObjects,
                                  int ownerModType, Object ownerModParam,
                                  Object ownerUndoParam, String representationName) {
        super(representationName);

        this.source             = source;
        this.collSessionObjects = new ArrayList<SessionObject>(collSessionObjects);
        this.ownerModType       = ownerModType;
        this.ownerModParam      = ownerModParam;
        this.ownerUndoParam     = ownerUndoParam;
    }

    /**
     * calls <code>doc.transmitterCollection.modifiedAll</code>.
     * The original edit source is discarded.
     */
    protected void undoDone() {
        int i;

        for (i = 0; i < collSessionObjects.size(); i++) {
            (collSessionObjects.get(i)).getMap().dispatchOwnerModification(
                    source, ownerModType, ownerUndoParam);
        }
    }

    /**
     * calls <code>doc.transmitterCollection.modifiedAll</code>.
     * The original edit source is discarded.
     */
    protected void redoDone() {
        int i;

        for (i = 0; i < collSessionObjects.size(); i++) {
            (collSessionObjects.get(i)).getMap().dispatchOwnerModification(
                    source, ownerModType, ownerModParam);
        }
    }

    protected void cancelDone() { /* empty */ }

    /**
     *  Finishes the compound edit and calls
     *  <code>doc.transmitterCollection.modifiedAll</code>
     *  using the source provided in the constructor.
     */
    public void end() {
        super.end();
        redoDone();
        source = this;
    }
}