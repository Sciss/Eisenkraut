/*
 *  TimelineToolBar.java
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

package de.sciss.eisenkraut.timeline;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.eisenkraut.gui.BlendingAction;
import de.sciss.eisenkraut.gui.CatchAction;
import de.sciss.eisenkraut.gui.EditModeAction;
import de.sciss.eisenkraut.gui.ToolAction;
import de.sciss.eisenkraut.gui.ToolBar;
import de.sciss.eisenkraut.session.Session;
import de.sciss.gui.DoClickAction;
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

    private static void createKeyAction2(AbstractButton comp, int i) {
        final Action    a   = new DoClickAction(comp);
        final InputMap  im  = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap am  = comp.getActionMap();
        final KeyStroke ks1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1 + i, 0);
        final KeyStroke ks2 = KeyStroke.getKeyStroke(KeyEvent.VK_1  + i, 0);
        im.put(ks1, "shortcut");
        im.put(ks2, "shortcut");
        am.put("shortcut", a);
    }

    /**
     *	Creates a tool palette with
     *	default buttons for editing the timeline frame.
     */
    public TimelineToolBar(Session doc) {
        super(SwingConstants.HORIZONTAL);

        final Application       app     = AbstractApplication.getApplication();
        final Preferences		prefs   = app.getUserPrefs();
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
        for (int i = 0; en.hasMoreElements(); i++) {
            toggle		= (JToggleButton) en.nextElement();
            createKeyAction2(toggle, i);
            addToggleButton(toggle, 3);
        }
        addSeparator();

        toolAction		= new ToolAction(ToolAction.POINTER);
        toggle			= new JToggleButton(toolAction);
        toggle.setToolTipText(app.getResourceString("ttTimelinePointer"));
        toolAction.setIcons(toggle);
        createKeyAction2(toggle, 4);
        addToggleButton(toggle, 0);
        mapToolButtons.put(toolAction.getID(), toggle);

        toolAction		= new ToolAction(ToolAction.ZOOM);
        toggle			= new JToggleButton(toolAction);
        toggle.setToolTipText(app.getResourceString("ttTimelineZoom"));
        toolAction.setIcons(toggle);
        createKeyAction2(toggle, 5);
        addToggleButton(toggle, 0);
        mapToolButtons.put(toolAction.getID(), toggle);
      
        addSeparator();
        actionBlending  = doc.getBlendingAction();
        button			= actionBlending.getButton();
        button.setToolTipText(app.getResourceString("ttTimelineBlending"));

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