/*
 *  ToolListener.java
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

/**
 *  Interface for listening
 *  to switches of the GUI tools
 *
 *  @see    ToolBar#addToolActionListener( ToolActionListener )
 *  @see    ToolActionEvent
 */
public interface ToolActionListener {
    /**
     * Notifies the listener that
     * a tool changed occurred.
     *
     * @param e the event describing
     *          the tool switch
     */
    public void toolChanged(ToolActionEvent e);
}