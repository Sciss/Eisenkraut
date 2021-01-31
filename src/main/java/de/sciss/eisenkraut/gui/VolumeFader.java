/*
 *  VolumeFader.java
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

import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Dictionary;
import java.util.Enumeration;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.sciss.app.AbstractApplication;
import de.sciss.app.GraphicsHandler;
import de.sciss.eisenkraut.math.MathUtil;

public class VolumeFader
        extends JSlider {

    protected boolean isZero = true;

    public VolumeFader() {
        super(SwingConstants.VERTICAL, -72, 18, 0);

        final GraphicsHandler gh;
        final Dictionary<?, ?> dictVolume;
        final Font fnt;
        final JLabel lbZero;
        JLabel lb, lbZeroTmp = null;

        putClientProperty("JSlider.isFilled", Boolean.TRUE);    // used by Metal-lnf
        dictVolume = createStandardLabels(12);
        setMinorTickSpacing(3);
        setMajorTickSpacing(12);
        gh = AbstractApplication.getApplication().getGraphicsHandler();
        fnt = gh.getFont(GraphicsHandler.FONT_LABEL | GraphicsHandler.FONT_MINI);
        for (Enumeration<?> en = dictVolume.elements(); en.hasMoreElements(); ) {
            lb = (JLabel) en.nextElement();
            if (lb.getText().equals("-72")) lb.setText("-\u221E");
            if (lb.getText().equals("0")) {
                lbZeroTmp = lb;
                lb.setText("0\u25C0");
            }
            lb.setFont(fnt);
        }
        lbZero = lbZeroTmp;
        setLabelTable(dictVolume);
        setPaintTicks(true);
        setPaintLabels(true);
        setValue(0);

        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.isAltDown()) resetVolume();
            }
        });
        if (lbZero != null) addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (isZero) {
                    if (getValue() != 0) {
                        isZero = false;
                        lbZero.setText("0");
                        repaint();
                    }
                } else {
                    if (getValue() == 0) {
                        isZero = true;
                        lbZero.setText("0\u25C0");
                        repaint();
                    }
                }
            }
        });
    }

    public void resetVolume() {
        setValue(0);
    }

    public float getVolumeDecibels() {
        final int db = getValue();
        return db == -72 ? Float.NEGATIVE_INFINITY : db;
    }

    public float getVolumeLinear() {
        final int db = getValue();
        return db == -72 ? 0f : (float) MathUtil.dBToLinear(db);
    }

    public void setVolumeDecibels(float db) {
        setValue(Math.max(-72, (int) (db + 0.5f)));
    }

    public void setVolumeLinear(float linear) {
        final int db = linear == 0f ? -72 : Math.max(-72, Math.min(18, (int) (MathUtil.linearToDB(linear) + 0.5)));
        setValue(db);
    }
}