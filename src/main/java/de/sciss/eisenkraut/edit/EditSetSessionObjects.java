/*
 *  EditSetSessionObjects.java
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

package de.sciss.eisenkraut.edit;

import java.util.ArrayList;
import java.util.List;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.session.SessionObject;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the selection or deselection
 *  of sessionObjects from the <code>SessionObjectCollection</code>
 *  of the session.
 *
 *  @see    UndoManager
 */
@SuppressWarnings("serial")
public class EditSetSessionObjects extends BasicUndoableEdit {

    private Object                      source;
    private final SessionCollection     quoi;
    private final List<SessionObject>   oldSelection;
    private final List<SessionObject>   newSelection;

    /**
     * Create and perform this edit. This
     * invokes the <code>SessionObjectCollection.selectionSet</code> method,
     * thus dispatching a <code>SessionObjectCollectionEvent</code>.
     *
     * @param source           who initiated the action
     * @param collNewSelection the new collection of sessionObjects
     *                         which form the new selection. the
     *                         previous selection is discarded.
     * @param    quoi                XXX
     * @see    de.sciss.eisenkraut.session.SessionCollection
     * @see    de.sciss.eisenkraut.session.SessionCollection.Event
     */
    public EditSetSessionObjects(Object source, SessionCollection quoi,
                                 List<SessionObject> collNewSelection) {
        super();
        this.source     = source;
        this.quoi       = quoi;
        oldSelection    = quoi.getAll();
        newSelection    = new ArrayList<SessionObject>(collNewSelection);
    }

    /**
     * @return false to tell the UndoManager it should not feature
     * the edit as a single undoable step in the history.
     */
    public boolean isSignificant() {
        return false;
    }

    public PerformableEdit perform() {
        quoi.clear(source);
        quoi.addAll(source, newSelection);
        source = this;
        return this;
    }

    /**
     * Undoes the edit
     * by calling the <code>SessionObjectCollection.selectionSet</code>,
     * method. thus dispatching a <code>SessionObjectCollectionEvent</code>.
     */
    public void undo() {
        super.undo();
        quoi.clear(source);
        quoi.addAll(source, oldSelection);
    }

    /**
     * Redoes the selection edit.
     * The original source is discarded
     * which means, that, since a new <code>SessionObjectCollectionEvent</code>
     * is dispatched, even the original object
     * causing the edit will not know the details
     * of the action, hence thorougly look
     * and adapt itself to the new edit.
     */
    public void redo() {
        super.redo();
        perform();
    }

    /**
     * Collapses multiple successive edits
     * into one single edit. The new edit is sucked off by
     * the old one.
     */
    public boolean addEdit(UndoableEdit anEdit) {
        if (anEdit instanceof EditSetSessionObjects) {
            newSelection.clear();
            newSelection.addAll(((EditSetSessionObjects) anEdit).newSelection);
            anEdit.die();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Collapses multiple successive edits
     * into one single edit. The old edit is sucked off by
     * the new one.
     */
    public boolean replaceEdit(UndoableEdit anEdit) {
        if (anEdit instanceof EditSetSessionObjects) {
            oldSelection.clear();
            oldSelection.addAll(((EditSetSessionObjects) anEdit).oldSelection);
            anEdit.die();
            return true;
        } else {
            return false;
        }
    }

    public String getPresentationName() {
        return getResourceString("editSetSessionObjects");
    }
}