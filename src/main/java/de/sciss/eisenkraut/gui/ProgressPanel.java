/*
 *  ProgressPanel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import java.awt.Component;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.ProgressBar;
import de.sciss.gui.ProgressComponent;

@SuppressWarnings("serial")
public class ProgressPanel
        extends JPanel
        implements ProgressComponent {

    private final ProgressBar pb;
    private final JLabel lb;
    private final ModificationButton ggCancel;

    public ProgressPanel() {
        super();

        pb = new ProgressBar();
        lb = new JLabel("", SwingConstants.RIGHT);
        lb.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4)); // T L B R
        ggCancel = new ModificationButton(ModificationButton.SHAPE_ABORT);
        ggCancel.setEnabled(false);

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(lb);
        add(pb);
        add(ggCancel);
    }

// ---------------- ProgressComponent interface ---------------- 

    public void addCancelListener(ActionListener l) {
        ggCancel.addActionListener(l);
    }

    public void removeCancelListener(ActionListener l) {
        ggCancel.removeActionListener(l);
    }

    public Component getComponent() {
        return this;
    }

    public void resetProgression() {
        pb.reset();
        ggCancel.setEnabled(true);
    }

    public void setProgression(float p) {
        if (p >= 0) {
            pb.setProgression(p);
        } else {
            pb.setIndeterminate(true);
        }
    }

    public void finishProgression(int result) {
        pb.finish(result);
        ggCancel.setEnabled(false);
    }

    public void setProgressionText(String text) {
        lb.setText(text);
    }

    public void displayError(Exception e, String processName) {
        BasicWindowHandler.showErrorDialog(this, e, processName);
    }

    public void showMessage(int type, String text) {
        System.out.println(text);
    }
}