/*
 *  PanoramaPanel.java
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

package de.sciss.eisenkraut.gui;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PanoramaPanel extends JComponent {

    private final AffineTransform at = new AffineTransform();
    protected static final Insets insets = new Insets(1, 1, 1, 1);
    private static final Shape shpCtrlIn = new Ellipse2D.Double(-2, -2, 5, 5);
    private static final Area shpCtrlOut;

    private static final Paint pntCtrlInLight   = new Color(0x00, 0x00, 0x00, 0x7F);
    private static final Paint pntCtrlOutLight  = new Color(0x00, 0x00, 0x00, 0x3F);
    private static final Paint pntCtrlOutSLight = new Color(0x00, 0x00, 0xFF, 0x7F);

    private static final Paint pntCtrlInDark    = new Color(0xFF, 0xFF, 0xFF, 0x7F);
    private static final Paint pntCtrlOutDark   = new Color(0xFF, 0xFF, 0xFF, 0x3F);
    private static final Paint pntCtrlOutSDark  = new Color(0x7F, 0x7F, 0xFF, 0x7F);

    private final Paint pntCtrlIn, pntCtrlOut, pntCtrlOutS;

    private final List<Shape> outlines      = new ArrayList<Shape>();
    private final List<Shape> tOutlines     = new ArrayList<Shape>();
    private final List<Paint> outlinePaints = new ArrayList<Paint>();

    private final List<Shape> areas         = new ArrayList<Shape>();
    private final List<Shape> tAreas        = new ArrayList<Shape>();
    private final List<Paint> areaPaints    = new ArrayList<Paint>();

    private final Point2D ctrlPt = new Point2D.Double(0.0, 0.75);

    protected double recentRadius = -1;
    private boolean recalculate = true;
    protected boolean isDragging = false;

    protected final double startAngle, deltaAngle;
    private final int numSpots;
    protected double azi, spread;

    private static final List<ActionListener> collListeners = new ArrayList<ActionListener>();

    private final boolean isDark;

    static {
        shpCtrlOut = new Area(new Ellipse2D.Double(-7, -7, 15, 15));
        shpCtrlOut.subtract(new Area(new Ellipse2D.Double(-4, -4, 9, 9)));
    }

    public PanoramaPanel(final int numSpots, double startAng) {
        super();

        isDark      = GraphicsUtil.isDarkSkin();
        pntCtrlIn   = isDark ? pntCtrlInDark   : pntCtrlInLight;
        pntCtrlOut  = isDark ? pntCtrlOutDark  : pntCtrlOutLight;
        pntCtrlOutS = isDark ? pntCtrlOutSDark : pntCtrlOutSLight;

        if (startAng < 0) {
            startAng = 360 - ((-startAng) % 360);
        }
        startAngle = startAng % 360;
        deltaAngle = 360.0 / numSpots;
        this.numSpots = numSpots;

        outlines.add(new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        outlinePaints.add(isDark ? Color.white : Color.black);
        outlines.add(new Ellipse2D.Double(-0.75, -0.75, 1.5, 1.5));
        outlinePaints.add(pntCtrlOut);

        double angDeg, angRad, dx, dy;
        double speakerWidth = Math.max(4, Math.min(10, deltaAngle / 2));
        Arc2D arc1 = new Arc2D.Double(-1.15, -1.15, 2.3, 2.3, 0, speakerWidth, Arc2D.PIE);
        Arc2D arc2 = new Arc2D.Double(-1.05, -1.05, 2.1, 2.1, 0, speakerWidth, Arc2D.PIE);
        Area area;

        for (int i = 0; i < numSpots; i++) {
            angDeg = startAngle + i * deltaAngle;
            angRad = (-angDeg + 90) * Math.PI / 180;
            dx = Math.cos(angRad);
            dy = Math.sin(angRad);
            outlines.add(new Line2D.Double(0.0, 0.0, dx, dy));
            outlinePaints.add(pntCtrlOut);
            arc1.setAngleStart(angDeg - 90 - speakerWidth / 2);
            arc2.setAngleStart(angDeg - 90 - speakerWidth / 2);
            area = new Area(arc1);
            area.subtract(new Area(arc2));
            areas.add(area);
            areaPaints.add(pntCtrlIn);
        }

        setMinimumSize  (new Dimension( 64,  64));
        setPreferredSize(new Dimension(128, 128));
        setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));

        MouseInputAdapter mia = new MouseInputAdapter() {
            public void mousePressed(MouseEvent e) {
                final Point2D mousePt = getVirtualMousePos(e);
                isDragging = true;
                processDrag(mousePt, !e.isControlDown());
            }

            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                repaint();
            }

            // x ^= radius, y ^= angleRad
            private Point2D getVirtualMousePos(MouseEvent e) {
                final double x = ((e.getX() - insets.left) / recentRadius) - 1.15;
                final double y = 1.15 - ((e.getY() - insets.top) / recentRadius);

                return new Point2D.Double(
                        Math.min(1.0, Math.sqrt(x * x + y * y)),
                        Math.atan2(y, x));
            }

            public void mouseDragged(MouseEvent e) {
                if (isDragging) processDrag(getVirtualMousePos(e), !e.isControlDown());
            }

            public void mouseMoved(MouseEvent e) {
                mouseDragged(e);
            }

            private void processDrag(Point2D mousePt, boolean snap) {
                double dragAngDeg, temp, temp2; // , spreadDegH;

                if (snap) {
                    if (mousePt.getX() < 0.1) {
                        mousePt.setLocation(0.0, mousePt.getY());
                    } else if (Math.abs(mousePt.getX() - 0.75) < 0.1) {
                        mousePt.setLocation(0.75, mousePt.getY());
                    } else if (1.0 - mousePt.getX() < 0.1) {
                        mousePt.setLocation(1.0, mousePt.getY());
                    }

                    dragAngDeg = -(mousePt.getY() * 180 / Math.PI) + 90;
                    if (dragAngDeg < 0) dragAngDeg += 360;

                    for (int i = 0; i < numSpots * 2; i++) {
                        temp = (startAngle + (deltaAngle * i) / 2) % 360;
                        temp2 = Math.abs(temp - dragAngDeg);
                        if (temp2 > 180) temp2 = 360 - temp2;
                        if (temp2 < 5) {
                            mousePt.setLocation(mousePt.getX(), (-temp + 90) * Math.PI / 180);
                            break;
                        }
                    }
                }

                // azi + spread
                if (mousePt.getX() <= 0.75) {
                    spread = 1.0 - (mousePt.getX() / 0.75);
                } else {
                    spread = (0.75 - mousePt.getX()) / 0.25;
                }
                dragAngDeg = -(mousePt.getY() * 180 / Math.PI) + 90;

                setAzimuthAndSpread(dragAngDeg, spread);
                dispatchAction();
            }
        };

        addMouseListener(mia);
        addMouseMotionListener(mia);
    }

    public void beginDragging()
    {
        isDragging = true;
    }

    protected void dispatchAction() {
        final ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);

        synchronized (collListeners) {
            for (ActionListener collListener : collListeners) {
                collListener.actionPerformed(e);
            }
        }
    }

    public void addActionListener(ActionListener l) {
        synchronized (collListeners) {
            collListeners.add(l);
        }
    }

    public void removeActionListener(ActionListener l) {
        synchronized (collListeners) {
            collListeners.remove(l);
        }
    }

    private void recalculateColours() {
        double temp, spreadDegH;

        if (spread >= 0.0) {
            spreadDegH = spread * (1.0 - 2.0 / numSpots) + 2.0 / numSpots;
        } else {
            spreadDegH = (2.0 + spread) / numSpots;
        }
        spreadDegH *= 180;

        for (int i = 0; i < numSpots; i++) {
            temp = (startAngle + deltaAngle * i) % 360;
            temp = Math.abs(temp - azi);
            if (temp > 180) temp = 360 - temp;
            temp = Math.max(0.0, 1.0 - temp / spreadDegH);
            final double tempI = 1.0 - temp;
            final int red   = (int) ((temp * 0xC0) + (isDark ? tempI * 0xFF : 0));
            final int green = isDark ? (int) (tempI * 0xFF) : 0;
            final int blue  = green;
            final int alpha = (int) ((temp + 1.0) * 0x7F);
            areaPaints.set(i, new Color(red, green, blue, alpha));
        }
    }

    public void setAzimuthAndSpread(double azi, double spread) {
        this.spread = Math.max(-1.0, Math.min(1.0, spread));
        this.azi    = azi < 0 ? 360 - ((-azi) % 360) : azi % 360;

        ctrlPt.setLocation(PanoramaPanel.aziAndSpreadToCtrlPoint(this.azi, this.spread));

        recalculateColours();
        if (isVisible()) repaint();
    }

    public static Point2D aziAndSpreadToCtrlPoint(double azi, double spread) {
        final double r = spread >= 0.0 ? (1.0 - spread) * 0.75 : 0.75 - spread * 0.25;
        final double angRad = (-azi + 90) * Math.PI / 180;

        return new Point2D.Double(r * Math.cos(angRad), r * Math.sin(angRad));
    }

    public double getAzimuth()
    {
        return azi;
    }

    public double getSpread()
    {
        return spread;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        final Graphics2D g2 = (Graphics2D) g;
        final int currentWidth  = getWidth()  - insets.left - insets.right;
        final int currentHeight = getHeight() - insets.top  - insets.bottom;
        final AffineTransform oldAT = g2.getTransform();
        final double radius = Math.min(currentWidth, currentHeight) / 2.3;

        double trnsX, trnsY;

        if ((radius != recentRadius) || recalculate) {
            recentRadius = radius;
            at.setToScale(radius, -radius);
            at.translate(1.15, -1.15);
            recalculateTransforms();
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(insets.left, insets.top);
        for (int i = 0; i < tOutlines.size(); i++) {
            g2.setPaint(outlinePaints.get(i));
            g2.draw(tOutlines.get(i));
        }
        for (int i = 0; i < tAreas.size(); i++) {
            g2.setPaint(areaPaints.get(i));
            g2.fill(tAreas.get(i));
        }

        trnsX = (ctrlPt.getX() + 1.15) * radius;
        trnsY = (1.15 - ctrlPt.getY()) * radius;
        g2.translate(trnsX, trnsY);
        g2.setPaint(pntCtrlIn);
        g2.fill(shpCtrlIn);
        if (isDragging) {
            g2.setPaint(pntCtrlOutS);
        } else {
            g2.setPaint(pntCtrlOut);
        }
        g2.fill(shpCtrlOut);
        g2.translate(-trnsX, -trnsY);

        g2.setTransform(oldAT);
    }

    private void recalculateTransforms() {
        tOutlines.clear();
        for (Shape outline : outlines) {
            tOutlines.add(at.createTransformedShape(outline));
        }
        tAreas.clear();
        for (Shape area : areas) {
            tAreas.add(at.createTransformedShape(area));
        }
        recalculate = false;
    }
}