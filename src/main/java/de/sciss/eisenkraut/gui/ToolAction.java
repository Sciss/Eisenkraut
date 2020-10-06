/*
 *  ToolAction.java
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 *  An extension of <code>AbstractAction</code>
 *  that creates a set of related
 *  tool icons and a default mouse
 *  cursor for the tool.
 *  <p>
 *  The <code>actionPerformed</code> method
 *  doesn't do anything at the moment.
 */
@SuppressWarnings("serial")
public class ToolAction extends AbstractAction {

    /**
     *  tool ID : pointer tool
     */
    public static final int POINTER     = 0;
    /**
     *  tool ID : line drawing tool
     */
    public static final int LINE        = 1;
    /**
     *  tool ID : bezier drawing tool
     */
    public static final int CURVE       = 2;
    /**
     *  tool ID : arc drawing tool
     */
    public static final int ARC         = 3;
    /**
     *  tool ID : freehand / pencil tool
     */
    public static final int PENCIL      = 4;
    /**
     *  tool ID : fork / preview tool
     */
    public static final int FORK        = 5;
    /**
     *  tool ID : zoom tool
     */
    public static final int ZOOM        = 6;
    /**
     *  maximum tool ID at the moment
     */
    public static final int MAX_ID      = 6;

    private static final int[] ICONS = {
            GraphicsUtil.ICON_POINTER   , GraphicsUtil.ICON_LINE,
            GraphicsUtil.ICON_CURVE     , GraphicsUtil.ICON_ARC,
            GraphicsUtil.ICON_PENCIL    , GraphicsUtil.ICON_FORK,
            GraphicsUtil.ICON_ZOOM
    };
    private static final Cursor[] CURSORS = {
            Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR), Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
            Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR), Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
            Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR), Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)    // csrZoomIn
    };

    private final int id;
    private final Icon[] icons;

    /**
     *  Creates a tool action with
     *  the given ID.
     *
     *  @param  id  identifier for the tool, e.g. LINE or PENCIL, which determines the icons and mouse pointer
     */
    public ToolAction(int id) {
        super();

        this.id = id;

        icons = GraphicsUtil.createToolIcons(ICONS[id]);
    }

    /**
     *  Attaches the icons for the different
     *  gadget states to a button
     *
     *  @param  b   the button whose icons are to be set
     *
     *  @see    GraphicsUtil#setToolIcons( AbstractButton, Icon[] )
     */
    public void setIcons(AbstractButton b) {
        GraphicsUtil.setToolIcons(b, icons);
    }

    public void actionPerformed(ActionEvent e) {
        // ...
    }

    /**
     *  Returns the tool action's ID
     *
     *  @return the identifier used to construct the tool action
     */
    public int getID() {
        return id;
    }

    /**
     *  Asks for a default mouse cursor
     *
     *  @return a <code>Cursor</code> object usually used for this kind of tool
     */
    public Cursor getDefaultCursor() {
        return CURSORS[id];
    }
}