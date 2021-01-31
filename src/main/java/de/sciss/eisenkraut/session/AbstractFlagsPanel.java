/*
 *	AbstractFlagsPanel.java
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

package de.sciss.eisenkraut.session;

import de.sciss.eisenkraut.gui.GraphicsUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public abstract class AbstractFlagsPanel extends JPanel {
    public static final int FLAGS_SOLO          = 0x01;
    public static final int FLAGS_MUTE          = 0x02;
    public static final int FLAGS_SOLO_SAFE     = 0x04;
    public static final int FLAGS_VIRTUAL_MUTE  = 0x08;

    private final FlagAction actionSolo, actionMute;

    public AbstractFlagsPanel() {
        super(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        actionSolo = new FlagAction(GraphicsUtil.ICON_SOLO, FLAGS_SOLO, FLAGS_SOLO_SAFE);
        actionMute = new FlagAction(GraphicsUtil.ICON_MUTE, FLAGS_MUTE, FLAGS_VIRTUAL_MUTE);

        add(actionSolo.getButton());
        add(actionMute.getButton());
    }

    protected void updateButtons(int flags) {
        actionSolo.set(flags);
        actionMute.set(flags);
    }

    protected abstract void setFlags(int mask, boolean set);

    protected abstract void broadcastFlags(int mask, boolean set);

    protected abstract boolean isAny(int mask, boolean set);

// ---------------- internal classes ----------------

    private abstract static class TriStateAction
            extends AbstractAction {

        private final	Icon			normalState, thirdState;
        private final	AbstractButton	ab;
        private boolean					isNormal	= true;

        protected TriStateAction(int iconID, int normalID, int thirdID) {
            super();

            Icon[] icons = GraphicsUtil.createToolIcons(iconID);
            ab = new JToggleButton(this);
            ab.putClientProperty("styleId", "icon-hover");
            ab.setBorderPainted(false);
            ab.setContentAreaFilled(false);
            ab.setMargin(new Insets(0, 0, 0, 0));
            ab.setOpaque(false);
            ab.setFocusable(false);
            ab.setBorder(null);
            GraphicsUtil.setToolIcons(ab, icons);

            normalState = icons[normalID];
            thirdState  = icons[thirdID];
        }

        protected AbstractButton getButton()
        {
            return ab;
        }

        protected void setThirdState(boolean b) {
            if (b == isNormal) {
                isNormal = !b;
                ab.setIcon(b ? thirdState : normalState);
            }
        }

        protected void setSelected(boolean b) {
            if (ab.isSelected() != b) {
                ab.setSelected(b);
            }
        }
    }

    private class FlagAction extends TriStateAction {
        private final int		normalMask;
        private final int		thirdMask;
        private int				flags;
        private final boolean	isSolo;

        protected FlagAction(int iconID, int normalMask, int thirdMask) {
            super(iconID, 0, 2);

            this.normalMask = normalMask;
            this.thirdMask = thirdMask;
            isSolo = iconID == GraphicsUtil.ICON_SOLO;
        }

        protected void set(int flags) {
            this.flags = flags;
            setThirdState((flags & thirdMask ) != 0);
            setSelected  ((flags & normalMask) != 0);
        }

        public void actionPerformed(ActionEvent e) {
            boolean meta 	= (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0;
            boolean alt 	= (e.getModifiers() & ActionEvent.ALT_MASK) != 0;
            int mask 		= meta && isSolo ? thirdMask : normalMask;
            boolean set = alt && isSolo ? !isAny(mask, true) : (flags & mask) == 0;

            if (alt) {
                broadcastFlags(mask, set);
            } else {
                setFlags(mask, set);
            }
        }
    }
}
