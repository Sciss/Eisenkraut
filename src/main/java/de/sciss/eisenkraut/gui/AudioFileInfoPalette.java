/*
 *  AudioFileInfoPalette.java
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

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.AncestorEvent;

import de.sciss.app.AncestorAdapter;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.edit.EditChangeAudioFileDescr;
import de.sciss.eisenkraut.edit.EditPutMapValue;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.timeline.Timeline;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicListening;
import de.sciss.app.GraphicsHandler;
import de.sciss.common.AppWindow;
import de.sciss.gui.LooseFocusAction;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.ParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.io.AudioFileDescr;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  Allows header editing (only sample rate for now).
 */
public class AudioFileInfoPalette
        extends AppWindow
        implements DynamicListening, de.sciss.app.DocumentListener, ParamField.Listener,
        TimelineListener, PropertyChangeListener {

    protected Session           doc;
    private AudioFileDescr      afd;
    private final JLabel        lbName;
    private final ParamField    ggRate;
    protected final JTextArea   ggComment;
    private final SpringPanel   p;

    private void checkCommentEdited() {
        if (doc != null) {
            final AudioFileDescr displayAFD = doc.getDisplayDescr();
            final String newText = ggComment.getText();
            final String oldText = (String) displayAFD.getProperty(AudioFileDescr.KEY_COMMENT);

            if (!newText.equals(oldText)) {
                doc.getUndoManager().addEdit(new EditChangeAudioFileDescr(this, displayAFD,
                        AudioFileDescr.KEY_COMMENT, newText, getResourceString("editChangeComment")).perform());
            }
        }
    }

    /**
     *  Constructs a new <code>AudioFileInfoPalette</code>
     */
    public AudioFileInfoPalette() {
        super(PALETTE);

        final Application app = AbstractApplication.getApplication();

        setTitle(app.getResourceString("paletteAudioInfo"));

        final Container     cp = getContentPane();
        final JScrollPane   ggScroll;
        final JPanel        p2 = new JPanel(new BorderLayout());
        final SpringPanel   p3 = new SpringPanel(4, 2, 4, 2);
        JLabel lb;

        p = new SpringPanel(4, 2, 4, 2);

        p3.gridAdd(new JLabel(getResourceString("labelName")), 0, 0);
        lbName = new JLabel();
        p3.gridAdd(lbName, 1, 0);

        p3.gridAdd(new JLabel(getResourceString("labelSampleRate")), 0, 1);
        ggRate = new ParamField();
        ggRate.addSpace(ParamSpace.spcFreqHertz.reshape(0.01, Double.POSITIVE_INFINITY, 44100.0));
        ggRate.addListener(this);
        p3.gridAdd(ggRate, 1, 1);

        ggComment = new JTextArea(4, 24);
        new LooseFocusAction(ggComment);
        ggComment.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                checkCommentEdited();
            }
        });
        ggComment.addAncestorListener(new AncestorAdapter() {
            public void ancestorRemoved(AncestorEvent e) {
                checkCommentEdited();
            }
        });
        ggScroll = new JScrollPane(ggComment, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        lb = new JLabel(getResourceString("labelComment"));
        lb.setVerticalAlignment(SwingConstants.TOP);
        p.gridAdd(lb, 0, 2);
        ggComment.setLineWrap(true);
        ggComment.setWrapStyleWord(true);
        p.gridAdd(ggScroll, 1, 2, 1, 2);

        p.makeCompactGrid();
        p3.makeCompactGrid();
        p2.add(p3, BorderLayout.NORTH);
        p2.add(p, BorderLayout.CENTER);

        cp.add(p2, BorderLayout.CENTER);

        // --- Listener ---
        addDynamicListening(this);

        app.getDocumentHandler().addDocumentListener(this);

        addListener(new AbstractWindow.Adapter() {
            public void windowClosing(AbstractWindow.Event e) {
                dispose();
            }
        });
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); // window listener see above!

        init();
        app.addComponent(Main.COMP_AUDIOINFO, this);
    }

    protected boolean autoUpdatePrefs() {
        return true;
    }

    public void dispose() {
        AbstractApplication.getApplication().removeComponent(Main.COMP_AUDIOINFO);
        super.dispose();
    }

    public void updateDocumentName(Session aDoc) {
        if (isVisible() && (doc == aDoc)) {
            setDocumentName();
        }
    }

    private void setDocumentName() {
        lbName.setText(doc.getName() == null ? getResourceString("frameUntitled") : doc.getName());
    }

    private void updateSchnucki(Session newDoc) {
        final String comment;

        if (afd != null) {
            afd.removePropertyChangeListener(this);
        }
        if (doc != null) {
            doc.timeline.removeTimelineListener(this);
        }

        doc = newDoc;
        afd = newDoc == null ? null : newDoc.getDisplayDescr();

        if (doc == null) {
            ggRate.setEnabled(false);
            ggRate.setValue(new Param(Double.NaN, ParamSpace.FREQ | ParamSpace.HERTZ));
            lbName.setText("");
            ggComment.setEnabled(false);
            ggComment.setText("");
        } else {
            comment = (String) afd.getProperty(AudioFileDescr.KEY_COMMENT);
            ggRate.setEnabled(true);
            ggRate.setValue(new Param(doc.timeline.getRate(), ParamSpace.FREQ | ParamSpace.HERTZ));
            setDocumentName();
            ggComment.setEnabled(true);
            ggComment.setText(comment);    // null allowed
            p.makeCompactGrid();
            pack();
        }

        if (afd != null) {
            afd.addPropertyChangeListener(this);
        }
        if (doc != null) {
            doc.timeline.addTimelineListener(this);
        }
    }

    protected String getResourceString(String key) {
        return AbstractApplication.getApplication().getResourceString(key);
    }

// ---------------- DynamicListening interface ---------------- 

    public void startListening() {
        final de.sciss.app.DocumentHandler dh = AbstractApplication.getApplication().getDocumentHandler();
        updateSchnucki((Session) dh.getActiveDocument());
        dh.addDocumentListener(this);
    }

    public void stopListening() {
        final de.sciss.app.DocumentHandler dh = AbstractApplication.getApplication().getDocumentHandler();
        dh.removeDocumentListener(this);
        if (afd != null) {
            afd.removePropertyChangeListener(this);
            afd = null;
        }
        if (doc != null) {
            doc.timeline.removeTimelineListener(this);
            doc = null;
        }
    }

// ---------------- PropertyChangeListener interface ---------------- 

    // fired from AudioFileDescr
    public void propertyChange(PropertyChangeEvent e) {
        if ((e.getSource() != this) && (e.getPropertyName().equals(AudioFileDescr.KEY_COMMENT))) {
            ggComment.setText((String) e.getNewValue());
        }
    }

// ---------------- TimelineListener interface ---------------- 

    public void timelineChanged(TimelineEvent e) {
        if ((e.getSource() != this) && (doc != null)) {
            ggRate.setValue(new Param(doc.timeline.getRate(), ParamSpace.FREQ | ParamSpace.HERTZ));
        }
    }

    public void timelineSelected    (TimelineEvent e) { /* empty */ }

    public void timelinePositioned  (TimelineEvent e) { /* empty */ }

    public void timelineScrolled    (TimelineEvent e) { /* empty */ }

// ---------------- NumberListener interface ---------------- 

    public void paramValueChanged(ParamField.Event e) {
        if ((doc == null) || e.isAdjusting()) return;

        doc.getUndoManager().addEdit(new EditPutMapValue(this,
                doc.timeline.getMap(), Timeline.MAP_KEY_RATE, new Float(ggRate.getValue().val),
                getResourceString("editSampleRate")).perform());
    }

    public void paramSpaceChanged(ParamField.Event e) { /* empty */ }

// ---------------- DocumentListener interface ---------------- 

    public void documentFocussed(de.sciss.app.DocumentEvent e) {
        updateSchnucki((Session) e.getDocument());
    }

    public void documentAdded   (de.sciss.app.DocumentEvent e) { /* empty */ }

    public void documentRemoved (de.sciss.app.DocumentEvent e) { /* empty */ }
}