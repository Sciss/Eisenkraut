/*
 *  TimelineToolBar.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.timeline;

import de.sciss.app.AbstractApplication;
import de.sciss.eisenkraut.gui.BlendingAction;
import de.sciss.eisenkraut.gui.CatchAction;
import de.sciss.eisenkraut.gui.EditModeAction;
import de.sciss.eisenkraut.gui.ToolAction;
import de.sciss.eisenkraut.gui.ToolBar;
import de.sciss.eisenkraut.session.Session;
import de.sciss.gui.GUIUtil;
import de.sciss.util.Disposable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 *	A palette of tools for editing
 *	objects in the timeline frame. Window
 *	key commands are installed: F1 to F6
 *	for the tools and caps lock for toggling
 *	the blending option.
 */
@SuppressWarnings("serial")
public class TimelineToolBar
        extends ToolBar
        implements Disposable {

    private final Map<Integer, JToggleButton> mapToolButtons	= new HashMap<Integer, JToggleButton>();

    /**
     *	Creates a tool palette with
     *	default buttons for editing the timeline frame.
     */
    public TimelineToolBar(Session doc) {
        super(SwingConstants.HORIZONTAL);

        final Preferences		prefs = AbstractApplication.getApplication().getUserPrefs();
        final CatchAction		actionCatch;
        final EditModeAction	actionEditMode;
        final AbstractButton	button;
        final BlendingAction	actionBlending;
        ToolAction				toolAction;
        JToggleButton			toggle;
        ButtonGroup				bg;
        Enumeration<AbstractButton> en;

        actionCatch = new CatchAction(prefs); // .node( PrefsUtil.NODE_SHARED ));
        toggle = actionCatch.getButton();
        GUIUtil.createKeyAction(toggle, KeyStroke.getKeyStroke(KeyEvent.VK_V, 0));
        addToggleButton(toggle, 2);
        addSeparator();

        actionEditMode	= new EditModeAction(doc);
        bg				= actionEditMode.getButtons();
        en				= bg.getElements();
        for( int i = 0; en.hasMoreElements(); i++ ) {
            toggle		= (JToggleButton) en.nextElement();
            GUIUtil.createKeyAction(toggle, KeyStroke.getKeyStroke(KeyEvent.VK_F1 + i, 0));
            addToggleButton(toggle, 3);
        }
        addSeparator();

        toolAction		= new ToolAction(ToolAction.POINTER);
        toggle			= new JToggleButton(toolAction);
        toolAction.setIcons(toggle);
        GUIUtil.createKeyAction(toggle, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        addToggleButton(toggle, 0);
        mapToolButtons.put(toolAction.getID(), toggle);

        toolAction		= new ToolAction(ToolAction.ZOOM);
        toggle			= new JToggleButton(toolAction);
        toolAction.setIcons(toggle);
        GUIUtil.createKeyAction(toggle, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        addToggleButton(toggle, 0);
        mapToolButtons.put(toolAction.getID(), toggle);
      
        addSeparator();
        actionBlending  = doc.getBlendingAction();
        button			= actionBlending.getButton();

        // N.B. caps-lock doesn't work nicely on linux with respect to release.
        GUIUtil.createKeyAction(button, KeyStroke.getKeyStroke(KeyEvent.VK_B /* VK_CAPS_LOCK */, 0));

        add(actionBlending.mkComboBox());
    }

    public void selectTool(int toolID) {
        final AbstractButton b = mapToolButtons.get(toolID);
        if (b != null) b.doClick();
    }

    public void dispose() {
        /* empty */
    }
}