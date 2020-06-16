/*
 *  CrossFadePanel.java
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

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.OverlayLayout;

@SuppressWarnings("serial")
public class CrossFadePanel
        extends JComponent
        implements ActionListener {

    private JComponent  a, b;
    private float       fade = 0f;
    private float       sourceFade;
    private float       targetFade;
    private long        fadeStart;          // abs millis
    private float       fadeWeight;
    private final javax.swing.Timer timer;

    public CrossFadePanel() {
        super();

        setLayout(new OverlayLayout(this));

        timer = new javax.swing.Timer(50, this);
    }

    public void setComponentA(JComponent c) {
        if (a != null) {
            remove(a);
        }
        a = c;
        checkVisibility();
        add(a, 0);
    }

    public void setComponentB(JComponent c) {
        if (b != null) {
            remove(b);
        }
        b = c;
        checkVisibility();
        add(b, -1);
    }

    /**
     *  Instantly update cross-fade.
     *
     *  @param  fade                        value between 0 (component A only) and 1 (component B only)
     *  @throws IllegalArgumentException    if fade less than 0 or fade greater than 1
     */
    public void setFade(float fade) {
        timer.stop();

        if ((fade < 0f) || (fade > 1f)) {
            throw new IllegalArgumentException(String.valueOf(fade));
        }
        if (fade != this.fade) {
            this.fade = fade;
            checkVisibility();
            repaint();
        }
    }

    private void checkVisibility() {
        if ((a != null) && (a.isVisible() != fade < 1f)) {
            a.setVisible(fade < 1f);
        }
        if ((b != null) && (b.isVisible() != fade > 0f)) {
            b.setVisible(fade > 0f);
        }
    }

    public float getFade() {
        return fade;
    }

    /**
     *  Performs an animated cross-fade.
     *
     *  @param  target                      target value between 0 (component A only) and 1 (component B only)
     *  @param  initialDelay                milliseconds to wait before the fade starts
     *  @param  fadeTime                    fade duration in milliseconds
     *  @throws IllegalArgumentException    if fade less than 0 or fade greater than 1 or initialDelay less than 0
     *                                      or fadeTime less than 0
     */
    public void performFade(float target, int initialDelay, int fadeTime) {

        timer.stop();

        if ((target < 0f) || (target > 1f) || (initialDelay < 0) || (fadeTime < 0)) {
            throw new IllegalArgumentException();
        }

        sourceFade  = fade;
        targetFade  = target;
        fadeStart   = System.currentTimeMillis() + initialDelay;
        fadeWeight  = 1.0f / (Math.max(1, fadeTime));

        if (sourceFade != targetFade) {
            timer.setInitialDelay(initialDelay);
            timer.start();
        }

        checkVisibility();
    }

    protected void paintChildren(Graphics g) {

        final Graphics2D    g2      = (Graphics2D) g;
        final Composite     origCmp = g2.getComposite();

        if ((a != null) && (fade < 1f)) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f - fade));
            a.paint(g);
        }
        if ((b != null) && (fade > 0f)) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));
            b.paint(g);
        }

        g2.setComposite(origCmp);
    }

    // --------------- ActionListener interface ---------------

    public void actionPerformed(ActionEvent e) {

        final long  delta   = System.currentTimeMillis() - fadeStart;
        final float fadePos = Math.max(0f, Math.min(1f, delta * fadeWeight));
        final float newFade = sourceFade * (1f - fadePos) + targetFade * fadePos;

        if (newFade != fade) {
            fade = newFade;
            repaint();
        }

        if (fadePos == 1f) {
            timer.stop();
            checkVisibility();
        }
    }
}
