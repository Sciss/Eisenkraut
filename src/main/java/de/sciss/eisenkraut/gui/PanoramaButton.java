/*
 *  PanoramaButton.java
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

package de.sciss.eisenkraut.gui;

import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.util.MapManager;
import de.sciss.gui.ModificationButton;
import de.sciss.util.Disposable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

@SuppressWarnings("serial")
public class PanoramaButton
        extends ModificationButton
        implements Disposable {

	private static final Color 		colrNormalLight     = new Color(0x3C, 0x3C, 0x3C, 0xFF);
	private static final Color 		colrPressedLight    = new Color(0x06, 0x06, 0x06, 0xFF);
	private static final Color 		colrDisabledLight   = new Color(0x6C, 0x6C, 0x6C, 0x7F);

    private static final Color 		colrNormalDark      = new Color(0xC3, 0xC3, 0xC3, 0xFF);
    private static final Color 		colrPressedDark     = new Color(0xF9, 0xF9, 0xF9, 0xFF);
    private static final Color 		colrDisabledDark    = new Color(0x93, 0x93, 0x93, 0x7F);

	protected static final Paint 	pntCtrlLight        = new Color(0x00, 0x00, 0x00, 0x7F);
	protected static final Paint 	pntCtrlDark         = new Color(0xFF, 0xFF, 0xFF, 0x7F);

    protected final static Stroke   strkInside          = new BasicStroke(0.333f);
    protected final static Stroke   strkOutside         = new BasicStroke(0.75f);
    protected final static Shape    shpLine             = new Line2D.Double(0.5, 14.5, 28.5, 14.5);
    protected static final Shape    shpCtrl 		    = new Ellipse2D.Double(-2, -2, 5, 5);

    protected AudioTrackPanorama		atPan			= null;
    private final Point2D				ctrlPt			= new Point2D.Double();
    private final AudioTrack			t;
    private final PopupTriggerMonitor	popMon;

    public PanoramaButton(final AudioTrack t, final SessionCollection tracks) {
        this(t, tracks, UIManager.getBoolean("dark-skin"));
    }

	public PanoramaButton(final AudioTrack t, final SessionCollection tracks, final boolean isDark) {
		super(new PantaRei(isDark ? colrNormalDark   : colrNormalLight  , isDark ? pntCtrlDark : pntCtrlLight),
			  new PantaRei(isDark ? colrPressedDark  : colrPressedLight , isDark ? pntCtrlDark : pntCtrlLight),
              new PantaRei(isDark ? colrDisabledDark : colrDisabledLight, isDark ? pntCtrlDark : pntCtrlLight));

		this.t	= t;

        ((PantaRei) icnNormal  ).setCtrlPoint(ctrlPt);
        ((PantaRei) icnPressed ).setCtrlPoint(ctrlPt);
        ((PantaRei) icnDisabled).setCtrlPoint(ctrlPt);

        t.getMap().addListener(new MapManager.Listener() {
            public void mapChanged(MapManager.Event e) {
                if (e.getPropertyNames().contains(AudioTrack.MAP_KEY_PANAZIMUTH) ||
                        e.getPropertyNames().contains(AudioTrack.MAP_KEY_PANSPREAD)) {

                    updateButtonState();
                }
            }

            public void mapOwnerModified(MapManager.Event e) { /* ignore */ }
        });

        popMon = new PopupTriggerMonitor(this);
        popMon.addListener(new PopupTriggerMonitor.Listener() {
            public void componentClicked(PopupTriggerMonitor m) {
                if (atPan == null) {
                    atPan = new AudioTrackPanorama(t, tracks);
                }
                atPan.showPalette();
            }

            public void popupTriggered(PopupTriggerMonitor m) {
                if (atPan == null) {
                    atPan = new AudioTrackPanorama(t, tracks);
                }
                atPan.showPopup(m.getComponent(), m.getComponent().getWidth(), 0, true);
            }
        });

        updateButtonState();
    }

    public void dispose() {
        if (atPan != null) {
            atPan.dispose();
            atPan = null;
        }
        popMon.dispose();
    }

    protected void updateButtonState() {
        Object o;
        o = t.getMap().getValue(AudioTrack.MAP_KEY_PANAZIMUTH);
        double azi = (o != null) && (o instanceof Number) ? ((Number) o).doubleValue() : 0.0;
        o = t.getMap().getValue(AudioTrack.MAP_KEY_PANSPREAD);
        double spread = (o != null) && (o instanceof Number) ? ((Number) o).doubleValue() : 2.0;

        ctrlPt.setLocation(PanoramaPanel.aziAndSpreadToCtrlPoint(azi, spread));
        ctrlPt.setLocation((ctrlPt.getX() + 1.0) * 14.5, (1.0 - ctrlPt.getY()) * 14.5);

        if (isVisible()) repaint();
    }

    private static class PantaRei
            implements Icon {

        private final Paint pntView;
        private final Paint pntCtrl;
        private Point2D			ctrlPt;

        protected PantaRei(Paint pntView, Paint pntCtrl) {
            this.pntView    = pntView;
            this.pntCtrl    = pntCtrl;
        }

        public int getIconWidth() {
            return 31;
        }

        public int getIconHeight() {
            return 31;
        }

        protected void setCtrlPoint(Point2D ctrlPt) {
            this.ctrlPt = ctrlPt;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D		g2			= (Graphics2D) g;
            final Stroke			strkOrig	= g2.getStroke();
            final AffineTransform	atOrig		= g2.getTransform();

            g2.translate(x + 0.5f, y + 0.5f);    // +0.5 = "smoother"
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setPaint(pntView);
            g2.setStroke(strkOutside);
            g2.drawOval(0, 0, 29, 29);
            g2.setStroke(strkInside);
            g2.drawOval(3, 3, 23, 23);
            g2.draw(shpLine);

            g2.translate(ctrlPt.getX() - 0.5f, ctrlPt.getY() - 0.5f);
            g2.setPaint(pntCtrl);
            g2.fill(shpCtrl);

            g2.setTransform(atOrig);
            g2.setStroke(strkOrig);
        }
    }
}
