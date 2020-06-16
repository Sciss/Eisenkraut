/*
 *  BlendingAction.java
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

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.eisenkraut.io.BlendContext;
import de.sciss.eisenkraut.timeline.Timeline;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.DefaultUnitViewFactory;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpButton;
import de.sciss.gui.ParamField;
import de.sciss.gui.PrefParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.net.MalformedURLException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

//import java.util.ArrayList;
//import java.util.List;
//import javax.swing.event.ListDataEvent;
//import javax.swing.event.ListDataListener;

/**
 *  A class implementing the <code>Action</code> interface
 *  which deals with the blending setting. Each instance
 *  generates a toggle button suitable for attaching to a tool bar;
 *  this button reflects the blending preferences settings and
 *  when alt+pressed will prompt the user to alter the blending settings.
 *
 *  TODO: gui panels should be destroyed / disposed
 *  because otherwise different BlendingAction instances
 *  for different docs all create their own panels
 *
 *  TODO: should not be a subclass of AbstractAction
 *
 *  TODO: close action should be called whenever popup disappears,
 *  and duplicates should be filtered out!
 */
@SuppressWarnings("serial")
public class BlendingAction
        extends AbstractAction {

    public static final int                     MAX_RECENTNUM   = 5;

    public static final String                  DEFAULT_NODE    = "blending";

    private static final String                 KEY_ACTIVE      = "active";
    public static final String                  KEY_DURATION    = "duration";
    private static final String                 NODE_RECENT     = "recent";

    private static final Stroke strkActive = new BasicStroke(1.5f);

    protected static final Param    DEFAULT_DUR =
            new Param(100.0, ParamSpace.TIME | ParamSpace.MILLI | ParamSpace.SECS);

    private static PrefComboBoxModel            pcbm    = null;

    protected final AbstractButton              b;
    protected final Preferences                 prefs;
    protected PrefParamField                    ggBlendTime;
    protected CurvePanel                        ggCurvePanel;
    private SpringPanel                         ggSettingsPane;
    private JComponent                          bottomPanel;

    private static final DefaultUnitTranslator ut = new DefaultUnitTranslator();
    protected static final DefaultUnitViewFactory uvf = new DefaultUnitViewFactory();

    private final CurvePanel.Icon               curveIcon;

    private JPopupMenu                          popup       = null;
    private AppWindow                           palette     = null;

    private boolean                             active;

    private final Timeline                      timeline;

    protected final Settings                    current;

    private final Color colrFgSel, colrFgNorm;

    /**
     *  Creates a new instance of an action
     *  that tracks blending changes
     *
     *  @param  prefs   node of preferences tree (usually DEFAULT_NODE)
     */
    public BlendingAction(Timeline timeline, Preferences prefs) {
        super();

        this.prefs      = prefs != null ? prefs : AbstractApplication.getApplication().getUserPrefs().node(DEFAULT_NODE);
        this.timeline   = timeline;

        final boolean isDark = GraphicsUtil.isDarkSkin();

        colrFgSel = isDark ? new Color(95, 142, 255) /* new Color(136, 136, 255) */ /* new Color(48, 77, 130) */ :
                Color.blue; // new Color(48, 77, 130);

        // b = new MultiStateButton();
        b = new JButton("xxxxxxxx");
        colrFgNorm = b.getForeground();
        // b.setBorderPainted(false);
        b.putClientProperty("styleId", "icon-hover");
        b.setBorder(null);
        b.setPreferredSize(b.getPreferredSize());
        b.setText(null);
        // b.setNumColumns(8);
        // b.setAutoStep(false);
        b.addActionListener(this);
        // final Color colrFg = isDark ? new Color(200, 200, 200) : Color.black;
        // b.addItem("", colrFg, new Color(0, 0, 0, 0));
        // b.addItem("", colrFg, isDark ? new Color(0x7F, 0x7F, 0xFF, 0x7F) /* new Color(48, 77, 130) */ : new Color(0xFF, 0xFA, 0x9D));
        curveIcon = new CurvePanel.Icon(createBasicCurves());
        b.setIcon(curveIcon);
        // b.setItemIcon(0, curveIcon);
        // b.setItemIcon(1, curveIcon);

        final PopupTriggerMonitor popMon = new PopupTriggerMonitor(b);
        popMon.addListener(new PopupTriggerMonitor.Listener() {
            public void componentClicked(PopupTriggerMonitor m) { /* empty */ }

            public void popupTriggered(PopupTriggerMonitor m) {
                b.getModel().setArmed(false);
                showPopup(m.getComponent(), 0, m.getComponent().getHeight());
            }
        });

        active  = this.prefs.getBoolean(KEY_ACTIVE, false);
        current = Settings.fromPrefs(this.prefs);
        updateButton();
    }

    public Preferences getPreferences() { return prefs; }
    private Preferences getRecentPreferences() { return prefs.node( NODE_RECENT ); }

    private void createBlendPan(boolean popped) {
        destroyBlendPan();

        createGadgets(0);
        ggBlendTime.setCycling(popped); // cannot open popup menu in another popup menu!
        if (palette != null) {
            palette.getContentPane().add(ggSettingsPane, BorderLayout.CENTER);
            palette.getContentPane().add(bottomPanel, BorderLayout.SOUTH);
            palette.revalidate();
        } else {
            popup.add(ggSettingsPane, BorderLayout.CENTER);
            popup.add(bottomPanel, BorderLayout.SOUTH);
            popup.revalidate();
        }
    }

    private void destroyBlendPan() {
        if (ggSettingsPane != null) {
            ggSettingsPane.getParent().remove(ggSettingsPane);
            ggSettingsPane = null;
        }
        if (bottomPanel != null) {
            bottomPanel.getParent().remove(bottomPanel);
            bottomPanel = null;
        }
    }

    private void createGadgets(int flags) {
        bottomPanel = createBottomPanel(flags);
        ggSettingsPane = new SpringPanel(4, 2, 4, 2);
        ut.setLengthAndRate(0, timeline.getRate());
        ggBlendTime = new PrefParamField(ut);
        ggBlendTime.addSpace(ParamSpace.spcTimeMillis);
        ggBlendTime.addSpace(ParamSpace.spcTimeSmps);
        ggBlendTime.setPreferences(prefs, KEY_DURATION);
        ggBlendTime.setReadPrefs(false);
        if (current.duration != null) {
            ggBlendTime.setValueAndSpace(current.duration);
        }
        ggBlendTime.addListener(new ParamField.Listener() {
            public void paramSpaceChanged(ParamField.Event e) {
                paramValueChanged(e);
            }

            public void paramValueChanged(ParamField.Event e) {
                if (!e.isAdjusting()) {
                    current.duration = ggBlendTime.getValue();
                    updateButtonText();
                }
            }
        });

        ggCurvePanel = new CurvePanel(createBasicCurves(), this.prefs);
        ggCurvePanel.setControlPoints(current.ctrlPt[0], current.ctrlPt[1]);
        ggCurvePanel.setPreferredSize(new Dimension(162, 162));
        ggCurvePanel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final Point2D[] pt = ggCurvePanel.getControlPoints();
                current.ctrlPt[0].setLocation(pt[0]);
                current.ctrlPt[1].setLocation(pt[1]);
                updateButtonIcon();
            }
        });

        ggSettingsPane.gridAdd(ggBlendTime, 0, 0);
        ggSettingsPane.gridAdd(ggCurvePanel, 0, 1);
        ggSettingsPane.makeCompactGrid();
    }

    public void showPopup(Component invoker, int x, int y) {
        createPopup();
        popup.show(invoker, x, y);
        // XXX this is necessary unfortunately
        // coz the DynamicAncestorAdapter doesn't seem
        // to work with the popup menu ...
        ggBlendTime.startListening();
    }

    private void createPopup() {
        if (popup != null) return;
        if (palette != null) destroyPalette();

        popup = new JPopupMenu();
        createBlendPan(true);
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent e) {
                stopAndDispose();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { /* empty */ }

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) { /* empty */ }
        });
    }

    protected void stopAndDispose() {
        // XXX this is necessary unfortunately
        // coz the DynamicAncestorAdapter doesn't seem
        // to work with the popup menu ...
        ggBlendTime.stopListening();
        dispose();
    }

    public void showPalette() {
        createPalette();
        palette.setVisible(true);
        palette.toFront();
    }

    private void destroyPalette() {
        if (palette == null) return;

        palette.dispose();
        palette = null;
    }

    private void destroyPopup() {
        if (popup == null) return;

        popup.setVisible(false);
        popup = null;
    }

    protected static CubicCurve2D[] createBasicCurves() {
        return new CubicCurve2D[]{
                new CubicCurve2D.Double(0.0, 1.0, 0.5, 0.0, 0.5, 0.0, 1.0, 0.0),
                new CubicCurve2D.Double(0.0, 0.0, 0.5, 0.0, 0.5, 0.0, 1.0, 1.0)
        };
    }

    /**
     *  Returns the toggle button
     *  which is connected to this action.
     *
     *  @return a toggle button which is suitable for tool bar display
     */
    public AbstractButton getButton()
    {
        return b;
    }

    public JComboBox mkComboBox() {
        final ComboBoxModel     model           = getComboBoxModel();
        final ComboBoxModel     emptyCBM        = new DefaultComboBoxModel();
        final AbstractButton    button          = b;
        final JComboBox         ggBlend         = new JComboBox(); // ( pcbm );
        final ListCellRenderer  blendRenderer   = mkComboBoxRenderer(ggBlend.getRenderer());

        ggBlend.setEditable(true);
        ggBlend.setEditor(new ComboBoxEditor() {
            public Component getEditorComponent() {
                return button;
            }

            public void setItem(Object o) {
                if (o != null) {
                    current.setFrom((Settings) o);
                    current.toPrefs(prefs);
                    updateButton();
                }
            }

            public Object getItem() { return current; }
            public void selectAll() { /* ignore */ }
            public void addActionListener(ActionListener l) { /* ignore */ }
            public void removeActionListener(ActionListener l) { /* ignore */ }
        });
        ggBlend.setRenderer(blendRenderer);
        button.setFocusable(false);
        ggBlend.setFocusable(false);
        GUIUtil.constrainSize(ggBlend, 120, 26);    // 110 XXX (140 for MetalLAF)
        ggBlend.setOpaque(false);

        // this is _crucial_ because since pcbm is global
        // and the combo-box registers with it, we have a
        // memory leak otherwise!!
        new DynamicAncestorAdapter(new DynamicListening() {
            public void startListening() {
                ggBlend.setModel(model);
            }

            public void stopListening() {
                ggBlend.setModel(emptyCBM);
            }
        }).addTo(ggBlend);

        return ggBlend;
    }

    private void updateButtonState() {
        // System.out.println("updateButtonState() " + active);
        // b.setSelected(active);
        // b.getModel().setArmed(active);
        // b.getModel().setPressed(active);
        b.setForeground(active ? colrFgSel : colrFgNorm);
        // b.setSelectedIndex(active ? 1 : 0);
    }

    protected void updateButton() {
        updateButtonState();
        updateButtonText();
        updateButtonIcon();
    }

    protected void updateButtonText() {
        final Param p = current.duration;
        final Object view;
        final String text;

        if (p != null) {
            view = uvf.createView(p.unit);

            if (view instanceof Icon) {
                // XXX hmmm. should use composite icon
            } else {
                text = String.valueOf((int) p.val) + " " + view.toString();
                b.setText(text);
                // b.setItemText(0, text);
                // b.setItemText(1, text);
            }
            if (ggBlendTime != null) ggBlendTime.setValueAndSpace(p);
        }
    }

    protected void updateButtonIcon() {
        curveIcon.update(current.ctrlPt[0], current.ctrlPt[1]);
        curveIcon.setStroke(active ? strkActive : null);
        if (ggCurvePanel != null) ggCurvePanel.setControlPoints(current.ctrlPt[0], current.ctrlPt[1]);
        b.repaint();
    }

    private JComponent createBottomPanel(int flags) {
        final JPanel    panel;
        final JButton   ggClose;

        panel   = new JPanel( new FlowLayout( FlowLayout.TRAILING, 4, 2 ));
        ggClose = new JButton( new CloseAction( getResourceString( "buttonClose" )));
        GUIUtil.createKeyAction( ggClose, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ));
        ggClose.setFocusable( false );
        panel.add(ggClose);
        try {
            panel.add(new HelpButton(MenuFactory.helpFile("Blending")));
        } catch (MalformedURLException e) {
            // nothing
        }
        panel.add(CoverGrowBox.create());

        return panel;
    }

    private String getResourceString(String key) {
        return AbstractApplication.getApplication().getResourceString( key );
    }

    public void actionPerformed(ActionEvent e) {
        if ((e.getModifiers() & ActionEvent.ALT_MASK) != 0) {
            showPalette();
        } else {
            active = !active; // b.isSelected(); // b.getSelectedIndex() == 0;
            prefs.putBoolean(KEY_ACTIVE, active);
            updateButtonState();
            updateButtonIcon();
        }
    }

    private void createPalette() {
        if (palette != null) return;
        if (popup != null) destroyPopup();

        final Application app = AbstractApplication.getApplication();

        palette = new AppWindow( AbstractWindow.PALETTE );
        palette.setTitle( app.getResourceString( "inputDlgSetBlendSpan" ));
        createBlendPan( false );
        palette.getContentPane().add( CoverGrowBox.create(), BorderLayout.SOUTH );
        palette.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
        palette.addListener( new AbstractWindow.Adapter() {
            public void windowClosing( AbstractWindow.Event e )
            {
                dispose();
            }
        });
        palette.init();
    }

    public void dispose() {
        destroyPalette();
        destroyPopup();
        destroyBlendPan();
    }

    private ComboBoxModel getComboBoxModel() {
        if (pcbm == null) {
            pcbm = new PrefComboBoxModel() {
                public Object dataFromNode(Preferences node) {
                    return Settings.fromPrefs(node);
                }

                public void dataToNode(Object data, Preferences node) {
                    ((Settings) data).toPrefs(node);
                }
            };
            pcbm.setPreferences( getRecentPreferences() );
        }
        return pcbm;
    }

    private ListCellRenderer mkComboBoxRenderer(ListCellRenderer peer) {
        return new BlendCBRenderer(peer);
    }

    protected void storeRecent() {
        if (pcbm == null) return;

        pcbm.setSelectedItem(null);

        while (pcbm.getSize() >= MAX_RECENTNUM) {
            try {
                pcbm.remove(pcbm.getSize() - 1);
            } catch (BackingStoreException e1) {
                e1.printStackTrace();
                return;
            }
        }
        pcbm.add(0, current.duplicate());
    }

    /**
     *  TODO: pre/post position not yet effective (using 0.5 right now)
     */
    public BlendContext createBlendContext(long maxLeft, long maxRight) {
        if (!java.awt.EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();
        if (!active) return null;

        final long  blendLen;
        final Param p;

        p = current.duration; // Param.fromPrefs( prefs, KEY_DURATION, null );
        if (p != null) {
            ut.setLengthAndRate(0, timeline.getRate());
            blendLen = (long) (ut.translate(p, ParamSpace.spcTimeSmps).val + 0.5);
            if (maxLeft + maxRight > blendLen) {
                maxLeft = (long) (maxLeft * (double) blendLen / (maxLeft + maxRight) + 0.5);
                maxRight = blendLen - maxLeft;
            }
        }
        return new BlendContext(maxLeft, maxRight, CurvePanel.getControlPoints(prefs));
    }

    private static class Settings {

        protected Param duration;
        protected final Point2D[] ctrlPt = new Point2D[]{new Point2D.Double(), new Point2D.Double()};

        private Settings() { /* empty */ }

        private Settings(Settings orig) {
            setFrom(orig);
        }

        protected void setFrom(Settings orig) {
            duration = orig.duration;
            ctrlPt[0].setLocation(orig.ctrlPt[0]);
            ctrlPt[1].setLocation(orig.ctrlPt[1]);
        }

        protected static Settings fromPrefs(Preferences node) {
            final Settings s = new Settings();
            s.duration = Param.fromPrefs(node, KEY_DURATION, DEFAULT_DUR);
            final Point2D[] pt = CurvePanel.getControlPoints(node);
            s.ctrlPt[0].setLocation(pt[0]);
            s.ctrlPt[1].setLocation(pt[1]);
            return s;
        }

        protected Settings duplicate() {
            return new Settings(this);
        }

        protected void toPrefs(Preferences node) {
            node.put(KEY_DURATION, duration.toString());
            CurvePanel.toPrefs(ctrlPt, node);
        }

        public String toString() {
            return String.valueOf(duration.val);
        }
    }

    @SuppressWarnings("serial")
    private static class BlendCBRenderer
            extends JLabel
            implements ListCellRenderer {

        final CurvePanel.Icon curveIcon;

        private final ListCellRenderer peer;

        protected BlendCBRenderer(ListCellRenderer peer) {
            super();
            this.peer = peer;
            curveIcon = new CurvePanel.Icon(createBasicCurves());
            setIcon(curveIcon);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        }

        public Component getListCellRendererComponent(
                JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            final Settings s = (Settings) value;
            final Object view = uvf.createView(s.duration.unit);

            final String txt = String.valueOf((int) s.duration.val) + " " + view.toString();
            final Component res = peer.getListCellRendererComponent(list, txt /* value */, index, isSelected, cellHasFocus);

            curveIcon.update(s.ctrlPt[0], s.ctrlPt[1]);
            if (res instanceof JLabel) {
                ((JLabel) res).setIcon(curveIcon);
            }

            return res;
        }
    }

    private class CloseAction
            extends AbstractAction {

        protected CloseAction(String text) {
            super(text);
        }

        public void actionPerformed(ActionEvent e) {
            stopAndDispose();
            storeRecent();
        }
    }
}
