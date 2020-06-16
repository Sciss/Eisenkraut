/*
 *  UndoManager.java
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

package de.sciss.eisenkraut.edit;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import de.sciss.eisenkraut.session.Session;

@SuppressWarnings("serial")
public class UndoManager extends de.sciss.app.UndoManager {

    public UndoManager(Session doc) {
        super(doc);
    }

    protected AbstractAction createUndoAction() {
        return new ActionUndoProc();
    }

    protected AbstractAction createRedoAction() {
        return new ActionRedoProc();
    }

    @SuppressWarnings("serial")
    private class ActionUndoProc
            extends ActionUndo {
        protected ActionUndoProc() { /* empty */ }

        public void actionPerformed(ActionEvent e) {
            if (!((Session) getDocument()).checkProcess()) return;
            super.actionPerformed(e);
        }
    }

    @SuppressWarnings("serial")
    private class ActionRedoProc
            extends ActionRedo {
        protected ActionRedoProc() { /* empty */ }

        public void actionPerformed(ActionEvent e) {
            if (!((Session) getDocument()).checkProcess()) return;
            super.actionPerformed(e);
        }
    }
}
