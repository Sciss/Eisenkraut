/*
 *  RoundedBorder.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;

/**
 *  A border looking like aqua's search-field border
 */
@SuppressWarnings("serial")
public class RoundedBorder extends AbstractBorder {

    private static final int radius     = 6; // 11;
    private static final int diameter   = 12; // 22; // radius << 1;

    private final Insets insets = new Insets(3, radius, 1, radius); // top left bottom right

    private static final Color colrDark     = new Color(0x00, 0x00, 0x00, 0x88);
    // private static final Color colrLight    = new Color(0xFF, 0xFF, 0xFF, 0x78);
    private static final Color colrLight    = new Color(0xA0, 0xA0, 0xA0, 0x18);
    private static final Color colrDark2    = new Color(0x00, 0x00, 0x00, 0x18);
    private static final Color colrClearD   = new Color(0x00, 0x00, 0x00, 0x00);
    private static final Color colrClearL   = new Color(0xA0, 0xA0, 0xA0, 0x00);
    private static final Stroke strkOutline = new BasicStroke(1.0f);
    private static final Stroke strkInline  = new BasicStroke(2.0f);

    private Color colrBg = Color.white;
    private Paint pntInline, pntOutlineT, pntOutlineB;

    private Shape shpBg, shpInline, shpOutline;

    private int recentWidth  = -1;
    private int recentHeight = -1;

    public RoundedBorder() {
        super();
    }

    public RoundedBorder(Color c) {
        this();
        setColor(c);
    }

    public void setColor(Color c) {
        colrBg = c;
    }

    public Insets getBorderInsets(Component c) {
        return new Insets(insets.top, insets.left, insets.bottom, insets.right);
    }

    public Insets getBorderInsets(Component c, Insets i) {
        i.top       = insets.top;
        i.left      = insets.left;
        i.bottom    = insets.bottom;
        i.right     = insets.right;
        return i;
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        final Graphics2D g2 = (Graphics2D) g;
        final AffineTransform atOrig = g2.getTransform();

        g2.translate(x, y);

        if ((width != recentWidth) || (height != recentHeight)) {
            if (height != recentHeight) {
                final int hh = height >> 1;
                pntOutlineT = new GradientPaint(0, 0, colrDark, 0, hh, colrClearD);
                pntOutlineB = new GradientPaint(0, hh, colrClearL, 0, height - 2, colrLight);
                pntInline   = new GradientPaint(0, 0, colrDark2, 0, hh, colrClearD);
            }

            final RectangularShape r = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, diameter, diameter);
            final RectangularShape r2 = new RoundRectangle2D.Float(1.0f, 1.0f, width - 2, height - 2, diameter, diameter);
            final Area a = new Area(r);
            a.subtract(new Area(new Rectangle2D.Float(insets.left, insets.top,
                    width - insets.left - insets.right, height - insets.top - insets.bottom)));

            shpOutline  = strkOutline.createStrokedShape(r2);
            shpInline   = strkInline.createStrokedShape(r2);
            shpBg       = a;

            recentWidth     = width;
            recentHeight    = height;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE );
        g2.setPaint(colrBg);
        g2.fill(shpBg);
        g2.setPaint(pntOutlineT);
        g2.fill(shpOutline);
        g2.setPaint(pntOutlineB);
        g2.fill(shpOutline);
        g2.translate(0, 1);
        g2.setPaint(pntInline);
        g2.fill(shpInline);

        g2.setTransform(atOrig);
    }
}