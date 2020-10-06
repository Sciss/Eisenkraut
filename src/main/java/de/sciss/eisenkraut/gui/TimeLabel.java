/*
 *  TimeLabel.java
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

import de.sciss.gui.TimeFormat;
import de.sciss.util.Disposable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 *  A GUI component showing a time position.
 */
@SuppressWarnings("serial")
public class TimeLabel
        extends JPanel
        implements Disposable {

    private final TimeFormat format;
    protected String text;

    protected static final Font fntMono;
    protected boolean dimsKnown = false;
    protected int textWidth, textHeight, textAscent;

    private static final Color colrTimeLight    = new Color(0xF1, 0xFA, 0xCA);
    private static final Color colrTimeDark     = new Color(16, 16, 16);
    private static final Color colrFgLight      = Color.black;
    private static final Color colrFgDark       = new Color(220, 220, 200);
    private static final Color colrHoverLight   = Color.blue;
    private static final Color colrHoverDark    = new Color(0x5E, 0x97, 0xFF);

    private final Label lb;

    static {
        final String[] fntNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        if (contains(fntNames, "DejaVu Sans Mono")) {
            // Linux
            fntMono = new Font("DejaVu Sans Mono", Font.PLAIN, 12);
        } else if (contains(fntNames, "Monaco")) {                        // Mac OS
            fntMono = new Font("Monaco", Font.PLAIN, 11);               // looks bigger than "normal monospaced"
        } else if (contains(fntNames, "Lucida Sans Unicode")) {            // Windows XP
            fntMono = new Font("Lucida Sans Unicode", Font.PLAIN, 12);
        } else {
            fntMono = new Font("Monospaced", Font.PLAIN, 12);
        }
    }

    private final boolean isDark;

    public TimeLabel() {
        this(null);
    }

    public TimeLabel(Color background) {
        super(new BorderLayout());

        isDark = GraphicsUtil.isDarkSkin();
        if (background == null) background = isDark ? colrTimeDark : colrTimeLight;
        setBorder(new RoundedBorder(background));

        format  = new TimeFormat(0, null, null, 3, Locale.US);
        lb      = new Label();
        add(lb, BorderLayout.CENTER);
        lb.setOpaque(true);
        lb.setBackground(background);
        normalState();
        // lb.setFont(fntMono);

        final Dimension d = new Dimension(106, 22);    // XXX
        setMinimumSize(d);
        setMaximumSize(d);
        setPreferredSize(d);

        text = format.formatTime(0);
    }

//    public Color getLabelBackground() { return lb.getBackground(); }

    private static boolean contains(String[] array, String name) {
        for (String anArray : array) {
            if (anArray.equals(name)) return true;
        }
        return false;
    }

    public void hoverState() {
        lb.setForeground(isDark ? colrHoverDark : colrHoverLight);
    }

    public void normalState() {
        lb.setForeground(isDark ? colrFgDark : colrFgLight);
    }

    public void setTime(Number seconds) {
        text = format.formatTime(seconds);
        lb.repaint();
    }

    public void dispose()
    {
        lb.dispose();
    }

    @SuppressWarnings("serial")
    private class Label
            extends JComponent
            implements Disposable {

        private Image img;

        protected Label() { /* empty */ }

        public void dispose() {
            if (img != null) {
                img.flush();
                img = null;
            }
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            final Graphics2D g2 = (Graphics2D) g;
            g2.setFont(fntMono);
            final FontMetrics fm = g2.getFontMetrics();

            if (!dimsKnown) {
                textWidth = fm.stringWidth("00:00:00.000");
                textAscent = fm.getAscent() - 1;
                textHeight = fm.getHeight(); // textAscent + fm.getDescent();
                dimsKnown = true;
                final Dimension d = new Dimension(textWidth, textHeight);
                setPreferredSize(d);
                setMinimumSize(d);
                setMaximumSize(d);
            }

            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(getForeground());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.drawString(text, (getWidth() - fm.stringWidth(text)) >> 1, ((getHeight() - textHeight) >> 1) + textAscent);
        }
    }
}

