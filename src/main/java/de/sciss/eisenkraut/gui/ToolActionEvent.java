/*
 *  ToolActionEvent.java
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

import de.sciss.app.BasicEvent;

/**
 *  This kind of event is fired
 *  from a <code>ToolBar</code> when
 *  the user switched to a different tool.
 *
 *  @see    ToolBar#addToolActionListener( ToolActionListener )
 *  @see    ToolActionListener
 *  @see    ToolAction
 */
@SuppressWarnings("serial")
public class ToolActionEvent
        extends BasicEvent {

    // --- ID values ---
    /**
     *  returned by getID() : the tool was changed
     */
    public static final int CHANGED = 0;

    private final ToolAction toolAction;

    /**
     *  Constructs a new <code>ToolActionEvent</code>
     *
     *  @param  source      who originated the action
     *  @param  id          <code>CHANGED</code>
     *  @param  when        system time when the event occured
     *  @param  toolAction  the new ToolAction to which was switched
     */
    public ToolActionEvent(Object source, int id, long when, ToolAction toolAction) {
        super(source, id, when);

        this.toolAction = toolAction;
    }

    /**
     *  Queries the new tool
     *
     *  @return the new tool action of the <code>ToolBar</code> or <code>ToolPalette</code>.
     */
    public ToolAction getToolAction() {
        return toolAction;
    }

    public boolean incorporate(BasicEvent oldEvent) {
        return oldEvent instanceof ToolActionEvent &&
                getSource() == oldEvent.getSource() &&
                getID() == oldEvent.getID();
    }
}