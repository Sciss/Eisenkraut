/*
 *  WaveformView.java
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

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.io.DecimatedSonaTrail;
import de.sciss.eisenkraut.io.DecimatedWaveTrail;
import de.sciss.eisenkraut.io.DecimationInfo;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.ComponentHost;
import de.sciss.io.Span;
import de.sciss.util.Disposable;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
public class WaveformView
        extends JComponent
        implements Disposable {

    private int fullChannels;

    private Insets insets = new Insets(0, 0, 0, 0);
    private int vGap = 1;

    private Rectangle r = new Rectangle();

    private static final Paint pntNull = new Color(0x7F, 0x7F, 0x00, 0xC0);
    private static final Stroke strkNull = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
            1.0f, new float[]{4.0f, 4.0f}, 0.0f);

    private int     verticalScale   = PrefsUtil.VSCALE_AMP_LIN;
    private float   ampLinMin       = -1.0f;    // minimum vector value
    private float   ampLinMax       = 1.0f;     // maximum vector value
    private float   ampLogMin       = -60f;
    private float   ampLogMax       = 0f;
    private float   freqMin         = 27.5f;
    private float   freqMax         = 20000f;
    private boolean nullLine        = false;

    private Span viewSpan = new Span();
    private final Session doc;

    private DecimationInfo info = null; // most recent one!

    private final ComponentHost host;

    public WaveformView(Session doc) {
        this(doc, null);
    }

    public WaveformView(Session doc, ComponentHost host) {
        super();

        this.host = host;

        final AudioTrail at = doc.getAudioTrail();
        fullChannels = at.getChannelNum();

        this.doc = doc;
    }

    public void setNullLine(boolean onOff) {
        if (nullLine != onOff) {
            nullLine = onOff;
            triggerRedisplay();
        }
    }

    public boolean getNullLine() {
        return nullLine;
    }

    public void setVerticalScale(int mode) {
        if (verticalScale != mode) {
            verticalScale = mode;
            triggerRedisplay();
        }
    }

    public int getVerticalScale() {
        return verticalScale;
    }

    /**
     *  Gets the minimum allowed y value
     *
     *  @return the minimum specified function value
     */
    public float getAmpLinMin()
    {
        return ampLinMin;
    }

    /**
     *  Gets the maximum allowed y value
     *
     *  @return the maximum specified function value
     */
    public float getAmpLinMax()
    {
        return ampLinMax;
    }

    public float getAmpLogMin()
    {
        return ampLogMin;
    }


    public float getAmpLogMax()
    {
        return ampLogMax;
    }

    /**
     *  Changes the allowed range for vector values.
     *  Influences the graphics display such that
     *  the top margin of the panel corresponds to max
     *  and the bottom margin corresponds to min. Also
     *  user drawings are limited to these values unless
     *  wrapY is set to true (not yet implemented).
     *
     *  Warning:    the current vector is left untouched,
     *              even if values lie outside the new
     *              allowed range.
     *
     *  @param  min     new minimum y value
     *  @param  max     new maximum y value
     */
    public void setAmpLinMinMax(float min, float max) {
        if ((this.ampLinMin != min) || (this.ampLinMax != max)) {
            this.ampLinMin = min;
            this.ampLinMax = max;

            if (verticalScale == PrefsUtil.VSCALE_AMP_LIN) triggerRedisplay();
        }
    }

    public void setAmpLogMinMax(float min, float max) {
        if ((this.ampLogMin != min) || (this.ampLogMax != max)) {
            this.ampLogMin = min;
            this.ampLogMax = max;

            if (verticalScale != PrefsUtil.VSCALE_AMP_LIN) triggerRedisplay();
        }
    }

    public float getFreqMin() {
        return freqMin;
    }

    public float getFreqMax() {
        return freqMax;
    }

    public void setFreqMinMax(float min, float max) {
        if ((this.freqMin != min) || (this.freqMax != max)) {
            this.freqMin = min;
            this.freqMax = max;

            if (verticalScale == PrefsUtil.VSCALE_FREQ_SPECT) triggerRedisplay();
        }
    }

    public void update(Span s) {
        viewSpan = s;
        triggerRedisplay();
    }

    public int getNumChannels()
    {
        return fullChannels;
    }

    /**
     *  Synchronization:    this uses and alters one internal rectangle object,
     *                      be sure to not use this rectangle outside the swing thread,
     *                      otherwise make a copy. do not modify the returned rectangle
     */
    public Rectangle rectForChannel(int ch) {
        final int ht    = getHeight() - (insets.top + insets.bottom);
        final int temp  = ht * ch / fullChannels;
        final int y     = insets.top + temp;
        final int h     = (ht * (ch + 1) / fullChannels) - temp - vGap;

        r.setBounds(insets.left, y, getWidth() - (insets.left + insets.right), h);

        return r;
    }

    public int channelForPoint(Point p) {
        final int py = p.y - insets.top;
        final int ht = getHeight();
        int y1 = 0;
        int y2;

        for( int ch = 0; ch < fullChannels; ch++ ) {
            y2 = ht * (ch + 1) / fullChannels;
            if( (py >= y1) && (py < (y2 - vGap)) ) return ch;
            y1 = y2;
        }
        return -1;
    }

    public DecimationInfo getDecimationInfo() { return info; }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (viewSpan.isEmpty()) return;
        final Graphics2D g2 = (Graphics2D) g;

        switch (verticalScale) {
            case PrefsUtil.VSCALE_AMP_LIN:
                paintAmpLin(g2);
                break;
            case PrefsUtil.VSCALE_AMP_LOG:
                paintAmpLog(g2);
                break;
            case PrefsUtil.VSCALE_FREQ_SPECT:
                paintFreqSpect(g2);
                break;
            default:
                assert false : verticalScale;
        }
    }

    private void paintAmpLin(Graphics2D g2) {
        final DecimatedWaveTrail dt = doc.getDecimatedWaveTrail();
        if (dt == null) return;

        final int w = getWidth();
        Rectangle cr;
        int y;

        info = dt.getBestSubsample(new Span(viewSpan.start, viewSpan.stop + 1), w);
        dt.drawWaveform(info, this, g2);

        if (nullLine) {
            g2.setPaint(pntNull);
            g2.setStroke(strkNull);
            for (int ch = 0; ch < fullChannels; ch++) {
                cr = rectForChannel(ch);
                y = cr.y + (cr.height >> 1);
                g2.drawLine(cr.x, y, cr.x + cr.width, y);
            }
        }
    }

    private void paintAmpLog(Graphics2D g2) {

        final DecimatedWaveTrail dt = doc.getDecimatedWaveTrail();
        if (dt == null) return;

        final int w = getWidth();
        Rectangle cr;
        int y;

        info = dt.getBestSubsample( new Span( viewSpan.start, viewSpan.stop + 1 ), w );
        dt.drawWaveform( info, this, g2 );

        if (nullLine) {
            g2.setPaint(pntNull);
            g2.setStroke(strkNull);
            for (int ch = 0; ch < fullChannels; ch++) {
                cr = rectForChannel(ch);
                y = cr.y + cr.height - 1;
                g2.drawLine(cr.x, y, cr.x + cr.width, y);
            }
        }
    }

    private void paintFreqSpect(Graphics2D g2) {
        final DecimatedSonaTrail dt = doc.getDecimatedSonaTrail();
        if (dt == null) return;

        final int w = getWidth();

        info = dt.getBestSubsample(new Span(viewSpan.start, viewSpan.stop + 1), w);
        dt.drawWaveform(info, this, g2);
    }

    private void triggerRedisplay() {
        if (host != null) {
            host.update(this);
        } else if (isVisible()) {
            repaint();
        }
    }
  
    // -------------- Disposable interface --------------

    public void dispose() { /* empty */ }
}
