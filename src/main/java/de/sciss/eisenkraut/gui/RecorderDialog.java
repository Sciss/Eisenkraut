/*
 *  RecorderDialog.java
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

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.net.SuperColliderPlayer;
import de.sciss.eisenkraut.session.DocumentFrame;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.DoClickAction;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpButton;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StringItem;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.jcollider.Buffer;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Constants;
import de.sciss.jcollider.Control;
import de.sciss.jcollider.GraphElem;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.NodeEvent;
import de.sciss.jcollider.NodeListener;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;
import de.sciss.jcollider.Synth;
import de.sciss.jcollider.SynthDef;
import de.sciss.jcollider.UGen;
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCMessage;
import de.sciss.util.MutableLong;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

public class RecorderDialog
        extends JDialog
        implements Constants, ServerListener, NodeListener, OSCRouter,
        PreferenceChangeListener {

    private static final int DISK_BUF_SIZE = 32768;    // buffer size in frames
    private static final String NODE_CONF = PrefsUtil.NODE_INPUT_CONFIGS;

    private static final String KEY_CONFIG = "config";

    private final Preferences audioPrefs;
    private final Preferences classPrefs;
    private final PrefComboBox ggRecordConfig;

    private RoutingConfig rCfg;
    protected final Server server;
    protected final SuperColliderPlayer player;

    protected final ActionRecord    actionRecord;
    private final   ActionStop      actionStop;
    private final   ActionAbort     actionAbort;
    private final   ActionClose     actionClose;

    protected Context ct = null;

    private final javax.swing.Timer meterTimer;

    protected NodeWatcher nw;

    protected final TimeoutTimer timeoutTimer = new TimeoutTimer(4000);

    private final RecLenTimer recLenTimer;

    protected boolean isRecording = false;

    protected final DocumentFrame docFrame;
    private final int numChannels;
    protected final String encodingString;

    private File result = null;
    private boolean stopCommit = false;    // true to close dlg after stop

    private final SuperColliderClient superCollider;

    protected boolean clipped = false;
    protected final JLabel lbPeak;
    private final Color colrPeakNorm;

    private static final String OSC_RECORDER = "recorder";
    private final OSCRouterWrapper osc;

    private final MutableLong recFrames = new MutableLong(0);
    private final JToggleButton ggMonitoring;
    private final ActionPeakReset actionPeakReset;

    /**
     *  @throws IOException if the server isn't running or no valid input routing config exists
     */
    public RecorderDialog(Session doc)
            throws IOException {
        super(((doc.getFrame() != null) && (doc.getFrame().getWindow() instanceof Frame)) ? (Frame) doc.getFrame().getWindow() : null,
                AbstractApplication.getApplication().getResourceString("dlgRecorder"),
                true); // modal

        setResizable(false);

        docFrame        = doc.getFrame();
        superCollider   = SuperColliderClient.getInstance();
        numChannels     = doc.getAudioTrail().getChannelNum();

        doc.getTransport().stop();

        final Application   app         = AbstractApplication.getApplication();
        final Container     cp          = getContentPane();
        final String        className   = getClass().getName();
        final AudioFileDescr displayAFD = doc.getDisplayDescr();
        final MessageFormat frmtPeak    = new MessageFormat(getResourceString("msgPeak"), Locale.US); // XXX Locale.US
        final JRootPane     rp          = getRootPane();
        final InputMap      imap        = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        final ActionMap     amap        = rp.getActionMap();

        final SpringPanel   recPane;
        final JPanel        butPane;
        final WindowAdapter winListener;
        final JButton       ggPeakReset;
        final TimeLabel     lbTime;
        final Object[]      peakArgs = new Object[1];
        final JButton       ggAbort, ggRecord, ggStop, ggClose;

        final int myMeta = BasicMenuFactory.MENU_SHORTCUT == InputEvent.CTRL_MASK ?
                InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK : BasicMenuFactory.MENU_SHORTCUT;    // META on Mac, CTRL+SHIFT on PC

        // use same encoding as parent document
        encodingString = (displayAFD.sampleFormat == AudioFileDescr.FORMAT_INT ? "int" : "float") +
                displayAFD.bitsPerSample;

        audioPrefs = app.getUserPrefs().node(PrefsUtil.NODE_AUDIO);
        classPrefs = app.getUserPrefs().node(className.substring(className.lastIndexOf('.') + 1));

        recPane         = new SpringPanel(4, 2, 4, 2);
        ggRecordConfig  = new PrefComboBox();
        ggRecordConfig.setFocusable(false);
        lbPeak          = new JLabel();
        colrPeakNorm    = lbPeak.getForeground();
        actionPeakReset = new ActionPeakReset();
        ggPeakReset     = new JButton(actionPeakReset);
        ggPeakReset.setFocusable(false);
        lbTime          = new TimeLabel();
        ggMonitoring    = new JToggleButton(new ActionMonitoring());
        ggMonitoring.setFocusable(false);
        recPane.gridAdd(lbTime, 1, 0, /* -2 */ -1, 1);
        recPane.gridAdd(new JLabel(getResourceString("labelRecInputs"), SwingConstants.RIGHT), 0, 1);
        recPane.gridAdd(ggRecordConfig, 1, 1, -1, 1);
        recPane.gridAdd(ggMonitoring /* tbMonitoring */, 2, 1);
        recPane.gridAdd(new JLabel(getResourceString("labelHeadroom") + " :", SwingConstants.RIGHT), 0, 2);
        recPane.gridAdd(lbPeak, 1, 2);
        recPane.gridAdd(ggPeakReset, 2, 2, -1, 1);

        refillConfigs();

        ggRecordConfig.setPreferences(classPrefs, KEY_CONFIG);

        recPane.makeCompactGrid();

        butPane         = new JPanel(); // new FlowLayout( FlowLayout.TRAILING ));
        butPane.setLayout(new BoxLayout(butPane, BoxLayout.X_AXIS));
        actionRecord    = new ActionRecord();
        actionStop      = new ActionStop();
        actionAbort     = new ActionAbort();
        actionClose     = new ActionClose();
        try {
            butPane.add(new HelpButton(MenuFactory.helpFile("RecorderDialog")));
        } catch (MalformedURLException e) {
            // ignore
        }
        butPane.add(Box.createHorizontalGlue());
        ggAbort = new JButton(actionAbort);
        ggAbort.setFocusable(false);
        butPane.add(ggAbort);
        ggRecord = new JButton(actionRecord);
        ggRecord.setFocusable(false);
        butPane.add(ggRecord);
        ggStop = new JButton(actionStop);
        ggStop.setFocusable(false);
        butPane.add(ggStop);
        ggClose = new JButton(actionClose);
        ggClose.setFocusable(false);
        butPane.add(ggClose);
        butPane.add(CoverGrowBox.create());

        cp.add(recPane, BorderLayout.NORTH);
        cp.add(butPane, BorderLayout.SOUTH);

        meterTimer = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final float value = docFrame.getMaxMeterHold();
                final boolean valueClip = value > -0.2f;
                peakArgs[0] = value;
                lbPeak.setText(frmtPeak.format(peakArgs));
                if (valueClip && !clipped) {
                    clipped = true;
                    lbPeak.setForeground(Color.red);
                }
            }
        });

        recLenTimer = new RecLenTimer(lbTime, recFrames, doc.timeline.getRate());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        server = superCollider.getServer();
        player = superCollider.getPlayerForDocument(doc);

        if ((server == null) || (player == null) || !server.isRunning()) {
            throw new IOException(getResourceString("errServerNotRunning"));
        }

        osc = new OSCRouterWrapper(doc, this);

        // ---- listeners -----

        winListener = new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (!isRecording) {
                    disposeRecorder();
                }
            }
        };
        addWindowListener(winListener);

        superCollider.addServerListener(this);

        nw = superCollider.getNodeWatcher();
        nw.addListener(this);

        // ---- keyboard shortcuts ----
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, myMeta), "record");    // VK_SPACE doesn't work (WHY?)
        amap.put("record", new DoClickAction(ggRecord));
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, myMeta), "abort");
        amap.put("abort", new DoClickAction(ggAbort));
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, myMeta), "stop");
        amap.put("stop", new DoClickAction(ggStop));
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        amap.put("close", actionClose);

        docFrame.setForceMeters( true );
        player.setActiveInput( true );
        meterTimer.start();

        GUIUtil.setInitialDialogFocus(rp);    // necessary to get keyboard shortcuts working

        new DynamicAncestorAdapter(new DynamicPrefChangeManager(classPrefs, new String[]{KEY_CONFIG},
                this)).addTo(getRootPane());

        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void createDefs(int numInputChannels)
            throws IOException {

        final Control ctrlI = Control.ir(new String[]{"i_aInBs", "i_aOtBf"}, new float[]{0f, 0f});
        final GraphElem graph;
        final SynthDef def;

        if (numInputChannels > 0) {
            final GraphElem in = UGen.ar("In", numInputChannels, ctrlI.getChannel("i_aInBs"));
            graph = UGen.ar("DiskOut", ctrlI.getChannel("i_aOtBf"), in);
        } else {
            graph = ctrlI;
        }
        def = new SynthDef("eisk-rec" + numInputChannels, graph);
        def.send(server);
    }

    public File getResult()
    {
        return result;
    }

    private void createRecordConfig() {
        final String cfgName = classPrefs.get(KEY_CONFIG, null);

        RoutingConfig newRCfg = null;

        try {
            if (cfgName != null && audioPrefs.node(NODE_CONF).nodeExists(cfgName)) {
                newRCfg = new RoutingConfig(audioPrefs.node(NODE_CONF).node(cfgName));
            }
        } catch (BackingStoreException e1) {
            printError("createRecordConfig", e1);
        }

        if ((newRCfg != null) && (newRCfg.numChannels == numChannels)) {
            rCfg = newRCfg;
            actionRecord.setEnabled(true);
        } else {
            rCfg = null;
            actionRecord.setEnabled(false);
        }
        rebuildSynths();
    }

    private void disposeAll() {
        disposeContext();
    }

    private void rebuildSynths() {
        final int chanOff;

        try {
            disposeAll();

            if (rCfg == null) return;

            ct = new Context(rCfg.numChannels, player.getInputBus());
            chanOff = server.getOptions().getNumOutputBusChannels();

            createDefs(rCfg.numChannels);

            final OSCBundle bndl = new OSCBundle(0.0);

            for (int ch = 0; ch < rCfg.numChannels; ch++) {
                bndl.addPacket(ct.synthsRoute[ch].newMsg(ct.grpRoot, new String[]{
                        "i_aInBs", "i_aOtBs"}, new float[]{
                        rCfg.mapping[ch] + chanOff, ct.busInternal.getIndex() + ch}, kAddToHead));
                nw.register(ct.synthsRoute[ch]);
            }

            server.sendBundle(bndl);
            if (!server.sync(4.0f)) {
                printTimeOutMsg();
            }

        } catch (IOException e1) {
            printError("rebuildSynths", e1);
        }
    }

    private void printTimeOutMsg() {
        Server.getPrintStream().println(AbstractApplication.getApplication().getResourceString("errOSCTimeOut"));
    }

    protected void disposeRecorder() {
        osc.remove();
        meterTimer.stop();
        docFrame.setForceMeters(false);
        setVisible(false);
        player.setActiveInput(false);
        player.setActiveOutput(false);
        superCollider.removeServerListener(this);
        disposeAll();
        dispose();    // JFrame / JDialog
    }

    private void disposeContext() {
        if (ct != null) {
            try {
                ct.dispose();
            } catch (IOException e1) {
                printError("disposeContext", e1);
            }
            ct = null;
        }
    }


    public void refillConfigs() {
        final Preferences childPrefs;
        final String[] cfgIDs;
        Preferences cfgPrefs;

        try {
            childPrefs = audioPrefs.node(NODE_CONF);
            cfgIDs = childPrefs.childrenNames();
            ggRecordConfig.removeAllItems();
            for (String cfgID : cfgIDs) {
                cfgPrefs = childPrefs.node(cfgID);
                if (cfgPrefs.getInt(RoutingConfig.KEY_RC_NUMCHANNELS, -1) == numChannels) {
                    ggRecordConfig.addItem(new StringItem(cfgID, cfgPrefs.get(RoutingConfig.KEY_NAME, cfgID)));
                }
            }
        } catch (BackingStoreException e1) {
            System.err.println(e1.getClass().getName() + " : " + e1.getLocalizedMessage());
        }
    }

    protected static void printError(String name, Throwable t) {
        System.err.println(name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage());
    }

    protected String getResourceString(String key) {
        return AbstractApplication.getApplication().getResourceString(key);
    }

    protected void stopRecording(boolean commit) {
        recLenTimer.stop();

        if ((server == null) || !server.isRunning() || (ct == null)) return;

        final OSCBundle bndl = new OSCBundle();

        try {
            bndl.addPacket(ct.synthDiskOut.freeMsg());
            bndl.addPacket(ct.bufDisk.closeMsg());
            stopCommit = commit;
            server.sendBundle(bndl);
            timeoutTimer.stop();
            timeoutTimer.setMessage("Failed to stop recording synth");
            timeoutTimer.setActions(new Action[]{actionStop, actionAbort, actionClose});
            timeoutTimer.start();
            timeoutTimer.enable(false);
        } catch (IOException e1) {
            printError(getResourceString("buttonStop"), e1);
        }
    }

    // ------------- OSCRouter interface -------------

    public String oscGetPathComponent()
    {
        return OSC_RECORDER;
    }

    public void oscRoute( RoutedOSCMessage rom )
    {
        osc.oscRoute( rom );
    }

    public void oscAddRouter( OSCRouter subRouter )
    {
        osc.oscAddRouter( subRouter );
    }

    public void oscRemoveRouter( OSCRouter subRouter )
    {
        osc.oscRemoveRouter( subRouter );
    }

    public Object oscQuery_recording()
    {
        return isRecording ? 1 : 0;
    }

    public Object oscQuery_length()
    {
        return recFrames.value();
    }

    public Object oscQuery_monitoring()
    {
        return ggMonitoring.isSelected() ? 1 : 0;
    }

    public Object oscQuery_headroom()
    {
        return docFrame.getMaxMeterHold();
    }

    public void oscCmd_resetHeadroom( RoutedOSCMessage rom )
    {
        actionPeakReset.perform();
    }

// ------------- ServerListener interface -------------

    public void serverAction(ServerEvent e) {
        if (e.getID() == ServerEvent.STOPPED) {
            disposeRecorder();
        }
    }

    // ------------- PreferenceChangeListener interface -------------

    public void preferenceChange( PreferenceChangeEvent e )
    {
        createRecordConfig();
    }

// ------------- NodeListener interface -------------

    public void nodeAction(NodeEvent e) {
        if ((ct == null) || (e.getNode() != ct.synthDiskOut)) return;

        switch (e.getID()) {
            case NodeEvent.GO:
                isRecording = true;
                timeoutTimer.stop();
                actionStop.setEnabled(true);
                actionAbort.setEnabled(true);
                actionClose.setEnabled(false);
                recLenTimer.restart();
                break;

            case NodeEvent.END:
                isRecording = false;
                timeoutTimer.stop();
                recLenTimer.stop();
                actionRecord.setEnabled(true);
                actionClose.setEnabled(true);
                if (ct != null) {
                    if (stopCommit) {
                        result = ct.recFile;
                        ct.forgetFile(true);
                        disposeRecorder();
                    } else {
                        try {
                            ct.recreateFile();
                        } catch (IOException e1) {
                            BasicWindowHandler.showErrorDialog(RecorderDialog.this, e1, getTitle());
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

// ------------- internal classes -------------

    private class Context {
        protected Group grpRoot;
        protected final Synth synthDiskOut;    // one multi-channel disk out synth
        protected final Synth[] synthsRoute;    // for each config channel one route
        protected Buffer bufDisk = null;
        protected Bus busInternal = null;        // reference from SuperColliderPlayer!

        protected File recFile = null;

        /*
         *  @throws IOException if the server ran out of busses or buffers
         */
        protected Context(int numConfigChannels, Bus busInternal)
                throws IOException {
            this.busInternal = busInternal;

            try {
                synthDiskOut = Synth.basicNew("eisk-rec" + numConfigChannels, server);
                synthsRoute = new Synth[numConfigChannels];
                for (int i = 0; i < numConfigChannels; i++) {
                    synthsRoute[i] = Synth.basicNew("eisk-route", server);
                }
                bufDisk = Buffer.alloc(server, DISK_BUF_SIZE, numConfigChannels);

                if( (bufDisk == null) || (busInternal == null) ) {
                    throw new IOException( "Server ran out of buffers or busses!" );
                }

                // the group is added to the *head* coz we want to be able to write
                // to the player's input bus (to see the meters, to allow monitoring)
                grpRoot = new Group(server.getDefaultGroup(), kAddToHead);
                grpRoot.setName("Recorder");
                nw.register(grpRoot);

                recFile = IOUtil.createTempFile();

            } catch (IOException e1) {
                dispose();
                throw e1;
            }
        }

        protected void forgetFile(boolean keep) {
            if ((recFile != null) && !keep) {
                if (!recFile.delete()) {
                    System.err.println("Couldn't delete temp file " + recFile.getAbsolutePath());
                }
            }
            recFile = null;
        }

        protected void recreateFile()
                throws IOException {

            forgetFile(false);
            recFile = IOUtil.createTempFile();
        }

        protected void dispose()
                throws IOException {
            IOException e11 = null;

            forgetFile(false);

            if ((bufDisk != null) && bufDisk.getServer().isRunning()) {
                try {
                    bufDisk.free();
                } catch (IOException e1) {
                    e11 = e1;
                }
            }

            if ((grpRoot != null) && grpRoot.getServer().isRunning()) grpRoot.free();
            if (e11 != null) throw e11;
        }
    }

    private class ActionPeakReset
            extends AbstractAction {
        protected ActionPeakReset()
        {
            super( getResourceString( "buttonReset" ));
        }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }


        protected void perform() {
            docFrame.clearMeterHold();
            lbPeak.setForeground(colrPeakNorm);
            clipped = false;
        }
    }

    private class ActionMonitoring
            extends AbstractAction {
        protected ActionMonitoring() {
            super(getResourceString("buttonMonitoring"));
        }

        public void actionPerformed(ActionEvent e) {
            final AbstractButton b = (AbstractButton) e.getSource();

            player.setActiveOutput(b.isSelected());
        }
    }

    private class ActionRecord
            extends AbstractAction {
        protected ActionRecord() {
            super(getResourceString("buttonRecord"));
            setEnabled(false);
        }

        private final SimpleDateFormat fmtMessageDate =
                new SimpleDateFormat("'Record start: 'HH'h'mm'm'ss.SSS's'", Locale.US);

        public void actionPerformed(ActionEvent e) {
            if ((server == null) || !server.isRunning() || (ct == null)) return;

            if (ct.recFile == null) {
                try {
                    ct.recreateFile();
                } catch (IOException e1) {
                    BasicWindowHandler.showErrorDialog(RecorderDialog.this, e1, getValue(NAME).toString());
                    return;
                }
            }

            final OSCBundle bndl = new OSCBundle();
            final OSCMessage msgWrite;

            try {
                bndl.addPacket(ct.bufDisk.closeMsg());
                msgWrite = ct.bufDisk.writeMsg(ct.recFile.getAbsolutePath(), "aiff",
                        encodingString, 0, 0, true);
                bndl.addPacket(msgWrite);
                if (server.sendBundleSync(bndl, msgWrite.getName(), 4)) {
                    nw.register(ct.synthDiskOut);

                    server.sendMsg(ct.synthDiskOut.newMsg(ct.grpRoot, new String[]{"i_aInBs", "i_aOtBf"},
                            new float[]{ct.busInternal.getIndex(), ct.bufDisk.getBufNum()}, kAddToTail));
                    timeoutTimer.stop();
                    timeoutTimer.setMessage("Failed to initialize recording synth");
                    timeoutTimer.setActions(new Action[]{actionRecord});
                    timeoutTimer.start();
                    timeoutTimer.enable(false);
                    System.out.println(fmtMessageDate.format(new Date()));

                } else {
                    System.err.println("Failed to initialize recording buffer");
                }
            } catch (IOException e1) {
                printError(getValue(NAME).toString(), e1);
            }
        }
    }

    private class ActionStop
            extends AbstractAction {
        protected ActionStop() {
            super(getResourceString("buttonStop"));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            stopRecording(true);
        }
    }

    private class ActionAbort
            extends AbstractAction {
        protected ActionAbort() {
            super(getResourceString("buttonAbort"));
            setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            stopRecording(false);
        }
    }

    private class ActionClose
            extends AbstractAction {
        protected ActionClose() {
            super(getResourceString("buttonClose"));
        }

        public void actionPerformed(ActionEvent e) {
            if (!isRecording) {
                disposeRecorder();
            }
        }
    }

    private static class TimeoutTimer
            extends javax.swing.Timer
            implements ActionListener {
        private String errorMsg;
        private Action[] actions;

        protected TimeoutTimer(int timeOutMillis) {
            super(timeOutMillis, null);
            addActionListener(this);
            setRepeats(false);
        }

        protected void setMessage(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        protected void setActions(Action[] actions) {
            this.actions = actions;
        }

        public void actionPerformed(ActionEvent e) {
            System.err.println(errorMsg);
            enable(true);
        }

        protected void enable(boolean onOff) {
            for (Action action : actions) {
                action.setEnabled(onOff);
            }
        }
    }

    private static class RecLenTimer
            extends javax.swing.Timer
            implements ActionListener {

        private final TimeLabel lbTime;
        private final MutableLong frames;
        private long startTime;
        private final double sampleRate;

        protected RecLenTimer(TimeLabel lbTime, MutableLong frames, double sampleRate) {
            super(66, null);
            this.lbTime = lbTime;
            this.frames = frames;
            this.sampleRate = sampleRate;
            addActionListener(this);
        }

        public void restart() {
            startTime = System.currentTimeMillis();
            super.restart();
        }

        public void actionPerformed(ActionEvent e) {
            final double secs = (double) (System.currentTimeMillis() - startTime) / 1000;
            frames.set((long) (secs * sampleRate + 0.5));
            lbTime.setTime(secs);
            GraphicsUtil.sync();
        }
    }
}
