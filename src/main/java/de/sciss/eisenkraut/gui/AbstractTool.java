/*
 *  AbstractTool.java
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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import de.sciss.gui.MenuAction;
import de.sciss.gui.TopPainter;

/**
 *  This class describes a generic GUI tool
 *  that can be acquired and dismissed by
 *  a <code>Component</code>.
 */
public abstract class AbstractTool
        implements MouseListener, MouseMotionListener, TopPainter {

    private Component comp;
    private MenuAction actionCancel = null;

    protected AbstractTool() {
        /* empty */
    }

    /**
     *  Makes this tool a component's active
     *  tool. Subclasses should override this
     *  method to perform additional initializations
     *  but are obliged to call the super method,
     *  which will store the component for the
     *  <code>getComponent</code> method and will
     *  activate Mouse and MouseMotion listening.
     *
     *  @param  c                           the <code>Component</code> on which
     *                                      this tool will operate.
     *  @throws IllegalArgumentException    if the passed <code>Component</code>
     *                                      is <code>null</code>
     *  @throws IllegalStateException       if the tool was acquired before.
     */
    public void toolAcquired(Component c) {
        if (comp != null) throw new IllegalStateException();
        if (c    == null) throw new IllegalArgumentException();

        comp = c;
        c.addMouseListener(this);
        c.addMouseMotionListener(this);

        if (comp instanceof JComponent) {
            if (actionCancel == null) actionCancel = new ActionCancel();
            actionCancel.installOn((JComponent) comp, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }

    /**
     *  Makes this tool inactive. This usually
     *  happens when the user switches to
     *  a different tool. Any ongoing drag- and
     *  drop actions or the like are to be
     *  cancelled upon invocation of this method.
     *  Subclasses should override this
     *  method to perform additional cleanup operations
     *  but are obliged to call the super method,
     *  which will forget the component so
     *  <code>getComponent</code> will return
     *  <code>null</code> afterwards, also it will
     *  deactivate Mouse and MouseMotion listening
     *
     *  @param  c                           the <code>Component</code> from
     *                                      which the tool is removed.
     *  @throws IllegalArgumentException    if the passed <code>Component</code>
     *                                      is <code>null</code> or if the
     *                                      tool was attached to a different
     *                                      component.
     *  @throws IllegalStateException       if the tool was not acquired before.
     */
    public void toolDismissed(Component c) {

        if (comp == null) throw new IllegalStateException();
        if (c == null || c != comp) throw new IllegalArgumentException();

        c.removeMouseMotionListener(this);
        c.removeMouseListener(this);
        if ((actionCancel != null) && (comp instanceof JComponent)) {
            actionCancel.deinstallFrom((JComponent) comp, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        cancelGesture();

        comp = null;
    }

    protected abstract void cancelGesture();

    /**
     *  Paint the current (possibly volatile) state
     *  of the tool performance. The should be called
     *  at the end of the component's <code>paintComponent</code>
     *  method. The tool is allowed to modify the <code>Stroke</code>
     *  and the <code>Paint</code> of the <code>Graphics2D</code>
     *  object without needing to restore it. The tool is responsible
     *  for undoing any changes to the <code>AffineTransform</code>
     *  of the <code>Graphics2D</code> object however.
     *
     *  @param  g   a <code>Graphics2D</code> object to paint on.
     *              Initial <code>Stroke</code> and <code>Paint</code>
     *              are undefined. Initial transform should
     *              be negotiated between the component
     *              and the tool, e.g. the <code>Surface</code> object
     *              may guarantee to scale the graphics
     *              to the virtual space (0, 0 ... 1, 1)
     */
    public abstract void paintOnTop(Graphics2D g);

    /**
     *  Returns the component on which the
     *  tool operates.
     *
     *  @return the tool's <code>Component</code> or
     *          <code>null</code> if the tool was dismissed.
     */
    protected Component getComponent() {
        return comp;
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) { /* empty */ }

    public void mouseClicked(MouseEvent e) { /* empty */ }

    public void mouseDragged(MouseEvent e) { /* empty */ }

    public void mouseEntered(MouseEvent e) { /* empty */ }

    public void mouseExited(MouseEvent e) { /* empty */ }

    public void mouseMoved(MouseEvent e) { /* empty */ }

    @SuppressWarnings("serial")
    private class ActionCancel
            extends MenuAction {
        protected ActionCancel() {
            super("tool-cancel", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        }

        public void actionPerformed(ActionEvent e) {
            cancelGesture();
        }
    }
}