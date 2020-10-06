/*
 *  RequestFocusAction.java
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

package de.sciss.eisenkraut.gui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import java.awt.event.ActionEvent;

public class RequestFocusAction extends AbstractAction {
    private final JComponent target;

    public RequestFocusAction(JComponent target) {
        super();
        this.target = target;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        target.requestFocusInWindow();
    }
}
