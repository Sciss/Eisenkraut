/*
 *  DocumentFrame.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.session;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.GraphicsHandler;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.common.ShowWindowAction;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.edit.BasicCompoundEdit;
import de.sciss.eisenkraut.edit.TimelineVisualEdit;
import de.sciss.eisenkraut.gui.AbstractTool;
import de.sciss.eisenkraut.gui.AudioFileInfoPalette;
import de.sciss.eisenkraut.gui.CrossfadePanel;
import de.sciss.eisenkraut.gui.GraphicsUtil;
import de.sciss.eisenkraut.gui.MenuFactory;
import de.sciss.eisenkraut.gui.ObserverPalette;
import de.sciss.eisenkraut.gui.PeakMeterManager;
import de.sciss.eisenkraut.gui.ProgressPanel;
import de.sciss.eisenkraut.gui.RecorderDialog;
import de.sciss.eisenkraut.gui.ToolAction;
import de.sciss.eisenkraut.gui.ToolActionEvent;
import de.sciss.eisenkraut.gui.ToolActionListener;
import de.sciss.eisenkraut.gui.WaveformView;
import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.io.DecimatedSonaTrail;
import de.sciss.eisenkraut.io.DecimatedTrail;
import de.sciss.eisenkraut.io.DecimatedWaveTrail;
import de.sciss.eisenkraut.io.DecimationInfo;
import de.sciss.eisenkraut.io.MarkerTrail;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.net.SuperColliderPlayer;
import de.sciss.eisenkraut.realtime.Transport;
import de.sciss.eisenkraut.realtime.TransportListener;
import de.sciss.eisenkraut.realtime.TransportToolBar;
import de.sciss.eisenkraut.render.FilterDialog;
import de.sciss.eisenkraut.render.RenderPlugIn;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.timeline.AudioTrackRowHeader;
import de.sciss.eisenkraut.timeline.MarkerAxis;
import de.sciss.eisenkraut.timeline.TimelineAxis;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;
import de.sciss.eisenkraut.timeline.TimelineScroll;
import de.sciss.eisenkraut.timeline.TimelineToolBar;
import de.sciss.eisenkraut.timeline.Track;
import de.sciss.eisenkraut.timeline.TrackRowHeader;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.Axis;
import de.sciss.gui.ComponentBoundsRestrictor;
import de.sciss.gui.ComponentHost;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.GradientPanel;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuRoot;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.PathField;
import de.sciss.gui.PeakMeter;
import de.sciss.gui.PeakMeterGroup;
import de.sciss.gui.ProgressComponent;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StretchedGridLayout;
import de.sciss.gui.TopPainter;
import de.sciss.gui.TreeExpanderButton;
import de.sciss.gui.VectorSpace;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.AudioFileFormatPane;
import de.sciss.io.IOUtil;
import de.sciss.io.Marker;
import de.sciss.io.Span;
import de.sciss.timebased.Trail;
import de.sciss.util.Flag;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

public class DocumentFrame
        extends AppWindow
        implements ProgressComponent, TimelineListener,
        ClipboardOwner, ToolActionListener,
        DecimatedWaveTrail.AsyncListener,
        TransportListener, PreferenceChangeListener,
        SwingConstants {

    protected final Session					doc;

    private final TimelineAxis				timeAxis;
    protected final MarkerAxis				markAxis;
    protected final TrackRowHeader			markAxisHeader;
    protected final TimelineScroll			scroll;
    protected final Transport				transport;

    protected Span							timelineSel;
    protected Span							timelineVis;
    protected long							timelinePos;
    protected long							timelineLen;
    protected double						timelineRate;

    protected final WaveformView			waveView;
    protected final ComponentHost			wavePanel;
    protected final JPanel					channelHeaderPanel;
    private final JPanel					flagsPanel;
    private final JPanel					rulersPanel;
    private final JPanel					metersPanel;
    private final List<AudioTrackRowHeader> collChannelHeaders		= new ArrayList<AudioTrackRowHeader>();
    protected final List<Axis> collChannelRulers		= new ArrayList<Axis>();
//	private final List						collChannelMeters		= new ArrayList();
    private PeakMeter[]						channelMeters			= new PeakMeter[ 0 ];

    private final JLabel					lbSRC;
    protected final TreeExpanderButton		ggTreeExp;

    private DecimatedTrail					asyncTrail				= null;

    // --- tools ---

    private final Map<Integer, TimelineTool> tools					= new HashMap<Integer, TimelineTool>();
    private			AbstractTool			activeTool				= null;
    private final	TimelinePointerTool		pointerTool;

    // --- actions ---
    private final static String				plugInPackage			= "de.sciss.eisenkraut.render.";
    private final static String				fscapePackage			= "de.sciss.fscape.render.";

    private final ActionRevealFile			actionRevealFile;
    private final ActionNewFromSel			actionNewFromSel;
    protected final ActionClose				actionClose;
    protected final ActionSave				actionSave;
    protected final ActionSaveAs			actionSaveAs;
    private final ActionSaveAs				actionSaveSelectionAs;
    private final MenuAction				actionProcess;
    protected final ActionProcessAgain		actionProcessAgain;

    protected final ActionScroll			actionZoomAllOut;

    private final AbstractWindow.Adapter	winListener;

    private final JLabel					lbWriteProtected;
    private boolean							writeProtected			= false;
    protected boolean						wpHaveWarned			= false;

    private final ShowWindowAction			actionShowWindow;

    private static final String smpPtrn			= "ch.{3} @ {0,number,0}";
    private static final String timePtrn		= "ch.{3} @ {1,number,integer}:{2,number,00.000}";
    protected final MessageFormat msgCsr1		= new MessageFormat( timePtrn, Locale.US );
    protected final MessageFormat msgCsr2PCMFloat = new MessageFormat( "{4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
    protected final MessageFormat msgCsr3PCMInt	= new MessageFormat( "= {6,number,0} @ {7,number,integer}-bit int", Locale.US );
    protected final MessageFormat msgCsr2Peak	= new MessageFormat( "peak {4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
    protected final MessageFormat msgCsr3RMS	= new MessageFormat( "eff {6,number,0.000} ({7,number,0.00} dBFS)", Locale.US );
    protected int					csrInfoBits;
    protected boolean				csrInfoIsInt;
    protected static final double TWENTYDIVLOG10 = 20 / Math.log( 10 );

    private final Color colrClear				= new Color( 0xA0, 0xA0, 0xA0, 0x00 );

    // --------- former viewport ---------
    // --- painting ---
    private final Color colrSelection			= GraphicsUtil.colrSelection;
    private final Color colrSelection2			= new Color( 0x00, 0x00, 0x00, 0x20 );  // selected timeline span over unselected trns
    protected final Color colrPosition			= new Color( 0xFF, 0x00, 0x00, 0x7F );
    protected final Color colrZoom				= new Color( 0xA0, 0xA0, 0xA0, 0x7F );
    protected Rectangle	vpRecentRect			= new Rectangle();
    protected int		vpPosition				= -1;
    private Rectangle   vpPositionRect			= new Rectangle();
    protected final ArrayList<Rectangle> vpSelections		= new ArrayList<Rectangle>();
    protected final ArrayList<Color> vpSelectionColors	= new ArrayList<Color>();
    protected Rectangle	vpSelectionRect			= new Rectangle();

    private Rectangle   vpUpdateRect			= new Rectangle();
    protected Rectangle	vpZoomRect				= null;
    private float[]		vpDash					= { 3.0f, 5.0f };
    private float		vpScale;

    protected final Stroke[] vpZoomStroke			= {
        new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 0.0f ),
        new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 4.0f ),
        new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 6.0f ),
    };
    protected int		vpZoomStrokeIdx			= 0;

    protected boolean	waveExpanded			= true;	// XXX should keep that in some prefs
    protected boolean	viewMarkers;
    protected boolean	markVisible;
    private boolean		chanMeters				= false;
    private boolean		forceMeters				= false;

    protected final TimelineToolBar			timeTB;
    private final TransportToolBar			transTB;

    // --- progress bar ---

    private final JLabel /* JTextField */ ggAudioFileDescr;
    private final ProgressPanel				pProgress;
    private final CrossfadePanel			pOverlay;

    private final boolean					internalFrames;

    protected final BasicApplication		app;
    private final PeakMeterManager			lmm;

    protected boolean						disposed		= false;

    private final Timer						playTimer;
    private double							playRate		= 1.0;

    protected final ComponentBoundsRestrictor cbr;

    private static Point					lastLeftTop		= new Point();
    private static final String				KEY_TRACKSIZE	= "tracksize";

    private int								verticalScale;

    protected static final Cursor[]			zoomCsr;

    static {
        final Toolkit tk		= Toolkit.getDefaultToolkit();
        final Point   hotSpot	= new Point( 6, 6 );
        zoomCsr					= new Cursor[] {
            tk.createCustomCursor( tk.createImage(
                ToolAction.class.getResource( "zoomin.png" )), hotSpot, "zoom-in" ),
            tk.createCustomCursor( tk.createImage(
                ToolAction.class.getResource( "zoomout.png" )), hotSpot, "zoom-out" )
        };
    }

    /**
     *  Constructs a new timeline window with
     *  all the sub elements. Installs the
     *  global key commands. (a DocumentFrame
     *  should be created only once in the application).
     *
     *  @param  doc		session Session
     */
    public DocumentFrame(final Session doc) {
        super(REGULAR);

        app					= (BasicApplication) AbstractApplication.getApplication();

        this.doc			= doc;
        transport			= doc.getTransport();
        timelinePos			= doc.timeline.getPosition();
        timelineSel			= doc.timeline.getSelectionSpan();
        timelineVis			= doc.timeline.getVisibleSpan();
        timelineRate		= doc.timeline.getRate();
        timelineLen			= doc.timeline.getLength();

        SuperColliderClient superCollider = SuperColliderClient.getInstance();

        lmm					= new PeakMeterManager( superCollider.getMeterManager() );

        final Container					cp			= getContentPane();
        final InputMap					iMap		= getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
        final ActionMap					aMap		= getActionMap();
        final AbstractButton			ggAudioInfo, ggRevealFile;
        final int						myMeta		= BasicMenuFactory.MENU_SHORTCUT == InputEvent.CTRL_MASK ?
            InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK : BasicMenuFactory.MENU_SHORTCUT;	// META on Mac, CTRL+SHIFT on PC
        final TopPainter				trackPainter;
        final MenuRoot					mr;
        final JPanel					topPane		= GUIUtil.createGradientPanel();
        Box								box;

        internalFrames		= app.getWindowHandler().usesInternalFrames();

        timeTB		= new TimelineToolBar( doc );
        transTB		= new TransportToolBar( doc );

        wavePanel			= new ComponentHost();
        timeAxis			= new TimelineAxis( doc, wavePanel );
        markAxis			= new MarkerAxis( doc, wavePanel );
        viewMarkers			= app.getUserPrefs().getBoolean( PrefsUtil.KEY_VIEWMARKERS, false );
        markVisible			= viewMarkers && waveExpanded;
        markAxisHeader		= new TrackRowHeader( doc.markerTrack, doc.tracks, doc.selectedTracks, doc.getUndoManager() );
        markAxisHeader.setPreferredSize( new Dimension( 63, markAxis.getPreferredSize().height ));	// XXX
        markAxisHeader.setMaximumSize( new Dimension( 128, markAxis.getMaximumSize().height ));		// XXX
        if( markVisible ) {
            markAxis.startListening();
        } else {
            markAxis.setVisible( false );
            markAxisHeader.setVisible( false );
        }
        flagsPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 ));
        metersPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 )); // SpringPanel( 0, 0, 1, 1 );
        rulersPanel			= new JPanel( new StretchedGridLayout( 0, 1, 1, 1 ));
        lmm.setDynamicComponent( metersPanel );
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        channelHeaderPanel	= new JPanel();
        channelHeaderPanel.setLayout( new BoxLayout( channelHeaderPanel, BoxLayout.X_AXIS ));
final Box bbb = Box.createVerticalBox();
final GradientPanel gp = GUIUtil.createGradientPanel();
gp.setBottomBorder( true );
gp.setLayout( null );
gp.setPreferredSize( new Dimension( 0, timeAxis.getPreferredSize().height ));
bbb.add( gp );
bbb.add( markAxisHeader );
        waveHeaderPanel.add(bbb, BorderLayout.NORTH);
        channelHeaderPanel.add( flagsPanel );
        channelHeaderPanel.add( metersPanel );
        channelHeaderPanel.add( rulersPanel );
        waveHeaderPanel.add(channelHeaderPanel, BorderLayout.CENTER);

        waveView			= new WaveformView( doc, wavePanel );
        wavePanel.setLayout( new BoxLayout( wavePanel, BoxLayout.Y_AXIS ));
        wavePanel.add( timeAxis );
        wavePanel.add( markAxis );
        wavePanel.add( waveView );

        scroll				= new TimelineScroll( doc );
        JPanel ggTrackPanel = new JPanel(new BorderLayout());
        ggTrackPanel.add(wavePanel, BorderLayout.CENTER);
        ggTrackPanel.add(waveHeaderPanel, BorderLayout.WEST);
        ggTrackPanel.add(scroll, BorderLayout.SOUTH);
        
        lbWriteProtected	= new JLabel();
        ggAudioInfo			= new ModificationButton( ModificationButton.SHAPE_INFO );
        ggAudioInfo.setAction( new ActionAudioInfo() );
        ggRevealFile		= new ModificationButton( ModificationButton.SHAPE_REVEAL );
        actionRevealFile	= new ActionRevealFile();
        ggRevealFile.setAction( actionRevealFile );
        //		ggAudioFileDescr	= new JTextField( 32 );
        //		ggAudioFileDescr.setEditable( false );
        //		ggAudioFileDescr.setFocusable( false );
        //		ggAudioFileDescr.setBackground( null );
        //		ggAudioFileDescr.setBorder( null );
        ggAudioFileDescr	= new JLabel();

        lbSRC				= new JLabel( getResourceString( "buttonSRC" ));
        lbSRC.setForeground( colrClear );
        box					= Box.createHorizontalBox();
        box.add( Box.createHorizontalStrut( 4 ));
        box.add( lbWriteProtected );
        box.add( ggAudioInfo );
        if (internalFrames || Main.isLinux /* !Main.isMac */) box.add(ggRevealFile);
        box.add( Box.createHorizontalStrut( 4 ));

        pProgress			= new ProgressPanel();
        pOverlay			= new CrossfadePanel();
        pOverlay.setComponentA( ggAudioFileDescr );
        pOverlay.setComponentB( pProgress );
        box.add( pOverlay );

        box.add( Box.createHorizontalStrut( 4 ));
        box.add( lbSRC );
        box.add( CoverGrowBox.create( 2, 0 ));

        updateAFDGadget();
        updateCursorFormat();

// ----- afr export -----
        final JButton ggExportAFR = new JButton(getResourceString("buttonDragRegion"), new ImageIcon(getClass().getResource("dragicon.png")));
        ggExportAFR.setTransferHandler( new AFRTransferHandler() );
        final MouseInputAdapter expAFRmia = new MouseInputAdapter() {
            private MouseEvent dndInit = null;
            private boolean dndStarted = false;

            public void mousePressed( MouseEvent e )
            {
                dndInit		= e;
                dndStarted	= false;
            }

            public void mouseReleased( MouseEvent e )
            {
                dndInit		= null;
                dndStarted	= false;
            }

            public void mouseDragged( MouseEvent e )
            {
                if( !dndStarted && (dndInit != null) &&
                    ((Math.abs( e.getX() - dndInit.getX() ) > 5) ||
                     (Math.abs( e.getY() - dndInit.getY() ) > 5))) {

                    JComponent c = (JComponent) e.getSource();
                    c.getTransferHandler().exportAsDrag( c, e, TransferHandler.COPY );
                    dndStarted = true;
                }
            }
        };

        ggExportAFR.addMouseListener( expAFRmia );
        ggExportAFR.addMouseMotionListener( expAFRmia );

        timeTB.add( Box.createHorizontalStrut( 4 ));
        timeTB.addButton( ggExportAFR );
// ----------

        topPane.setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
        timeTB.setOpaque( false );
        topPane.add( timeTB );
        transTB.setOpaque( false );
        topPane.add( transTB );
        topPane.add( Box.createHorizontalGlue() );
        cbr			= new ComponentBoundsRestrictor();
        ggTreeExp	= new TreeExpanderButton();
        ggTreeExp.setExpandedToolTip( getResourceString( "buttonExpWaveTT" ));
        ggTreeExp.setCollapsedToolTip( getResourceString( "buttonCollWaveTT" ));
        ggTreeExp.setExpanded( true );
        ggTreeExp.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                final Dimension d	= getSize();

                waveExpanded	= ggTreeExp.isExpanded();
                markVisible		= viewMarkers && waveExpanded;

                if( waveExpanded ) {
                    cbr.remove( getWindow() );
                    waveView.setVisible( true );
                    channelHeaderPanel.setVisible( true );
                    if( viewMarkers ) {
                        markAxis.setVisible( true );
                        markAxisHeader.setVisible( true );
                    }
                    scroll.setVisible( true );
                    timeTB.setVisible( true );
                    pack();

                } else {
                    checkDecimatedTrails();
                    setPreferredSize( getSize() );

                    waveView.setVisible(false);
                    channelHeaderPanel.setVisible(false);
                    markAxis.setVisible(false);
                    markAxisHeader.setVisible(false);
                    scroll.setVisible(false);
                    timeTB.setVisible(false);
                    actionZoomAllOut.perform();

                    final int h = d.height - (waveView.getHeight() + scroll.getHeight() +
                            (viewMarkers ? markAxis.getHeight() : 0));
                    setSize(new Dimension(d.width - timeTB.getWidth(), h));
                    cbr.setMinimumHeight(h);
                    cbr.setMaximumHeight(h);
                    cbr.add(getWindow());
                }
            }
        });
        topPane.add( ggTreeExp );

        gp.setGradientShift(0, topPane.getPreferredSize().height);

        cp.add(topPane, BorderLayout.NORTH);
        cp.add(ggTrackPanel, BorderLayout.CENTER);
        cp.add(box, BorderLayout.SOUTH);

        // --- Tools ---

        pointerTool = new TimelinePointerTool();
        tools.put(ToolAction.POINTER, pointerTool);
        tools.put(ToolAction.ZOOM, new TimelineZoomTool());

        // ---- TopPainter ----

        trackPainter	= new TopPainter() {
            public void paintOnTop( Graphics2D g2 )
            {
                Rectangle r;

                r = new Rectangle(0, 0, wavePanel.getWidth(), wavePanel.getHeight()); // getViewRect();
                if (!vpRecentRect.equals(r)) {
                    recalcTransforms(r);
                }

                for (int i = 0; i < vpSelections.size(); i++) {
                    r = vpSelections.get(i);
                    g2.setColor(vpSelectionColors.get(i));
                    g2.fillRect(vpSelectionRect.x, r.y - vpRecentRect.y, vpSelectionRect.width, r.height);
                }

                if (markVisible) {
                    markAxis.paintFlagSticks(g2, vpRecentRect);
                }

                g2.setColor(colrPosition);
                g2.drawLine(vpPosition, 0, vpPosition, vpRecentRect.height);

                if (vpZoomRect != null) {
                    g2.setColor(colrZoom);
                    g2.setStroke(vpZoomStroke[vpZoomStrokeIdx]);
                    g2.drawRect(vpZoomRect.x, vpZoomRect.y, vpZoomRect.width, vpZoomRect.height);
                }
            }
        };
        wavePanel.addTopPainter( trackPainter );

        // ---- listeners ----

        doc.timeline.addTimelineListener( this );
//		doc.addListener( this );

//		checkDecimatedTrails();

        doc.audioTracks.addListener( new SessionCollection.Listener() {
            public void sessionCollectionChanged( SessionCollection.Event e )
            {
                documentUpdate();
            }

            public void sessionObjectMapChanged( SessionCollection.Event e ) { /* ignored */ }

            public void sessionObjectChanged( SessionCollection.Event e )
            {
                // nothing
            }
        });

        doc.selectedTracks.addListener( new SessionCollection.Listener() {
            public void sessionCollectionChanged( SessionCollection.Event e )
            {
                updateSelectionAndRepaint();
            }

            public void sessionObjectMapChanged( SessionCollection.Event e ) { /* ignore */ }
            public void sessionObjectChanged( SessionCollection.Event e ) { /* ignore */ }
        });

        transport.addTransportListener( this );

        doc.markers.addListener( new Trail.Listener() {
            public void trailModified( Trail.Event e )
            {
                repaintMarkers( e.getAffectedSpan() );
            }
        });

        doc.getAudioTrail().addListener( new Trail.Listener() {
            public void trailModified( Trail.Event e )
            {
                if( !waveExpanded || !e.getAffectedSpan().touches( timelineVis )) return;

                updateOverviews( false, false );
            }
        });

        winListener = new AbstractWindow.Adapter() {
            public void windowClosing( AbstractWindow.Event e ) {
                actionClose.perform();
            }

            public void windowActivated( AbstractWindow.Event e )
            {
                // need to check 'disposed' to avoid runtime exception in doc handler if document was just closed
                if( !disposed ) {
                    app.getDocumentHandler().setActiveDocument( DocumentFrame.this, doc );
                    ((BasicWindowHandler) app.getWindowHandler()).setMenuBarBorrower( DocumentFrame.this );
                }
            }
        };
        this.addListener( winListener );

        waveView.addComponentListener( new ComponentAdapter() {
            public void componentResized( ComponentEvent e )
            {
                updateSelectionAndRepaint();
            }
        });

        timeTB.addToolActionListener( this );
        timeTB.selectTool( ToolAction.POINTER );

        playTimer = new Timer( 33, new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                timelinePos = transport.getCurrentFrame();
                updatePositionAndRepaint();
                scroll.setPosition( timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
            }
        });

        // --- Actions ---
        actionNewFromSel	= new ActionNewFromSel();
        actionClose			= new ActionClose();
        actionSave			= new ActionSave();
        actionSaveAs		= new ActionSaveAs( false, false );
        ActionSaveAs actionSaveCopyAs = new ActionSaveAs(true, false);
        actionSaveSelectionAs = new ActionSaveAs( true, true );
        ActionSelectAll actionSelectAll = new ActionSelectAll();
        MenuAction actionInsertRec = new ActionInsertRec();

        actionProcess		= new ActionProcess();
        actionProcessAgain	= new ActionProcessAgain();
        MenuAction actionFadeIn = new ActionPlugIn(plugInPackage + "FadeIn");
        MenuAction actionFadeOut = new ActionPlugIn(plugInPackage + "FadeOut");
        MenuAction actionGain = new ActionPlugIn(plugInPackage + "Gain");
        MenuAction actionInvert = new ActionPlugIn(plugInPackage + "Invert");
        MenuAction actionReverse = new ActionPlugIn(plugInPackage + "Reverse");
        MenuAction actionRotateChannels = new ActionPlugIn(plugInPackage + "RotateChannels");
        MenuAction actionFScNeedlehole = new ActionPlugIn(fscapePackage + "Needlehole");

        MenuAction actionDebugDump = new ActionDebugDump();
        MenuAction actionDebugVerify = new ActionDebugVerify();

        AbstractAction actionIncVertMax = new ActionVerticalMax(2.0f, 6f);
        AbstractAction actionDecVertMax = new ActionVerticalMax(0.5f, -6f);
        AbstractAction actionIncVertMin = new ActionVerticalMin(6f);
        AbstractAction actionDecVertMin = new ActionVerticalMin(-6f);
        ActionSpanWidth actionIncHoriz = new ActionSpanWidth(2.0f);
        ActionSpanWidth actionDecHoriz = new ActionSpanWidth(0.5f);
        actionZoomAllOut	= new ActionScroll( SCROLL_ENTIRE_SESSION );

        actionShowWindow	= new ShowWindowAction( this );

        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.CTRL_MASK ), "incvmax" );
        aMap.put( "incvmax", actionIncVertMax);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.CTRL_MASK ), "decvmax" );
        aMap.put( "decvmax", actionDecVertMax);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.CTRL_MASK | InputEvent.ALT_MASK ), "incvmin" );
        aMap.put( "incvmin", actionIncVertMin);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.CTRL_MASK | InputEvent.ALT_MASK ), "decvmin" );
        aMap.put( "decvmin", actionDecVertMin);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, InputEvent.CTRL_MASK ), "inch" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, BasicMenuFactory.MENU_SHORTCUT ), "inch" );
        aMap.put( "inch", actionIncHoriz);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, InputEvent.CTRL_MASK ), "dech" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, BasicMenuFactory.MENU_SHORTCUT ), "dech" );
        aMap.put( "dech", actionDecHoriz);
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, myMeta ), "samplvl" );
        aMap.put( "samplvl", new ActionSpanWidth( 0.0f ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
        aMap.put( "retn", new ActionScroll( SCROLL_SESSION_START ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
        aMap.put( "left", new ActionScroll( SCROLL_SELECTION_START ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
        aMap.put( "right", new ActionScroll( SCROLL_SELECTION_STOP ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, InputEvent.ALT_MASK ), "fit" );
        aMap.put( "fit", new ActionScroll( SCROLL_FIT_TO_SELECTION ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, InputEvent.ALT_MASK ), "entire" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, myMeta ), "entire" );
        aMap.put( "entire", actionZoomAllOut );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK ), "seltobeg" );
        aMap.put( "seltobeg", new ActionSelect( SELECT_TO_SESSION_START ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK + InputEvent.ALT_MASK ), "seltoend" );
        aMap.put( "seltoend", new ActionSelect( SELECT_TO_SESSION_END ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ), "postoselbegc" );
        aMap.put( "postoselbegc", doc.timeline.getPosToSelAction( true, true ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ), "postoselendc" );
        aMap.put( "postoselendc", doc.timeline.getPosToSelAction( false, true ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, InputEvent.ALT_MASK ), "postoselbeg" );
        aMap.put( "postoselbeg", doc.timeline.getPosToSelAction( true, false ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.ALT_MASK ), "postoselend" );
        aMap.put( "postoselend", doc.timeline.getPosToSelAction( false, false ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_M, 0 ), "dropmark" );
        aMap.put( "dropmark", new ActionDropMarker() );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB       , 0 ), "selnextreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LESS      , 0 ), "selnextreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_BACK_QUOTE, 0 ), "selnextreg" );
        aMap.put( "selnextreg", new ActionSelectRegion( SELECT_NEXT_REGION ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB       , InputEvent.ALT_MASK ), "selprevreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LESS      , InputEvent.CTRL_MASK), "selprevreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_MASK), "selprevreg" );
        aMap.put( "selprevreg", new ActionSelectRegion( SELECT_PREV_REGION ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB       , InputEvent.SHIFT_MASK), "extnextreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LESS      , InputEvent.SHIFT_MASK), "extnextreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_BACK_QUOTE, InputEvent.SHIFT_MASK), "extnextreg" );
        aMap.put( "extnextreg", new ActionSelectRegion( EXTEND_NEXT_REGION ));
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB       , InputEvent.ALT_MASK  + InputEvent.SHIFT_MASK ), "extprevreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LESS      , InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK ), "extprevreg" );
        iMap.put( KeyStroke.getKeyStroke( KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_MASK + InputEvent.SHIFT_MASK ), "extprevreg" );
        aMap.put( "extprevreg", new ActionSelectRegion( EXTEND_PREV_REGION ));

        setFocusTraversalKeysEnabled( false ); // we want the tab! we gotta have that tab! ouwe!

        setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );

        // ---- menus and actions ----
        mr = app.getMenuBarRoot();

        mr.putMimic( "file.new.fromSelection", this, actionNewFromSel );
        mr.putMimic( "file.close", this, actionClose );
        mr.putMimic( "file.save", this, actionSave );
        mr.putMimic( "file.saveAs", this, actionSaveAs );
        mr.putMimic( "file.saveCopyAs", this, actionSaveCopyAs);
        mr.putMimic( "file.saveSelectionAs", this, actionSaveSelectionAs );

        mr.putMimic( "edit.undo", this, doc.getUndoManager().getUndoAction() );
        mr.putMimic( "edit.redo", this, doc.getUndoManager().getRedoAction() );
        mr.putMimic( "edit.cut", this, doc.getCutAction() );
        mr.putMimic( "edit.copy", this, doc.getCopyAction() );
        mr.putMimic( "edit.paste", this, doc.getPasteAction() );
        mr.putMimic( "edit.clear", this, doc.getDeleteAction() );
        mr.putMimic( "edit.selectAll", this, actionSelectAll);

        mr.putMimic( "timeline.insertSilence", this, doc.getSilenceAction() );
        mr.putMimic( "timeline.insertRecording", this, actionInsertRec);
        mr.putMimic( "timeline.trimToSelection", this, doc.getTrimAction() );

        mr.putMimic( "process.again", this, actionProcessAgain );
        mr.putMimic( "process.fadeIn", this, actionFadeIn);
        mr.putMimic( "process.fadeOut", this, actionFadeOut);
        mr.putMimic( "process.gain", this, actionGain);
        mr.putMimic( "process.invert", this, actionInvert);
        mr.putMimic( "process.reverse", this, actionReverse);
        mr.putMimic( "process.rotateChannels", this, actionRotateChannels);
        mr.putMimic( "process.fscape.needlehole", this, actionFScNeedlehole);

        mr.putMimic( "debug.dumpRegions", this, actionDebugDump);
        mr.putMimic( "debug.verifyRegions", this, actionDebugVerify);

        updateEditEnabled( false );

        AbstractWindowHandler.setDeepFont( cp, Collections.singletonList( timeTB ));
        GUIUtil.setDeepFont( timeTB, app.getGraphicsHandler().getFont(GraphicsHandler.FONT_MINI));
        app.getMenuFactory().addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!

        init();
        updateTitle();
        documentUpdate();

        addDynamicListening( new DynamicPrefChangeManager( app.getUserPrefs(), new String[] {
            PrefsUtil.KEY_VIEWNULLLINIE, PrefsUtil.KEY_VIEWVERTICALRULERS, PrefsUtil.KEY_VIEWMARKERS,
            PrefsUtil.KEY_TIMEUNITS, PrefsUtil.KEY_VERTSCALE, PrefsUtil.KEY_VIEWCHANMETERS },
            this ));

        initBounds();	// be sure this is after documentUpdate!

        setVisible( true );
        toFront();
    }

    public void setLoop( boolean onOff ) {
        transTB.setLoop( onOff );
    }

    protected boolean alwaysPackSize()
    {
        return false;
    }

    /*
     */
    private void initBounds()
    {
        final Preferences			cp	= getClassPrefs();
        final BasicWindowHandler	bwh	= getWindowHandler();
        final Rectangle				sr	= bwh.getWindowSpace();
        final Dimension				dt	= stringToDimension( cp.get( KEY_TRACKSIZE, null ));
        final Dimension				d	= dt == null ? new Dimension() : dt;
        final float					hf	= (float) Math.sqrt( Math.max( 1, waveView.getNumChannels() ));
        final Dimension				winSize;
        final Rectangle				wr;
        int							w	= d.width;
        int							h	= d.height;
        sr.x		+= 36;
        sr.y		+= 36;
        sr.width	-= 60;
        sr.height	-= 60;
        if( w <= 0 ) {
            w = sr.width*2/3 - AudioTrackRowHeader.ROW_WIDTH;
        }
        if( h <= 0 ) {
            h = (sr.height - 106) / 4; // 106 = approx. extra space for title bar, tool bar etc.
        }
//System.out.println( "read KEY_TRACKSIZE : " + d );
//System.out.println( "w " + w + "; h " + h + "; hf " + hf );
        waveView.setPreferredSize( new Dimension( w, (int) (h * hf + 0.5f) ));
        pack();
        winSize = getSize();
        wr = new Rectangle( lastLeftTop.x + 21, lastLeftTop.y + 23,
                winSize.width, winSize.height );
        GUIUtil.wrapWindowBounds( wr, sr );
        lastLeftTop.setLocation( wr.getLocation() );
        setBounds( wr );
//System.out.println( "winSize " + winSize + "; wr " + wr );
        waveView.addComponentListener( new ComponentAdapter() {
            public void componentResized( ComponentEvent e )
            {
                if( waveExpanded ) {
                    final Dimension dNew = e.getComponent().getSize();
                    dNew.height = (int) (dNew.height / hf + 0.5f);
                    if( !dNew.equals( d )) {
//System.out.println( "write KEY_TRACKSIZE : " + dNew );
                        d.setSize( dNew );
                        cp.put( KEY_TRACKSIZE, AppWindow.dimensionToString( dNew ));
                    }
                }
            }
        });
    }

    protected void checkDecimatedTrails()
    {
        final DecimatedTrail dt;

        if( waveExpanded ) {
            if( verticalScale == PrefsUtil.VSCALE_FREQ_SPECT ) {
                if( doc.getDecimatedSonaTrail() == null ) {
                    try {
                        final DecimatedSonaTrail dst = doc.createDecimatedSonaTrail();
                        // set initial freq bounds of waveview
                        waveView.setFreqMinMax( dst.getMinFreq(), dst.getMaxFreq() );
                    }
                    catch( IOException e1 ) {
                        e1.printStackTrace();
                    }
                }
                dt = doc.getDecimatedSonaTrail();
            } else {
                if( doc.getDecimatedWaveTrail() == null ) {
                    try {
                        doc.createDecimatedWaveTrail();
                    }
                    catch( IOException e1 ) {
                        e1.printStackTrace();
                    }
                }
                dt = doc.getDecimatedWaveTrail();
            }
            if( dt != asyncTrail ) {
                if( asyncTrail != null ) asyncTrail.removeAsyncListener( this );
                asyncTrail = dt;
                if( asyncTrail != null ) asyncTrail.addAsyncListener( this );
            }
        }
    }

    public void addCatchBypass() { scroll.addCatchBypass(); }
    public void removeCatchBypass() { scroll.removeCatchBypass(); }

    public void repaintMarkers( Span affectedSpan )
    {
        if( !markVisible || !affectedSpan.touches( timelineVis )) return;

        final Span span	 = affectedSpan.shift( -timelineVis.start );
        final Rectangle updateRect = new Rectangle(
            (int) (span.start * vpScale), 0,
            (int) (span.getLength() * vpScale) + 2, wavePanel.getHeight() ).
                intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
        if( !updateRect.isEmpty() ) {
            // update markAxis in any case, even if it's invisible
            // coz otherwise the flag stakes are not updated!
            wavePanel.update( markAxis );
            wavePanel.repaint( updateRect );
        }
    }

    public void playerCreated( SuperColliderPlayer p )
    {
        lmm.setInputs( p.getInputBus() );
        lmm.addTaskSync( p.getInputSync() );
    }

    public void playerDestroyed( SuperColliderPlayer p )
    {
        lmm.clearInputs();
    }

    public void setSRCEnabled(boolean onOff) {
        lbSRC.setForeground(onOff ? Color.red : colrClear);
    }

    public void setForceMeters(boolean onOff) {
        if (onOff != forceMeters) {
            forceMeters = onOff;
            showHideMeters();
            final int holdDur = forceMeters ? -1 : PeakMeter.DEFAULT_HOLD_DUR;
            for (PeakMeter channelMeter : channelMeters) {
                channelMeter.setHoldDuration(holdDur);
            }
        }
    }

    public float getMaxMeterHold() {
        float hold = Float.NEGATIVE_INFINITY;
        for (PeakMeter channelMeter : channelMeters) {
            hold = Math.max(hold, channelMeter.getHoldDecibels());
        }
        return hold;
    }

    public void clearMeterHold() {
        for (PeakMeter channelMeter : channelMeters) {
            channelMeter.clearHold();
        }
    }

    private void showHideMeters() {
        final boolean visible = chanMeters || forceMeters;

        if (metersPanel.isVisible() != visible) {
            metersPanel.setVisible(visible);
        }
    }

    /**
     *  Recreates the main frame's title bar
     *  after a sessions name changed (clear/load/save as session)
     */
    public void updateTitle()
    {
//		final File				fDisp	= doc.getDisplayDescr().file;
        final AudioFileDescr[]	afds	= doc.getDescr();
        final String			name;
        final Icon				icn;
        final File              f;

        writeProtected	= false;

        f = afds.length == 0 ? null : afds[0].file;
        actionRevealFile.setFile(f);

        if( doc.getName() == null ) {
            name			= getResourceString( "frameUntitled" );
        } else {
            name			= doc.getName();
            try {
                for (AudioFileDescr afd : afds) {
                    final File f1 = afd.file;
                    if (f1 == null) continue;
                    writeProtected |= !f1.canWrite() || ((f1.getParentFile() != null) && !f1.getParentFile().canWrite());
                }
            } catch( SecurityException e ) { /* ignored */ }
        }

        if( writeProtected ) {
            icn = GUIUtil.getNoWriteIcon();
            if( lbWriteProtected.getIcon() != icn ) {
                lbWriteProtected.setIcon( icn );
            }
        } else if( lbWriteProtected.getIcon() != null ) {
            lbWriteProtected.setIcon( null );
        }
//		icnWriteProtected.setID( writeProtected ? MutableIcon.WRITE_PROTECTED : MutableIcon.INVISIBLE );
//		lbWriteProtected.repaint();

        if( internalFrames ) {
            if( doc.isDirty() ) {
                setTitle( "\u2022" + name );
            } else {
                setTitle( name );
            }
        } else {
            setTitle( app.getName() + (doc.isDirty() ? " - \u2022" : " - " ) + name );
            final Component c = getComponent();
            if (c instanceof JFrame) {
                final JFrame jf = (JFrame) c;
                jf.getRootPane().putClientProperty("Window.documentFile", f);
            }
        }
        actionShowWindow.putValue( Action.NAME, name );
        actionSave.setEnabled( !writeProtected && doc.isDirty() );
        setDirty( doc.isDirty() );

        final AudioFileInfoPalette infoBox = (AudioFileInfoPalette) app.getComponent( Main.COMP_AUDIOINFO );
        if( infoBox != null ) infoBox.updateDocumentName( doc );

        if( writeProtected && !wpHaveWarned && doc.isDirty() ) {
//			MutableIcon warnIcon = new MutableIcon( 128 );
//			warnIcon.setID( MutableIcon.WRITE_PROTECTED );
            final JOptionPane op = new JOptionPane( getResourceString( "warnWriteProtected" ), JOptionPane.WARNING_MESSAGE );
//			JOptionPane.showMessageDialog( getWindow(), getResourceString( "warnWriteProtected" ),
//				getResourceString( "msgDlgWarn" ), JOptionPane.WARNING_MESSAGE, null );
            BasicWindowHandler.showDialog( op, getWindow(), getResourceString( "msgDlgWarn" ));
            wpHaveWarned = true;
        }
    }

    // sync: attempts exclusive on MTE and shared on TIME!
    protected void updateOverviews( boolean justBecauseOfResize, boolean allTracks )
    {
//System.err.println( "update" );
        waveView.update( timelineVis );
        if( allTracks ) wavePanel.updateAll();
    }

    protected String getResourceString( String key )
    {
        return app.getResourceString( key );
    }

//	private BlendContext createBlendContext( long maxLength )
//	{
//		return BlendingAction.createBlendContext(
//			AbstractApplication.getApplication().getUserPrefs().node( BlendingAction.DEFAULT_NODE ),
//			timelineRate, maxLength );
//	}

    protected void documentUpdate()
    {
        final List<PeakMeter> collChannelMeters;
        PeakMeter[]				meters;
        AudioTrackRowHeader		chanHead;
        AudioTrack				t;
        int						oldChannels, newChannels;
        Axis					chanRuler;
        PeakMeter				chanMeter;

        newChannels = doc.getDisplayDescr().channels;
        oldChannels	= collChannelHeaders.size();

        meters				= channelMeters;
        collChannelMeters	= new ArrayList<PeakMeter>( meters.length );
        Collections.addAll(collChannelMeters, meters);

        // first kick out editors whose tracks have been removed
        for( int ch = 0; ch < oldChannels; ch++ ) {
            chanHead	= collChannelHeaders.get( ch );
            t			= (AudioTrack) chanHead.getTrack();
            if( !doc.audioTracks.contains( t )) {
                chanHead	= collChannelHeaders.remove( ch );
                chanMeter	= collChannelMeters.remove( ch );
                chanRuler	= collChannelRulers.remove( ch );
                oldChannels--;
                // XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
                flagsPanel.remove( chanHead );
                metersPanel.remove( chanMeter );
                rulersPanel.remove( chanRuler );
                ch--;
                chanHead.dispose();
                chanMeter.dispose();
                chanRuler.dispose();
            }
        }
        // next look for newly added transmitters and create editors for them
newLp:	for( int ch = 0; ch < newChannels; ch++ ) {
            t			= (AudioTrack) doc.audioTracks.get( ch );
            for( int ch2 = 0; ch2 < oldChannels; ch2++ ) {
                chanHead = collChannelHeaders.get( ch );
                if( chanHead.getTrack() == t ) continue newLp;
            }

            chanHead = new AudioTrackRowHeader( t, doc.tracks, doc.selectedTracks, doc.getUndoManager() );
            collChannelHeaders.add( chanHead );
            flagsPanel.add( chanHead, ch );

            chanMeter = new PeakMeter();
            collChannelMeters.add( chanMeter );
            metersPanel.add( chanMeter, ch );

            chanRuler = new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
            collChannelRulers.add( chanRuler );
            rulersPanel.add( chanRuler, ch );
        }

        meters	= new PeakMeter[ collChannelMeters.size() ];
        for( int ch = 0; ch < meters.length; ch++ ) {
            meters[ ch ] = collChannelMeters.get( ch );
        }
        channelMeters	= meters;
        lmm.setView( new PeakMeterGroup( meters ));

        updateOverviews( false, true );
    }

//	private void initStrip( Axis chanRuler, PeakMeter chanMeter )
//	{
//		final Preferences prefs = app.getUserPrefs();
//	
//		chanMeter.setVisible( prefs.getBoolean( PrefsUtil.KEY_VIEWCHANMETERS, false ));
//		chanRuler.setVisible( prefs.getBoolean( PrefsUtil.KEY_VIEWVERTICALRULERS, false ));
//	}

    public ProcessingThread closeDocument( boolean force, Flag wasClosed )
    {
        doc.getTransport().stop();
        if( !force ) {
            final String name = getResourceString( "menuClose" );
            if( !confirmCancel( name )) {
                wasClosed.set( false );
                return null;
            }
            final ProcessingThread pt = confirmUnsaved( name, wasClosed );
            if( pt != null ) {
                pt.addListener( new ProcessingThread.Listener() {
                    public void processStarted( ProcessingThread.Event e ) { /* ignored */ }
                    public void processStopped( ProcessingThread.Event e )
                    {
                        if( e.isDone() ) {
                            documentClosed();
                        }
                    }
                });
                return pt;
            }
        }
        if( wasClosed.isSet() ) {
            documentClosed();
        }
        return null;
    }

    protected void documentClosed()
    {
        disposed = true;	// important to avoid "too late window messages" to be processed; fucking swing doesn't kill them despite listener being removed
//System.err.println( "DocumentFrame.documentClosed()" );
        this.removeListener( winListener );
        actionShowWindow.dispose();
//		this.removeWindowFocusListener( winListener );	// otherwise we'll try to set an obsolete active doc
        app.getDocumentHandler().removeDocument( this, doc );	// invokes doc.dispose() and hence this.dispose()
//		actionShowWindow.removeAll();
    }

    public void dispose()
    {
        playTimer.stop();

        app.getMenuFactory().removeFromWindowMenu( actionShowWindow );

        AudioTrackRowHeader	chanHead;
        Axis				chanRuler;

        lmm.dispose();
        wavePanel.dispose();
        while( !collChannelHeaders.isEmpty() ) {
            chanHead = collChannelHeaders.remove( 0 );
            chanHead.dispose();
        }
        while( !collChannelRulers.isEmpty() ) {
            chanRuler = collChannelRulers.remove( 0 );
            chanRuler.dispose();
        }
        for (PeakMeter channelMeter : channelMeters) {
            channelMeter.dispose();
        }
        channelMeters = new PeakMeter[ 0 ];
        markAxis.stopListening();
        markAxis.dispose();
        timeAxis.dispose();
        timeTB.dispose();
        transTB.dispose();

        super.dispose();
    }

    private void updateEditEnabled( boolean enabled )
    {
        Action ma;
        ma			= doc.getCutAction();
        if( ma != null ) ma.setEnabled( enabled );
        ma			= doc.getCopyAction();
        if( ma != null ) ma.setEnabled( enabled );
        ma			= doc.getDeleteAction();
        if( ma != null ) ma.setEnabled( enabled );
        ma			= doc.getTrimAction();
        if( ma != null ) ma.setEnabled( enabled );
        actionProcess.setEnabled( enabled );
        actionNewFromSel.setEnabled( enabled );
        actionSaveSelectionAs.setEnabled( enabled );
    }

    @SuppressWarnings("serial")
    private static class SetFlagAndDisposeAction extends AbstractAction {
        private final Flag 		flag;
        private final JDialog	d;

        public SetFlagAndDisposeAction(Flag flag, JDialog d) {
            super();
            this.flag	= flag;
            this.d		= d;
        }

        public void actionPerformed(ActionEvent e) {
            flag.set(true);
            d.dispose();
        }
    }

    /*
     *  Checks if there are unsaved changes to
     *  the session. If so, displays a confirmation
     *  dialog. Invokes Save/Save As depending
     *  on user selection. IF the doc was not dirty,
     *	or if &quot;Cancel&quot; or
     *	&quot;Don't save&quot; was chosen, the
     *	method returns <code>null</code> and the
     *	<code>confirmed</code> flag reflects whether
     *	the document should be closed. If a saving
     *	process should be started, that process is
     *	returned. Note that the <code>ProcessingThread</code>
     *	in this case has not yet been started, as to
     *	allow interested objects to install a listener
     *	first. So it's their job to call the <code>start</code>
     *	method!
     *
     *  @param  actionName		name of the action that
     *							threatens the session
     *	@param	confirmed		a flag that will be set to <code>true</code> if
     *							the doc is allowed to be closed
     *							(doc was not dirty or user chose &quot;Don't save&quot;),
     *							otherwise <code>false</code> (save process
     *							initiated or user chose &quot;Cancel&quot;).
     *  @return					a saving process yet to be started or <code>null</code>
     *							if the doc needn't/shouldn't be saved
     *
     *	@see	de.sciss.eisenkraut.util.ProcessingThread#start
     */
    private ProcessingThread confirmUnsaved( String actionName, Flag confirmed )
    {
        if( !doc.isDirty() ) {
            confirmed.set( true );
            return null;
        }

        final Object[]			options	= { getResourceString( "buttonSave" ),
                                            getResourceString( "buttonCancel" ),
                                            getResourceString( "buttonDontSave" )};
        final int				choice;
//		final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
        final String			name;
        final JOptionPane		op;
        final JDialog 			d;
        final JRootPane			rp;
        final Flag				dont		= new Flag( false );
        AudioFileDescr[]		afds		= doc.getDescr();

        if( doc.getName() == null ) {
            name = getResourceString( "frameUntitled" );
        } else {
            name = doc.getName();
        }

//		choice = JOptionPane.showOptionDialog( getWindow(), name + " :\n" + getResourceString( "optionDlgUnsaved" ),
//											   actionName, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
//											   options, options[1] );
        op = new JOptionPane( name + " :\n" + getResourceString( "optionDlgUnsaved" ),
                              JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null,
                              options, options[ 1 ]);
        d = op.createDialog( getWindow(), actionName );
        rp = d.getRootPane();
        if( rp != null ) {
            rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, BasicMenuFactory.MENU_SHORTCUT), "dont");
            rp.getActionMap().put("dont", new SetFlagAndDisposeAction(dont, d));
        }
        BasicWindowHandler.showDialog(d);
        if (dont.isSet()) {
            choice = 2;
        } else {
            final Object value = op.getValue();
            if ((value == null) || (value == options[1])) {
                choice = 1;
            } else if (value == options[0]) {
                choice = 0;
            } else if (value == options[2]) {
                choice = 2;
            } else {
                choice = -1;    // throws assertion error in switch block
            }
        }

        switch( choice ) {
        case JOptionPane.CLOSED_OPTION:
        case 1:	// cancel
            confirmed.set( false );
            return null;

        case 2:	// don't save
            confirmed.set( true );
            return null;

        case 0:
            confirmed.set( false );
            if( (doc.getDisplayDescr().file == null) || writeProtected ) {
                afds = actionSaveAs.query( afds );
            }
            if( afds != null ) {
                return actionSave.initiate( actionSave.getValue( Action.NAME ).toString(), null, afds, null, true, false, false );
            }
            return null;

        default:
            assert false : choice;
            return null;
        }
    }

    private boolean confirmCancel( String actionName )
    {
        if( doc.checkProcess( 50 )) {
            return true;
        }

        final int				choice;
//		final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
        final String			name;

        if( doc.getName() == null ) {
            name = getResourceString( "frameUntitled" );
        } else {
            name = doc.getName();
        }

        final JOptionPane op = new JOptionPane( name + " :\n" + getResourceString( "optionDlgProcessing" ) +
                                                "\n(" + doc.getProcessName() + ")?", JOptionPane.WARNING_MESSAGE,
                                                JOptionPane.YES_NO_OPTION );
//		choice = JOptionPane.showConfirmDialog( getWindow(), name + " :\n" + getResourceString( "optionDlgProcessing" ) +
//		                                        "\n(" + doc.getProcessName() + ")?",
//											    actionName, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
        choice = BasicWindowHandler.showDialog( op, getWindow(), actionName );

        switch( choice ) {
        case JOptionPane.CLOSED_OPTION:
        case JOptionPane.NO_OPTION:
            return false;

        case JOptionPane.YES_OPTION:	// abort
            doc.cancelProcess( true );
            return true;

        default:
            assert false : choice;
            return false;
        }
    }

    /**
     *  Only call in the Swing thread!
     */
    protected void updatePositionAndRepaint()
    {
        boolean pEmpty, cEmpty;
        int		x, x2;

        pEmpty = (vpPositionRect.x + vpPositionRect.width < 0) || (vpPositionRect.x > vpRecentRect.width);
        if( !pEmpty ) vpUpdateRect.setBounds( vpPositionRect );

        if( vpScale > 0f ) {
            vpPosition	= (int) ((timelinePos - timelineVis.getStart()) * vpScale + 0.5f);
            // choose update rect such that even a paint manager delay of 200 milliseconds
            // will still catch the (then advanced) position so we don't see flickering!
            // XXX this should take playback rate into account, though
            vpPositionRect.setBounds( vpPosition, 0, Math.max( 1, (int) (vpScale * timelineRate * 0.2f) ), vpRecentRect.height );
        } else {
            vpPosition	= -1;
            vpPositionRect.setBounds( 0, 0, 0, 0 );
        }

        cEmpty = (vpPositionRect.x + vpPositionRect.width <= 0) || (vpPositionRect.x > vpRecentRect.width);
        if( pEmpty ) {
            if( cEmpty ) return;
            x   = Math.max( 0, vpPositionRect.x );
            x2  = Math.min( vpRecentRect.width, vpPositionRect.x + vpPositionRect.width );
            vpUpdateRect.setBounds( x, vpPositionRect.y, x2 - x, vpPositionRect.height );
        } else {
            if( cEmpty ) {
                x   = Math.max( 0, vpUpdateRect.x );
                x2  = Math.min( vpRecentRect.width, vpUpdateRect.x + vpUpdateRect.width );
                vpUpdateRect.setBounds( x, vpUpdateRect.y, x2 - x, vpUpdateRect.height );
            } else {
                x   = Math.max( 0, Math.min( vpUpdateRect.x, vpPositionRect.x ));
                x2  = Math.min( vpRecentRect.width, Math.max( vpUpdateRect.x + vpUpdateRect.width,
                                                            vpPositionRect.x + vpPositionRect.width ));
                vpUpdateRect.setBounds( x, vpUpdateRect.y, x2 - x, vpUpdateRect.height );
            }
        }
        if( !vpUpdateRect.isEmpty() ) {
            wavePanel.repaint( vpUpdateRect );
        }
    }

    /**
     *  Only call in the Swing thread!
     */
    protected void updateSelectionAndRepaint()
    {
        final Rectangle r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() );

        vpUpdateRect.setBounds( vpSelectionRect );
        recalcTransforms( r );
        updateSelection();
        if( vpUpdateRect.isEmpty() ) {
            vpUpdateRect.setBounds( vpSelectionRect );
        } else if( !vpSelectionRect.isEmpty() ) {
            vpUpdateRect = vpUpdateRect.union( vpSelectionRect );
        }
        vpUpdateRect = vpUpdateRect.intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
        if( !vpUpdateRect.isEmpty() ) {
            wavePanel.repaint( vpUpdateRect );
        }
    }

    /**
     *  Only call in the Swing thread!
     */
    private void updateTransformsAndRepaint( boolean verticalSelection )
    {
        final Rectangle r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() );

        vpUpdateRect = vpSelectionRect.union( vpPositionRect );
        recalcTransforms( r );
        if( verticalSelection ) updateSelection();
        vpUpdateRect = vpUpdateRect.union( vpPositionRect ).union( vpSelectionRect ).intersection( r );
        if( !vpUpdateRect.isEmpty() ) {
            wavePanel.repaint( vpUpdateRect );	// XXX ??
        }
    }

    protected void recalcTransforms( Rectangle newRect )
    {
        int x, w;

        vpRecentRect = newRect; // getViewRect();

        if( !timelineVis.isEmpty() ) {
            vpScale			= (float) vpRecentRect.width / (float) timelineVis.getLength(); // - 1;
            playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
            vpPosition		= (int) ((timelinePos - timelineVis.getStart()) * vpScale + 0.5f);
            vpPositionRect.setBounds( vpPosition, 0, 1, vpRecentRect.height );
            if( !timelineSel.isEmpty() ) {
                x			= (int) ((timelineSel.getStart() - timelineVis.getStart()) * vpScale + 0.5f) + vpRecentRect.x;
                w			= Math.max( 1, (int) ((timelineSel.getStop() - timelineVis.getStart()) * vpScale + 0.5f) - x );
                vpSelectionRect.setBounds( x, 0, w, vpRecentRect.height );
            } else {
                vpSelectionRect.setBounds( 0, 0, 0, 0 );
            }
        } else {
            vpScale			= 0.0f;
            vpPosition		= -1;
            vpPositionRect.setBounds( 0, 0, 0, 0 );
            vpSelectionRect.setBounds( 0, 0, 0, 0 );
        }
    }

    // sync: caller must sync on timeline + grp + tc
    private void updateSelection()
    {
        Rectangle	r;
        Track		t;
        int			x, y;

        vpSelections.clear();
        vpSelectionColors.clear();
        if( !timelineSel.isEmpty() ) {
            x			= waveView.getX();
            y			= waveView.getY();
            vpSelections.add( timeAxis.getBounds() );
            vpSelectionColors.add( colrSelection );
            t			= doc.markerTrack;
            vpSelections.add( markAxis.getBounds() );
            vpSelectionColors.add( doc.selectedTracks.contains( t ) ? colrSelection : colrSelection2 );
            for( int ch = 0; ch < waveView.getNumChannels(); ch++ ) {
                r		= new Rectangle( waveView.rectForChannel( ch ));
                r.translate( x, y );
                t		= (Track) doc.audioTracks.get( ch );
                vpSelections.add( r );
                vpSelectionColors.add( doc.selectedTracks.contains( t ) ? colrSelection : colrSelection2 );
            }
        }
    }

    protected void setZoomRect( Rectangle r )
    {
        vpZoomRect		= r;
        vpZoomStrokeIdx	= (vpZoomStrokeIdx + 1) % vpZoomStroke.length;

        wavePanel.repaint();
    }

// ------------- DecimatedTrail.AsyncListener interface -------------

    public void asyncFinished( DecimatedTrail.AsyncEvent e )
    {
        final DecimatedTrail dt = e.getDecimatedTrail();
        dt.removeAsyncListener( this );
        if( dt == asyncTrail ) asyncTrail = null;
        updateOverviews( false, true );
    }

    public void asyncUpdate( DecimatedTrail.AsyncEvent e )
    {
        updateOverviews( false, true );
    }

// ---------------- ProgressComponent interface ---------------- 

    public void addCancelListener( ActionListener l )
    {
        pProgress.addCancelListener( l );
    }

    public void removeCancelListener( ActionListener l )
    {
        pProgress.removeCancelListener( l );
    }

    public Component getComponent()
    {
        return getWindow();
    }

    public void resetProgression()
    {
        pProgress.resetProgression();
        pOverlay.performFade( 1f, 1000, 250 );
    }

    public void setProgression( float p )
    {
        pProgress.setProgression( p );
    }

    public void	finishProgression( int result )
    {
        if( result != CANCELLED ) pProgress.finishProgression( result );
        pOverlay.performFade( 0f, result == CANCELLED ? 0 : 4000, 250 );
    }

    public void setProgressionText( String text )
    {
        pProgress.setProgressionText( text );
    }

    public void showMessage( int type, String text )
    {
        pProgress.showMessage( type, text );
    }

    public void displayError(Exception e, String processName) {
        BasicWindowHandler.showErrorDialog(getWindow(), e, processName);
    }

    protected void updateAFDGadget() {
        final AudioFileDescr displayAFD = doc.getDisplayDescr();
        final AudioFileDescr[] afds = doc.getDescr();
        displayAFD.rate = timelineRate;
        displayAFD.length = timelineLen;
        for (AudioFileDescr afd : afds) {
            afd.rate = displayAFD.rate;
            afd.length = displayAFD.length;
        }
        ggAudioFileDescr.setText(displayAFD.getFormat());

        pOverlay.performFade(0f, 1000, 250);
    }

    protected void updateCursorFormat() {
        final AudioFileDescr displayAFD = doc.getDisplayDescr();
        csrInfoBits 	= displayAFD.bitsPerSample;
        csrInfoIsInt 	= displayAFD.sampleFormat == AudioFileDescr.FORMAT_INT;
    }

    protected void updateVerticalRuler()
    {
        final VectorSpace	spc;
        final float			min, max;
        Axis				chanRuler;

        switch( waveView.getVerticalScale() ) {
        case PrefsUtil.VSCALE_AMP_LIN:
            min = waveView.getAmpLinMin() * 100;
            max = waveView.getAmpLinMax() * 100;
            spc = VectorSpace.createLinSpace( 0.0, 1.0, min, max, null, null, null, null );
            break;
        case PrefsUtil.VSCALE_AMP_LOG:
            min = waveView.getAmpLogMin();
            max = waveView.getAmpLogMax();
            spc = VectorSpace.createLinSpace( 0.0, 1.0, min, max, null, null, null, null );
            break;
        case PrefsUtil.VSCALE_FREQ_SPECT:
            min = waveView.getFreqMin();
            max = waveView.getFreqMax();
            spc = VectorSpace.createLinLogSpace( 0.0, 1.0, min, max, Math.sqrt( min * max ), null, null, null, null );
            break;
        default:
            assert false : waveView.getVerticalScale();
            spc = null;
        }

        for (Axis collChannelRuler : collChannelRulers) {
            chanRuler = collChannelRuler;
            chanRuler.setSpace(spc);
        }
    }

// ---------------- TimelineListener interface ---------------- 

    public void timelineSelected( TimelineEvent e )
    {
        final boolean	wasEmpty = timelineSel.isEmpty();
        final boolean	isEmpty;

        timelineSel	= doc.timeline.getSelectionSpan();

        updateSelectionAndRepaint();
        isEmpty	= timelineSel.isEmpty();
        if( wasEmpty != isEmpty ) {
            updateEditEnabled( !isEmpty );
        }
    }

    // warning : don't call doc.setAudioFileDescr, it will restore the old markers!
    public void timelineChanged( TimelineEvent e )
    {
        timelineRate				= doc.timeline.getRate();
        timelineLen					= doc.timeline.getLength();
        playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
        updateAFDGadget();
        updateOverviews( false, true );
    }

    public void timelinePositioned( TimelineEvent e )
    {
        timelinePos = doc.timeline.getPosition();

        updatePositionAndRepaint();
        scroll.setPosition( timelinePos, 0, pointerTool.validDrag ?
            TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
    }

    public void timelineScrolled( TimelineEvent e )
    {
//System.out.println( "scrolled " + doc.timeline.getVisibleSpan() );
        timelineVis	= doc.timeline.getVisibleSpan();

        updateOverviews( false, true );
        updateTransformsAndRepaint( false );
    }

// ---------------- TransportListener interface ---------------- 

    public void transportPlay( Transport t, long pos, double rate )
    {
        playRate = rate;
        playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
        playTimer.restart();
    }

    public void transportStop( Transport t, long pos )
    {
        playTimer.stop();
    }

    public void transportPosition( Transport t, long pos, double rate ) { /* ignored */ }
    public void transportReadjust( Transport t, long pos, double rate ) { /* ignored */ }

    public void transportQuit( Transport t )
    {
        playTimer.stop();
    }

// ---------------- RealtimeConsumer interface ---------------- 

//	/**
//	 *  Requests 30 fps notification (no data block requests).
//	 *  This is used to update the timeline position during transport
//	 *  playback.
//	 */
//	public RealtimeConsumerRequest createRequest( RealtimeContext context )
//	{
//		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
//		// 30 fps is visually fluent
//		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 30 );
//		request.notifyTicks		= true;
//		request.notifyOffhand	= true;
//		return request;
//	}
//	
//	public void realtimeTick( RealtimeContext context, long timelinePos )
//	{
//		this.timelinePos = timelinePos;
//updatePositionAndRepaint();
//		scroll.setPosition( timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
//	}
//
//	public void offhandTick( RealtimeContext context, long timelinePos )
//	{
//	}

// ---------------- ToolListener interface ---------------- 

    // sync: attemptShared DOOR_TRNS
    public void toolChanged(ToolActionEvent e) {
        if (activeTool != null) {
            activeTool.toolDismissed(waveView);
        }

        activeTool = tools.get(e.getToolAction().getID());
        if (activeTool != null) {
            waveView.setCursor(e.getToolAction().getDefaultCursor());
            activeTool.toolAcquired(waveView);
        } else {
            waveView.setCursor(null);
        }
    }

// ---------------- PreferenceChangeListener interface ---------------- 

    public void preferenceChange(PreferenceChangeEvent e) {
        final String key = e.getKey();

        if (key.equals(PrefsUtil.KEY_VIEWNULLLINIE)) {
            waveView.setNullLinie(e.getNode().getBoolean(e.getKey(), false));
        } else if (key.equals(PrefsUtil.KEY_VIEWVERTICALRULERS)) {
            final boolean visible = e.getNode().getBoolean(e.getKey(), false);
            rulersPanel.setVisible(visible);
        } else if (key.equals(PrefsUtil.KEY_VIEWCHANMETERS)) {
            chanMeters = e.getNode().getBoolean(e.getKey(), false);
            showHideMeters();
        } else if (key.equals(PrefsUtil.KEY_VIEWMARKERS)) {
            viewMarkers = e.getNode().getBoolean(e.getKey(), false);
            markVisible = viewMarkers && waveExpanded;
            if (waveExpanded) {
                markAxis.setVisible(markVisible);
                markAxisHeader.setVisible(markVisible);
                wavePanel.updateAll();
            }
            if (markVisible) {
                markAxis.startListening();
            } else {
                markAxis.stopListening();
            }

        } else if (key.equals(PrefsUtil.KEY_TIMEUNITS)) {
            final boolean timeSmps = e.getNode().getInt(key, PrefsUtil.TIME_SAMPLES) == PrefsUtil.TIME_SAMPLES;
            msgCsr1.applyPattern(timeSmps ? smpPtrn : timePtrn);
        } else if (key.equals(PrefsUtil.KEY_VERTSCALE)) {
            verticalScale = e.getNode().getInt(key, PrefsUtil.VSCALE_AMP_LIN);
            checkDecimatedTrails(); // needs to be before setVert.scale / updateRuler!

            waveView.setVerticalScale(verticalScale);
            updateVerticalRuler();
        }
    }

// ---------------- ClipboardOwner interface ---------------- 

    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // XXX possibly call dispose()
    }

// ---------------- internal action classes ---------------- 

    @SuppressWarnings("serial")
    private class ActionDebugDump
            extends MenuAction {
        protected ActionDebugDump() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            System.err.println( "------------ Document: "+doc.getDisplayDescr().file+" ------------" );
            doc.getAudioTrail().debugDump();
            System.err.println( "   --------- decimated ---------" );
            doc.getDecimatedWaveTrail().debugDump();
        }
    }

    @SuppressWarnings("serial")
    private class ActionDebugVerify
            extends MenuAction {
        protected ActionDebugVerify() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            System.err.println( "------------ Document: " + doc.getDisplayDescr().file + " ------------" );
            doc.getAudioTrail().debugVerifyContiguity();
            System.err.println("   --------- decimated ---------");
            doc.getDecimatedWaveTrail().debugVerifyContiguity();
        }
    }

    @SuppressWarnings("serial")
    private class ActionNewFromSel
            extends MenuAction {
        protected ActionNewFromSel() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            final ClipboardTrackList	tl		= doc.getSelectionAsTrackList();
            final Session				doc2;
            final AudioFileDescr		afd, afd2;
            final int					selChans;
            final ProcessingThread		pt;

            if( tl == null ) return;

            selChans			= tl.getTrackNum( AudioTrail.class );

            afd					= doc.getDisplayDescr();
            afd2				= new AudioFileDescr();
            afd2.bitsPerSample	= afd.bitsPerSample;
            afd2.channels		= selChans;
            afd2.rate			= afd.rate;
            afd2.sampleFormat	= afd.sampleFormat;

            doc2				= ((MenuFactory) app.getMenuFactory()).newDocument( afd2 );
            if( doc2 == null ) {
                // it's important that the clipboard tl be diposed
                // when not used any more
                tl.dispose();
                return;
            }

            pt = doc2.pasteTrackList( tl, 0, getResourceString( "menuPaste" ), Session.EDIT_INSERT );
            if( pt != null ) {
                pt.addListener( new ProcessingThread.Listener() {
                    public void processStarted( ProcessingThread.Event e1 ) { /* ignored */ }
                    public void processStopped( ProcessingThread.Event e2 ) {
                        tl.dispose();
                    }
                });
                doc.start( pt );
            } else {
                tl.dispose();
            }
        }
    } // actionNewFromSelClass

    // action for the Save-Session menu item
    @SuppressWarnings("serial")
    private class ActionClose
            extends MenuAction {
        protected ActionClose() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }

        public void perform()
        {
            final ProcessingThread pt = closeDocument( false, new Flag( false ));
            if( pt != null ) doc.start( pt );
        }
    }

    // action for the Save-Session menu item
    @SuppressWarnings("serial")
    private class ActionSave
            extends MenuAction {
        protected ActionSave() { /* empty */ }

        /**
         *  Saves a Session. If the file
         *  wasn't saved before, a file chooser
         *  is shown before.
         */
        public void actionPerformed( ActionEvent e )
        {
            final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
            final AudioFileDescr[]	afds;

            if( displayAFD.file == null ) {
                afds = actionSaveAs.query( doc.getDescr() );
            } else {
                afds = doc.getDescr();
            }
            if( afds != null ) {
                perform( getValue( NAME ).toString(), afds );
            }
        }

        protected void perform( String name, AudioFileDescr[] afds )
        {
            perform( name, null, afds, null, true, false, false );
        }

        protected void perform( String name, Span span, AudioFileDescr[] afds,
                                int[] channelMap, boolean saveMarkers, boolean asCopy, boolean openAfterSave )
        {
            final ProcessingThread pt = initiate( name, span, afds, channelMap, saveMarkers, asCopy, openAfterSave );
            if( pt != null ) doc.start( pt );
        }

        protected ProcessingThread initiate( String name, final Span span, final AudioFileDescr[] afds,
                                             int[] channelMap, boolean saveMarkers, final boolean asCopy, final boolean openAfterSave )
        {
            final ProcessingThread pt = doc.procSave( name, span, afds, channelMap, saveMarkers, asCopy );
            if( pt == null ) return null;

            pt.addListener( new ProcessingThread.Listener() {
                public void processStopped( ProcessingThread.Event e )
                {
                    if( !e.isDone() ) return;

                    wpHaveWarned = false;

                    if( !asCopy ) {
                        if( afds.length == 1 ) app.getMenuFactory().addRecent( afds[ 0 ].file );
                        updateAFDGadget();
                        updateCursorFormat();
                    }
                    if( openAfterSave ) {
                        if( afds.length == 1 ) {
                            app.getMenuFactory().openDocument( afds[ 0 ].file );
                        } else {
                            final File[] fs = new File[ afds.length ];
                            for( int i = 0; i < afds.length; i++ ) {
                                fs[ i ] = afds[ i ].file;
                            }
                            ((MenuFactory) app.getMenuFactory()).openDocument( fs );
                        }
                    }
                }

                public void processStarted( ProcessingThread.Event e ) { /* ignored */ }
            });
            return pt;
        }
    }

    // action for the Save-Session-As menu item
    @SuppressWarnings("serial")
    private class ActionSaveAs
            extends MenuAction {
        private final boolean	asCopy;
        private final boolean	selection;
        private final Flag		openAfterSave;

        protected ActionSaveAs( boolean asCopy, boolean selection )
        {
            if( selection && !asCopy ) throw new IllegalArgumentException();

            this.asCopy		= asCopy;
            this.selection	= selection;
            openAfterSave	= new Flag( false );
        }

        /*
         *  Query a file name from the user and save the Session
         */
        public void actionPerformed( ActionEvent e )
        {
            final List<Track.Info>	infos = Track.getInfos( doc.selectedTracks.getAll(), doc.tracks.getAll() );
            boolean		saveMarkers	= true;
            int[] 		channelMap	= null;
            if( selection ) {
                for (Object info : infos) {
                    Track.Info ti = (Track.Info) info;
                    if (ti.trail instanceof AudioTrail) {
                        int numSelChannels = 0;
                        for (int j = 0; j < ti.trackMap.length; j++) {
                            if (ti.trackMap[j]) numSelChannels++;
                        }
                        channelMap = new int[numSelChannels];
                        for (int j = 0, k = 0; j < ti.trackMap.length; j++) {
                            if (ti.trackMap[j]) channelMap[k++] = j;
                        }
                    } else if (ti.trail instanceof MarkerTrail) {
                        saveMarkers = ti.selected;
                    }
                }
            }
            final AudioFileDescr[] afds = query( doc.getDescr(), channelMap, saveMarkers, asCopy, selection, openAfterSave );
            if( afds != null ) {
                actionSave.perform( getValue( NAME ).toString(), selection ? timelineSel : null, afds, channelMap, saveMarkers, asCopy, openAfterSave.isSet() );
            }
        }

        protected AudioFileDescr[] query( AudioFileDescr[] protoType )
        {
            return query( protoType, null, true, false, false, null );
        }

        /**
         *  Open a file chooser so the user
         *  can select a new output file and format for the session.
         *
         *  @return the AudioFileDescr representing the chosen file path
         *			and format or <code>null</code>
         *			if the dialog was cancelled.
         */
        protected AudioFileDescr[] query( AudioFileDescr[] protoType, int[] channelMap, boolean saveMarkers,
                                          boolean asCopySettings, boolean selectionSettings, Flag openAfterSaveSettings )
        {
            if( protoType.length == 0 ) return null;

//			final FileDialog			fDlg;
            final AudioFileDescr[]		afds;
            final AudioFileFormatPane	affp;
//			final JOptionPane			dlg;
            final SpringPanel			msgPane;
            final PathField[]			ggPathFields;
            final int[]					channelsUsed	= new int[ protoType.length ];
            final JCheckBox				ggOpenAfterSave;
            final String				prefsDirKey		= selectionSettings ? PrefsUtil.KEY_FILESAVESELDIR : PrefsUtil.KEY_FILESAVEDIR;
            final JPanel				p;
            int							filesUsed		= 0;
            File						f; // , f2;
            String[]					queryOptions	= { getResourceString( "buttonSave" ),
                                                            getResourceString( "buttonCancel" )};
            int							i, result;
            String						str;
            JLabel						lb;
            String						fileName, dirName;
            boolean						setFocus		= false;
            int							y				= 0;

//System.out.print( "channelMap = [ " );
//for( int kkk = 0; kkk < channelMap.length; kkk++ ) System.out.print( (kkk > 0 ? ", " : "") + channelMap[ kkk ]);
//System.out.println( " ]" );

            msgPane			= new SpringPanel( 4, 2, 4, 2 );
            ggPathFields	= new PathField[ protoType.length ];
            affp			= new AudioFileFormatPane( AudioFileFormatPane.FORMAT | AudioFileFormatPane.ENCODING );
            affp.fromDescr( protoType[0] );
            lb				= new JLabel( getResourceString( "labelOutputFile" ), RIGHT );
//			lb.setLabelFor( ggPathField );
            msgPane.gridAdd( lb, 0, y );
            for( int j = 0, chanOff = 0; j < protoType.length; chanOff += protoType[ j ].channels, j++, y++ ) {
                if( channelMap == null ) {
                    channelsUsed[ j ] = protoType[ j ].channels;
                } else {
                    for (int aChannelMap : channelMap) {
                        if ((aChannelMap >= chanOff) && (aChannelMap < chanOff + protoType[j].channels)) {
                            channelsUsed[j]++;
                        }
                    }
                }
//System.out.println( "channelsUsed[ " + j + " ] = " + channelsUsed[ j ]);
                if( channelsUsed[ j ] == 0 ) continue;

                filesUsed++;
                ggPathFields[ j ] = new PathField( PathField.TYPE_OUTPUTFILE, getValue( NAME ).toString() );
                if( protoType[ j ].file == null ) {
                    fileName	= getResourceString( "frameUntitled" ) + (ggPathFields.length > 1 ? "-" + (j+1) : "");
                } else if( asCopySettings || selectionSettings ) {
                    str	= protoType[ j ].file.getName();
                    i	= str.lastIndexOf( '.' );
                    if( i == -1 ) i = str.length();
                    fileName = str.substring( 0, i ) + (selectionSettings ? getResourceString( "fileDlgCut" ) : " " + getResourceString( "fileDlgCopy" )); // suffix is appended by affp!
                } else {
                    fileName = protoType[ j ].file.getName();
                }
                dirName = app.getUserPrefs().get( prefsDirKey, protoType[ j ].file == null ? System.getProperty( "user.home" ) : protoType[ j ].file.getParent() );
                ggPathFields[ j ].setPath( new File( dirName, fileName ));
                affp.automaticFileSuffix( ggPathFields[ j ] );
                if( (protoType[ j ].file == null) || asCopySettings || selectionSettings ) {	// create non-existent file name
                    ggPathFields[ j ].setPath( IOUtil.nonExistentFileVariant( ggPathFields[ j ].getPath(), -1,
                        selectionSettings ? null : " ", null ));
                }
                ggPathFields[ j ].selectFileName( false );
                msgPane.gridAdd( ggPathFields[ j ], 1, y );
                if( !setFocus ) {
                    GUIUtil.setInitialDialogFocus( ggPathFields[ j ]);
                    setFocus = true;
                }
            }
            lb = new JLabel( getResourceString( "labelFormat" ), RIGHT );
            msgPane.gridAdd( lb, 0, y );
            msgPane.gridAdd( affp, 1, y, -1, 1 );
            lb.setLabelFor( affp );
            y++;

            if( asCopySettings ) {
                ggOpenAfterSave = new JCheckBox( getResourceString( "labelOpenAfterSave" ));
                ggOpenAfterSave.setSelected( openAfterSaveSettings.isSet() );
                msgPane.gridAdd( ggOpenAfterSave, 1, y );
            } else {
                ggOpenAfterSave	= null;
//				msgPane.gridAdd( new JLabel( " " ), 1, y );
            }

            AbstractWindowHandler.setDeepFont( msgPane );
            msgPane.makeCompactGrid();
            p			= new JPanel( new BorderLayout() );
            p.add( msgPane, BorderLayout.NORTH );

            final JOptionPane op = new JOptionPane( p, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, queryOptions, queryOptions[ 0 ]);
//			result		= JOptionPane.showOptionDialog( getWindow(), p, getValue( NAME ).toString(),
//														JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
//														null, queryOptions, queryOptions[ 0 ]);
            result = BasicWindowHandler.showDialog( op, getWindow(), getValue( NAME ).toString() );

            if( ggOpenAfterSave != null ) {
                openAfterSaveSettings.set( ggOpenAfterSave.isSelected() );
            }

            if( result == 0 ) {
                // save dir prefs
                if( ggPathFields.length > 0 ) {
                    app.getUserPrefs().put( prefsDirKey, ggPathFields[ 0 ].getPath().getParent() );
                }

                afds = new AudioFileDescr[ filesUsed ];
                for( int j = 0, k = 0; j < ggPathFields.length; j++ ) {
                    if( channelsUsed[ j ] == 0 ) continue;
                    f = ggPathFields[ j ].getPath();
                    if( f.exists() ) {
                        queryOptions = new String[] { getResourceString( "buttonOverwrite" ),
                                                      getResourceString( "buttonCancel" )};
                        final JOptionPane op2 = new JOptionPane( getResourceString( "warnFileExists" ) +
                                                                ":\n" + f.getAbsolutePath() + "\n" + getResourceString( "warnOverwriteFile" ),
                                                                JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION,
                                                                null, queryOptions, queryOptions[1] );
//						result = JOptionPane.showOptionDialog( getWindow(), getResourceString( "warnFileExists" ) +
//							":\n" + f.getAbsolutePath() + "\n" + getResourceString( "warnOverwriteFile" ),
//							getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
//							null, queryOptions, queryOptions[1] );
                        result = BasicWindowHandler.showDialog( op2, getWindow(), getValue( NAME ).toString() );
                        if( result != 0 ) return null;
                    }
                    afds[ k ]		= new AudioFileDescr( protoType[ j ]);
                    affp.toDescr( afds[ k ]);
                    afds[ k ].file	= f;
                    afds[ k ].channels = channelsUsed[ j ];
                    k++;
                }
                return afds;
            } else {
                return null;
            }
        }
    }

//	private class actionImportMarkersClass
//	extends MenuAction
//	{
//		public void actionPerformed( ActionEvent e )
//		{
//			new ImportMarkersDialog( doc );
//		}
//	}

    @SuppressWarnings("serial")
    private class ActionSelectAll
            extends MenuAction {
        protected ActionSelectAll() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            doc.timeline.editSelect( this, new Span( 0, timelineLen ));
        }
    }

    @SuppressWarnings("serial")
    private class ActionInsertRec
            extends MenuAction {
        protected ActionInsertRec() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            final RecorderDialog		recDlg;
            final File					recFile;
            final Session				tmpDoc;
            final ClipboardTrackList	tl;
            final ProcessingThread		pt;
            final Session				targetDoc;
            final AudioFileDescr		afd, afd2;

            try {
                recDlg	= new RecorderDialog( doc );
            }
            catch( IOException e1 ) {
                BasicWindowHandler.showErrorDialog( getWindow(), e1, getValue( NAME ).toString() );
                return;
            }
            recFile	= recDlg.getResult();

            if( recFile != null ) {
                try {
//					if( true ) throw new IOException( "test" );
                    tmpDoc	= Session.newFrom( recFile, false, false );
                }
                catch( IOException e1 ) {
                    GUIUtil.displayError( getWindow(), e1, getValue( NAME ).toString() );
                    confirmDelete( recFile );
                    return;
                }
                tl		= new ClipboardTrackList( tmpDoc, new Span( 0, tmpDoc.timeline.getLength() ), tmpDoc.tracks.getAll() );
                if( doc.checkProcess()) {
                    targetDoc			= doc;
                } else {	// doc busy, save the recording by creating a separate document
                    afd					= doc.getDisplayDescr();
                    afd2				= new AudioFileDescr();
                    afd2.bitsPerSample	= afd.bitsPerSample;
                    afd2.channels		= afd.channels;
                    afd2.rate			= afd.rate;
                    afd2.sampleFormat	= afd.sampleFormat;
                    targetDoc			= ((MenuFactory) app.getMenuFactory()).newDocument( afd2 );
                }
//pt = null;
                pt = targetDoc == null ? null : targetDoc.pasteTrackList( tl, targetDoc.timeline.getPosition(), getValue( NAME ).toString(), targetDoc.getEditMode() );

                if( pt != null ) {
                    pt.addListener( new ProcessingThread.Listener() {
                        public void processStarted( ProcessingThread.Event e1 ) { /* ignored */ }
                        public void processStopped( ProcessingThread.Event e2 )
                        {
                            tl.dispose();
                            tmpDoc.dispose();
                            if( e2.isDone() ) {
                                deleteFile( recFile );
                            } else {
                                confirmDelete( recFile );
                            }
                        }
                    });
                    targetDoc.start( pt );
                } else {
                    tl.dispose();
                    tmpDoc.dispose();
                    confirmDelete( recFile );
                }
            }
        }

        protected void confirmDelete( File path )
        {
            final int		choice;
            final Object[]	options	= new String[] { getResourceString( "buttonKeepFile" ), getResourceString( "buttonDeleteFile" )};
            final JOptionPane op = new JOptionPane( getResourceString( "optionDlgKeepRec1" ) + path.getAbsolutePath() + getResourceString( "optionDlgKeepRec2" ),
                                                    JOptionPane.ERROR_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, options[ 0 ]);
//			choice = JOptionPane.showOptionDialog( getWindow(), getResourceString( "optionDlgKeepRec1" ) + path.getAbsolutePath() + getResourceString( "optionDlgKeepRec2" ),
//					   getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null,
//					   options, options[ 0 ]);
            choice = BasicWindowHandler.showDialog( op, getWindow(), getValue( NAME ).toString() );
            if( choice == 1 ) {
                deleteFile( path );
            }
        }

        protected void deleteFile( File path )
        {
            if( !path.delete() ) {
                final JOptionPane op = new JOptionPane( path.getAbsolutePath() + ":\n" + getResourceString( "errDeleteFile" ), JOptionPane.WARNING_MESSAGE );
//				JOptionPane.showMessageDialog( getWindow(), path.getAbsolutePath() + ":\n" + getResourceString( "errDeleteFile" ), getValue( NAME ).toString(), 
//					JOptionPane.WARNING_MESSAGE );
                BasicWindowHandler.showDialog( op, getWindow(), getValue( NAME ).toString() );
            }
        }
    } // class actionInsertRecClass

    @SuppressWarnings("serial")
    private class ActionProcess
            extends MenuAction {
        protected ActionProcess() { /* empty */ }

        public void actionPerformed( ActionEvent e ) { /* empty */ }
    }

    @SuppressWarnings("serial")
    private class ActionPlugIn
            extends MenuAction {
        private final String plugInClassName;

        protected ActionPlugIn( String plugInClassName )
        {
            this.plugInClassName	= plugInClassName;
        }

        public void actionPerformed( ActionEvent e )
        {
            FilterDialog filterDlg = (FilterDialog) app.getComponent( Main.COMP_FILTER );

            if( filterDlg == null ) {
                filterDlg = new FilterDialog();
            }
            filterDlg.process( plugInClassName, doc, (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0, false );
            actionProcessAgain.setPlugIn( filterDlg.getPlugIn() );
        }
    }

    @SuppressWarnings("serial")
    private class ActionProcessAgain
            extends MenuAction {
        private String plugInClassName = null;

        protected ActionProcessAgain()
        {
            super();
            setEnabled( false );
        }

        public void actionPerformed( ActionEvent e )
        {
            if( plugInClassName == null ) return;

            FilterDialog filterDlg = (FilterDialog) app.getComponent( Main.COMP_FILTER );

            if( filterDlg == null ) {
                filterDlg = new FilterDialog();
            }
            filterDlg.process( plugInClassName, doc, false, true );
        }

        protected void setPlugIn( RenderPlugIn plugIn )
        {
            if( plugIn == null ) {
                if( isEnabled() ) {
                    setEnabled( false );
                    putValue( NAME, getResourceString( "menuProcessAgain" ));
                }
                plugInClassName = null;
            } else {
                if( !isEnabled() ) {
                    setEnabled( true );
                }
                putValue( NAME, getResourceString( "menuProcessAgain" ) + " : " + plugIn.getName() );
                plugInClassName = plugIn.getClass().getName();
            }
        }
    }

    @SuppressWarnings("serial")
    private class ActionAudioInfo
            extends MenuAction {
        protected ActionAudioInfo() { /* empty */ }

        /**
         *  Brings up the Audio-Info-Box
         */
        public void actionPerformed( ActionEvent e )
        {
            AudioFileInfoPalette infoBox = (AudioFileInfoPalette) app.getComponent( Main.COMP_AUDIOINFO );

            if( infoBox == null ) {
                infoBox = new AudioFileInfoPalette();
            }
            infoBox.setVisible( true );
            infoBox.toFront();
        }
    }

    @SuppressWarnings("serial")
    private class ActionRevealFile extends MenuAction {
        private File f;

        protected ActionRevealFile() {
            super("Reveal File in Finder");
            setFile(null);
        }

        /**
         *  Shows the file in the desktop manager (Mac and Linux only)
         */
        public void actionPerformed(ActionEvent e) {
            if (f == null) return;
            if      (Main.isMac  ) performMac  ();
            else if (Main.isLinux) performLinux();
        }

        /* Show parent directory, that's as much as we can do I think... */
        private void performLinux() {
            final String dir = f.getParent();
            if (dir == null) return;

            final String[] cmdArray = { "xdg-open", dir };
            try {
                Runtime.getRuntime().exec( cmdArray, null, null );
            } catch (IOException e1) {
                displayError(e1, getValue(NAME).toString());
            }
        }

        // osascript -e 'tell application "Finder"' -e 'activate' -e 'open location "file:///Volumes/Claude/audio"'
        //   -e 'select file "Sine441HzGain.aif" of folder of the front window'
        //   -e 'end tell'

        private void performMac() {
            try {
                // File.toURL() ends up with sth. like "file:/Volumes/Claude/..." omitting the two initial slashes
//				final String[] cmdArray = { "osascript", "-e", "tell application \"Finder\"", "-e", "activate",
//											"-e", "open location \"" + f.getAbsoluteFile().toURL().toString() + "\"",
//											"-e", "end tell" };
                // make sure space characters are escaped as %20 in URL stylee
//				final Normalizer n		= new Normalizer( Normalizer.C, false );
//				final String parentDir	= n.normalize( f.getParentFile().getAbsolutePath() ).replaceAll( " ", "%20" );
                String path				= f.getParentFile().getAbsoluteFile().toURI().toURL().toExternalForm();
//				path = n.normalize(path);
//				String path				= n.normalize( f.getAbsoluteFile().toURL().toExternalForm() ); // getAbsolutePath() ).replaceAll( " ", "%20" );
                path					= path.substring( 5 );
StringBuilder sb = new StringBuilder();
//char ch;
int  chI;
byte[] hex = "0123456789abcdef".getBytes();
byte[] enc = path.getBytes( "UTF-8" );
                for (byte anEnc : enc) {
                    chI = anEnc; // parentDir.charAt( i );
//	chI = (int) ch;
                    if ((chI < 33) || (chI > 127)) {
                        sb.append("%").append((char) hex[(chI >> 4) & 0x0F]).append((char) hex[chI & 0x0F]);
                    } else {
                        sb.append((char) chI);
                    }
                }
path = sb.toString();
//int i = path.lastIndexOf( '/' ) + 1;
final String parentDir = path;
final String fileName = f.getName(); // n.normalize( f.getName() ); // .getBytes( "ISO-8859-1" ));
//final String parentDir = path.substring( 0, i );
//final String fileName = path.substring( i );
//System.err.println( "'" + parentDir + "'" );				
//System.err.println( "'" + fileName + "'" );
                final String[] cmdArray = { "osascript", "-e", "tell application \"Finder\"", "-e", "activate",
                                            "-e", "open location \"file://" + parentDir + "\"",
//											"-e", "select location \"file://" + parentDir + "\"",
//											"-e", "select file \"" + f.getName() + "\" of folder of the front window",
//											"-e", "select location \"" + fileName + "\" of folder of the front window",
                                            "-e", "select file \"" + fileName + "\" of folder of the front window",
                                            "-e", "end tell" };
//for( int i = 0; i < cmdArray.length; i++ ) {
//	System.err.println( "#" + cmdArray[i] + "#" );
//}

                Runtime.getRuntime().exec( cmdArray, null, null );
            }
            catch( IOException e1 ) {
                displayError( e1, getValue( NAME ).toString() );
            }
        }

        protected void setFile( File f )
        {
            this.f	= f;
            setEnabled( f != null );
        }
    }

//	private abstract class ActionVerticalZoom
//	extends AbstractAction
//	{
//		protected ActionVerticalZoom() { /* empty */ }
//	}

    /**
     *  Increase or decrease the vertical
     *  range of the waveform display
     */
    @SuppressWarnings("serial")
    private class ActionVerticalMax
            extends AbstractAction {
        private final float linFactor;
        private final float logOffset;

        /**
         *  @param  linFactor  factors > 1 increase the row height,
         *					factors < 1 decrease.
         */
        protected ActionVerticalMax( float linFactor, float logOffset )
        {
            super();
            this.linFactor = linFactor;
            this.logOffset = logOffset;
        }

        public void actionPerformed( ActionEvent e )
        {
            if( waveView.getVerticalScale() == PrefsUtil.VSCALE_AMP_LIN ) zoomLin(); else zoomLog();
        }

        private void zoomLin()
        {
            float min, max;

            min = waveView.getAmpLinMin();
            max = waveView.getAmpLinMax();

            if( ((linFactor >= 1.0f) && (min > -1.0e6f) && (max < 1.0e6f)) || (linFactor < 1.0f && (min < -1.0e-4f) && (max > 1.0e-4f)) ) {
                min	   *= linFactor;
                max	   *= linFactor;
                waveView.setAmpLinMinMax( min, max );
                updateVerticalRuler();
            }
        }

        private void zoomLog()
        {
            float min, max;

            min = waveView.getAmpLogMin();
            max = waveView.getAmpLogMax();

            if( (max + logOffset - min >= 6f) &&
                (((logOffset >= 0f) && (max < 60)) || (logOffset < 0f && (max > -160))) ) {
//				min	   += logOffset;
                max	   += logOffset;
                waveView.setAmpLogMinMax( min, max );
                updateVerticalRuler();
            }
        }
    } // class actionVerticalMax

    /**
     *  Increase or decrease the vertical
     *  noisefloor of the waveform display (in log mode)
     */
    @SuppressWarnings("serial")
    private class ActionVerticalMin
            extends AbstractAction {
        private final float logOffset;

        /**
         */
        protected ActionVerticalMin( float logOffset )
        {
            super();
            this.logOffset = logOffset;
        }

        public void actionPerformed( ActionEvent e )
        {
            if( waveView.getVerticalScale() != PrefsUtil.VSCALE_AMP_LIN ) zoomLog();
        }

        private void zoomLog()
        {
            float min, max;

            min = waveView.getAmpLogMin();
            max = waveView.getAmpLogMax();

            if( (max - (min + logOffset) >= 6f) &&
                (((logOffset >= 0f) && (min < 60)) || (logOffset < 0f && (max > -160))) ) {
                min	   += logOffset;
//				max	   += logOffset;
                waveView.setAmpLogMinMax( min, max );
                updateVerticalRuler();
            }
        }
    } // class actionVerticalMin

    /**
     *  Increase or decrease the width
     *  of the visible time span
     */
    @SuppressWarnings("serial")
    private class ActionSpanWidth
            extends AbstractAction {
        private final float factor;

        /**
         *  @param  factor  factors > 1 increase the span width (zoom out)
         *					factors < 1 decrease (zoom in).
         *					special value 0.0 means zoom to sample level
         */
        protected ActionSpanWidth( float factor )
        {
            super();
            this.factor = factor;
        }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }

        public void perform()
        {
            long	pos, visiLen, start, stop;
            Span	visiSpan;

            visiSpan	= timelineVis;
            visiLen		= visiSpan.getLength();
            pos			= timelinePos; // doc.timeline.getPosition();
            if( factor == 0.0f ) {				// to sample level
                start	= Math.max( 0, pos - (wavePanel.getWidth() >> 1) );
                stop	= Math.min( timelineLen, start + wavePanel.getWidth() );
            } else if( factor < 1.0f ) {		// zoom in
                if( visiLen < 4 ) return;
                // if timeline pos visible -> try to keep it's relative position constant
                if( visiSpan.contains( pos )) {
                    start	= pos - (long) ((pos - visiSpan.getStart()) * factor + 0.5f);
                    stop    = start + (long) (visiLen * factor + 0.5f);
                // if timeline pos before visible span, zoom left hand
                } else if( visiSpan.getStart() > pos ) {
                    start	= visiSpan.getStart();
                    stop    = start + (long) (visiLen * factor + 0.5f);
                // if timeline pos after visible span, zoom right hand
                } else {
                    stop	= visiSpan.getStop();
                    start   = stop - (long) (visiLen * factor + 0.5f);
                }
            } else {			// zoom out
                start   = Math.max( 0, visiSpan.getStart() - (long) (visiLen * factor/4 + 0.5f) );
                stop    = Math.min( timelineLen, start + (long) (visiLen * factor + 0.5f) );
            }
            visiSpan	= new Span( start, stop );
            if( !visiSpan.isEmpty() ) {
                doc.timeline.editScroll( this, visiSpan );
            }
        }
    } // class actionSpanWidthClass

    private static final int SCROLL_SESSION_START	= 0;
    private static final int SCROLL_SELECTION_START	= 1;
    private static final int SCROLL_SELECTION_STOP	= 2;
    private static final int SCROLL_FIT_TO_SELECTION= 3;
    private static final int SCROLL_ENTIRE_SESSION	= 4;

    @SuppressWarnings("serial")
    private class ActionScroll
            extends AbstractAction {
        private final int mode;

        protected ActionScroll( int mode )
        {
            super();

            this.mode = mode;
        }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }

        public void perform()
        {
            UndoableEdit	edit	= null;
            Span			selSpan, newSpan;
            long			start, stop;

            if( mode == SCROLL_SESSION_START && transport.isRunning() ) {
                transport.stop();
            }
            selSpan		= timelineSel; // doc.timeline.getSelectionSpan();

            switch( mode ) {
            case SCROLL_SESSION_START:
                if( timelinePos != 0 ) {
                    edit	= TimelineVisualEdit.position( this, doc, 0 ).perform();
                    if( !timelineVis.contains( 0 )) {
                        final CompoundEdit ce	= new BasicCompoundEdit();
                        ce.addEdit( edit );
                        newSpan	= new Span( 0, timelineVis.getLength() );
                        ce.addEdit( TimelineVisualEdit.scroll( this, doc, newSpan ).perform() );
                        ce.end();
                        edit	= ce;
                    }
                }
                break;

            case SCROLL_SELECTION_START:
                if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
                if( timelineVis.contains( selSpan.getStart() )) {
                    start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 1) );
                } else {
                    start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 3) );
                }
                stop	= Math.min( timelineLen, start + timelineVis.getLength() );
                newSpan	= new Span( start, stop );
                if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
                    edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
                }
                break;

            case SCROLL_SELECTION_STOP:
                if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
                if( timelineVis.contains( selSpan.getStop() )) {
                    stop = Math.min( timelineLen, selSpan.getStop() + (timelineVis.getLength() >> 1) );
                } else {
                    stop = Math.min( timelineLen, selSpan.getStop() + (timelineVis.getLength() >> 3) );
                }
                start	= Math.max( 0, stop - timelineVis.getLength() );
                newSpan	= new Span( start, stop );
                if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
                    edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
                }
                break;

            case SCROLL_FIT_TO_SELECTION:
                newSpan		= selSpan;
                if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
                    edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
                }
                break;

            case SCROLL_ENTIRE_SESSION:
                newSpan		= new Span( 0, timelineLen );
                if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
                    edit	= TimelineVisualEdit.scroll( this, doc, newSpan ).perform();
                }
                break;

            default:
                assert false : mode;
                break;
            }
            if( edit != null ) doc.getUndoManager().addEdit( edit );
        }
    } // class actionScrollClass

    private static final int SELECT_TO_SESSION_START	= 0;
    private static final int SELECT_TO_SESSION_END		= 1;

    @SuppressWarnings("serial")
    private class ActionSelect
            extends AbstractAction {
        private final int mode;

        protected ActionSelect( int mode )
        {
            super();

            this.mode = mode;
        }

        public void actionPerformed( ActionEvent e )
        {
            Span			selSpan, newSpan = null;

            selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
            if( selSpan.isEmpty() ) {
                selSpan	= new Span( timelinePos, timelinePos );
            }

            switch( mode ) {
            case SELECT_TO_SESSION_START:
                if( selSpan.getStop() > 0 ){
                    newSpan = new Span( 0, selSpan.getStop() );
                }
                break;

            case SELECT_TO_SESSION_END:
                if( selSpan.getStart() < timelineLen ){
                    newSpan = new Span( selSpan.getStart(), timelineLen );
                }
                break;

            default:
                assert false : mode;
                break;
            }
            if( newSpan != null && !newSpan.equals( selSpan )) {
                doc.timeline.editSelect( this, newSpan );
//					doc.getUndoManager().addEdit( TimelineVisualEdit.select( this, doc, newSpan ));
            }
        }
    } // class actionSelectClass

    private static final int SELECT_NEXT_REGION	= 0;
    private static final int SELECT_PREV_REGION	= 1;
    private static final int EXTEND_NEXT_REGION	= 2;
    private static final int EXTEND_PREV_REGION	= 3;

    @SuppressWarnings("serial")
    private class ActionSelectRegion
            extends AbstractAction {
        private final int mode;

        protected ActionSelectRegion( int mode )
        {
            super();

            this.mode = mode;
        }

        public void actionPerformed( ActionEvent e )
        {
            Span			selSpan;
            UndoableEdit	edit;
            long			start, stop;
            Marker			mark;
            int				idx;

            if( !markVisible ) return;

            selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
            if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );

            start		= selSpan.getStart();
            stop		= selSpan.getStop();

            switch( mode ) {
            case SELECT_NEXT_REGION:
            case EXTEND_NEXT_REGION:
                idx		= doc.markers.indexOf( stop + 1 );	// XXX check
                if( idx < 0 ) idx = -(idx + 1);

                if(	idx == doc.markers.getNumStakes() ) {
                    stop	= timelineLen;
                } else {
                    mark	= doc.markers.get( idx );
                    stop	= mark.pos;
                }
                // (-(insertion point) - 1)

                if( mode == SELECT_NEXT_REGION ) {
                    idx		= doc.markers.indexOf( stop - 1 );	// XXX check
                    if( idx < 0 ) idx = -(idx + 2);

                    if( idx == -1 ) {
                        start	= 0;
                    } else {
                        mark	= doc.markers.get( idx );
                        start	= mark.pos;
                    }
                }
                break;

            case SELECT_PREV_REGION:
            case EXTEND_PREV_REGION:
                idx		= doc.markers.indexOf( start - 1 );	// XXX check
                if( idx < 0 ) idx = -(idx + 2);

                if(	idx == -1 ) {
                    start	= 0;
                } else {
                    mark	= doc.markers.get( idx );
                    start	= mark.pos;
                }

                if( mode == SELECT_PREV_REGION ) {
                    idx		= doc.markers.indexOf( start + 1 );	// XXX check
                    if( idx < 0 ) idx = -(idx + 1);

                    if( idx == doc.markers.getNumStakes() ) {
                        stop	= timelineLen;
                    } else {
                        mark	= doc.markers.get( idx );
                        stop	= mark.pos;
                    }
                }
                break;

            default:
                assert false : mode;
                break;
            }

            if( (start == selSpan.getStart()) && (stop == selSpan.getStop()) ) return;

            edit	= TimelineVisualEdit.select( this, doc, new Span( start, stop )).perform();
            doc.getUndoManager().addEdit( edit );
        }
    } // class actionSelectRegionClass

    @SuppressWarnings("serial")
    private class ActionDropMarker
            extends AbstractAction {
        protected ActionDropMarker() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            if( markVisible ) {
                markAxis.addMarker( timelinePos );
            }
        }
    } // class actionDropMarkerClass

// -------------- AFR Transfer Handler --------------

    @SuppressWarnings("serial")
    private class AFRTransferHandler
            extends TransferHandler {
        protected AFRTransferHandler() { /* empty */ }

        public int getSourceActions( JComponent c )
        {
            return COPY;
        }

        protected Transferable createTransferable( JComponent c )
        {
//			return new StringSelection( doc.getAudioFileDescr().file.getAbsolutePath() + File.pathSeparator +
//				doc.timeline.getSelectionSpan().getStart() + File.pathSeparator +
//				doc.timeline.getSelectionSpan().getStop() );
            return new StringSelection( doc.getDisplayDescr().file.getAbsolutePath() + File.pathSeparator +
                doc.timeline.getSelectionSpan().getStart() + File.pathSeparator +
                doc.timeline.getSelectionSpan().getStop() );

//			System.err.println( "createTransferable" );
//			return new AudioFileRegion( new File( "haschimoto" ), new Span( 42, 43 ));
//		
//			final Span					span;
//			final AudioTrail	mte;
//
//			if( !doc.bird.attemptShared( Session.DOOR_TIME | Session.DOOR_MTE, 250 )) return null;
//			try {
//				span = timelineSel; // doc.timeline.getSelectionSpan();
//				if( span.isEmpty() ) return null;
//
//				mte = doc.getAudioTrail();
//				return mte.getSampledChunkList( span );
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIME | Session.DOOR_MTE );
//			}
        }

        protected void exportDone( JComponent source, Transferable data, int action )
        {
             /* ignored */
        }

//		public boolean canImport( JComponent c, DataFlavor[] flavors )
//		{
//			return false;
//		}
    }

    private abstract class TimelineTool
    extends AbstractTool
    {
        private final List<Component> collObservedComponents	= new ArrayList<Component>();

        private boolean adjustCatchBypass	= false;

        protected TimelineTool() { /* empty */ }

        public void toolAcquired( Component c )
        {
            super.toolAcquired( c );

            if( c instanceof Container ) addMouseListeners( (Container) c );
        }

        // additionally installs mouse input listeners on child components
        private void addMouseListeners(Container c) {
            Component c2;

            for (int i = 0; i < c.getComponentCount(); i++) {
                c2 = c.getComponent(i);
                collObservedComponents.add(c2);
                c2.addMouseListener(this);
                c2.addMouseMotionListener(this);
                if (c2 instanceof Container) addMouseListeners((Container) c2);    // recurse
            }
        }

        // additionally removes mouse input listeners from child components
        private void removeMouseListeners() {
            Component c;

            while (!collObservedComponents.isEmpty()) {
                c = collObservedComponents.remove(0);
                c.removeMouseListener(this);
                c.removeMouseMotionListener(this);
            }
        }

        public void toolDismissed(Component c) {
            super.toolDismissed(c);

            removeMouseListeners();

            if (adjustCatchBypass) {
                adjustCatchBypass = false;
                removeCatchBypass();
            }
        }

        public void mousePressed( MouseEvent e )
        {
            adjustCatchBypass = true;
            addCatchBypass();

            super.mousePressed( e );
        }

        public void mouseReleased( MouseEvent e )
        {
            adjustCatchBypass = false;
            removeCatchBypass();

            super.mouseReleased( e );
        }
    }

    /*
     *	Keyboard modifiers are consistent with Bias Peak:
     *	Shift+Click = extend selection, Meta+Click = select all,
     *	Alt+Drag = drag timeline position; double-click = Play
     */
    private class TimelinePointerTool
    extends TimelineTool
    {
        private boolean shiftDrag, ctrlDrag, dragStarted = false;
        protected boolean validDrag = false;
        private long startPos;
        private int startX;

        private final Object[] argsCsr	= new Object[8];
        private final String[] csrInfo	= new String[3];

        protected TimelinePointerTool() { /* empty */ }

        public void paintOnTop( Graphics2D g )
        {
            // not necessary
        }

        protected void cancelGesture()
        {
            dragStarted = false;
            validDrag	= false;
        }

        public void mousePressed( MouseEvent e )
        {
            super.mousePressed( e );

            if( e.isMetaDown() ) {
                selectRegion( e );
                dragStarted = false;
                validDrag	= false;
            } else {
                shiftDrag	= e.isShiftDown();
                ctrlDrag	= e.isControlDown();
                dragStarted = false;
                validDrag	= true;
                startX		= e.getX();
                processDrag( e, false );
            }
        }

        public void mouseDragged( MouseEvent e )
        {
            final ObserverPalette observer;

            super.mouseDragged( e );

            if( validDrag ) {
                if( !dragStarted ) {
                    if( shiftDrag || ctrlDrag || Math.abs( e.getX() - startX ) > 2 ) {
                        dragStarted = true;
                    } else return;
                }
                processDrag( e, true );
            }

            // cursor information
            observer = (ObserverPalette) app.getComponent( Main.COMP_OBSERVER );
            if( (observer != null) && observer.isVisible() && (observer.getShownTab() == ObserverPalette.CURSOR_TAB) ) {
                showCursorInfo( SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), waveView ));
            }
        }

        private void showCursorInfo( Point screenPt )
        {
            final ObserverPalette	observer;

            final int				ch		= waveView.channelForPoint( screenPt );
            if( ch == -1 ) return;

            final DecimationInfo	info	= waveView.getDecimationInfo();
            if( info == null ) return;

            final long				pos		= timelineVis.getStart() + (long)
                                        ((double) screenPt.x / (double) waveView.getWidth() *
                                         timelineVis.getLength());
            if( (pos < 0) || (pos >= timelineLen) ) return;

            final String			chName	= doc.audioTracks.get( ch ).getName();
            final double			seconds	= pos / timelineRate;
            final AudioTrail 		at;
            final DecimatedWaveTrail	dt;
            final float[][]			data;
            final float[]			frame;
            float					f1;

            argsCsr[3]		= chName;
            argsCsr[0]		= pos;
            argsCsr[1]		= (int) (seconds / 60);
            argsCsr[2]		= (float) (seconds % 60);

            csrInfo[0]		= msgCsr1.format( argsCsr );

            switch( info.model ) {
            case DecimatedTrail.MODEL_PCM:
                at			= doc.getAudioTrail();
                data		= new float[ at.getChannelNum() ][];
                data[ ch ]	= new float[ 1 ];
                try {
                    at.readFrames( data, 0, new Span( pos, pos + 1 ));
                }
                catch( IOException e1 ) { return; }
                f1			= data[ ch ][ 0 ];
                argsCsr[4] = f1;
                argsCsr[5] = (float) (Math.log(Math.abs(f1)) * TWENTYDIVLOG10);
                csrInfo[1] = msgCsr2PCMFloat.format(argsCsr);
                if (csrInfoIsInt) {
                    argsCsr[6] = (long) (f1 * (1L << (csrInfoBits - 1)));
                    argsCsr[7] = csrInfoBits;
                    csrInfo[2] = msgCsr3PCMInt.format(argsCsr);
                } else {
                    csrInfo[2] = "";
                }
                break;

            case DecimatedTrail.MODEL_FULLWAVE_PEAKRMS:
                dt			= doc.getDecimatedWaveTrail();
                if( dt == null ) return;
                frame		= new float[ dt.getNumModelChannels() ];
                try {
                    dt.readFrame( Math.min( dt.getNumDecimations() - 1, info.idx + 1 ), pos, ch, frame );
                }
                catch( IOException e1 ) { return; }
                f1			= Math.max( frame[ 0 ], -frame[ 1 ] );	// peak pos/neg
                argsCsr[4]	= f1;
                argsCsr[5]	= new Float( Math.log( f1 ) * TWENTYDIVLOG10 );
                f1			= (float) Math.sqrt( frame[ 2 ]);	// mean sqr pos/neg
                argsCsr[6]	= f1;
                argsCsr[7]	= (float) (Math.log(f1) * TWENTYDIVLOG10);
                csrInfo[1]	= msgCsr2Peak.format( argsCsr );
                csrInfo[2]	= msgCsr3RMS.format( argsCsr );
                break;

            default:
                return;
            }

            observer = (ObserverPalette) app.getComponent( Main.COMP_OBSERVER );
            if( observer != null ) observer.showCursorInfo( csrInfo );
        }

        private void selectRegion( MouseEvent e )
        {
            final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );

            Span			span, span2;
            long			pos, start, stop;
            UndoableEdit	edit;
            int				idx;
            Marker			mark;

            span        = timelineVis; // doc.timeline.getVisibleSpan();
            span2		= timelineSel; // doc.timeline.getSelectionSpan();
            pos			= span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
                                                    span.getLength());
            pos			= Math.max( 0, Math.min( timelineLen, pos ));

            stop		= timelineLen;
            start		= 0;

            if( markVisible ) {
                idx		= doc.markers.indexOf( pos + 1 );	// XXX check
                if( idx < 0 ) idx = -(idx + 1);
                if(	idx < doc.markers.getNumStakes() ) {
                    mark	= doc.markers.get( idx );
                    stop	= mark.pos;
                }
                idx		= doc.markers.indexOf( stop - 1 );	// XXX check
                if( idx < 0 ) idx = -(idx + 2);
                if( idx >= 0 ) {
                    mark	= doc.markers.get( idx );
                    start	= mark.pos;
                }
            }

            // union with current selection
            if( e.isShiftDown() && !span2.isEmpty() ) {
                start	= Math.min( start, span2.start );
                stop	= Math.max( stop, span2.stop );
            }

            span	= new Span( start, stop );
            if( span.equals( span2 )) {
                span	= new Span( 0, timelineLen );
            }
            if( !span.equals( span2 )) {
                edit = TimelineVisualEdit.select( this, doc, span ).perform();
                doc.getUndoManager().addEdit( edit );
            }
        }

        private void processDrag( MouseEvent e, boolean hasStarted )
        {
            final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );

            Span			span, span2;
            long			position;
            UndoableEdit	edit;

            span        = timelineVis; // doc.timeline.getVisibleSpan();
            span2		= timelineSel; // doc.timeline.getSelectionSpan();
            position    = span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
                                                    span.getLength());
            position    = Math.max( 0, Math.min( timelineLen, position ));
            if( !hasStarted && !ctrlDrag ) {
                if( shiftDrag ) {
                    if( span2.isEmpty() ) {
                        span2 = new Span( timelinePos, timelinePos );
                    }
                    startPos = Math.abs( span2.getStart() - position ) >
                               Math.abs( span2.getStop() - position ) ?
                                    span2.getStart() : span2.getStop();
                    span2	= new Span( Math.min( startPos, position ),
                                        Math.max( startPos, position ));
                    edit	= TimelineVisualEdit.select( this, doc, span2 ).perform();
                } else {
                    startPos = position;
                    if( span2.isEmpty() ) {
                        edit = TimelineVisualEdit.position( this, doc, position ).perform();
                    } else {
                        edit = new CompoundEdit();
                        edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ).perform() );
                        edit.addEdit( TimelineVisualEdit.position( this, doc, position ).perform() );
                        ((CompoundEdit) edit).end();
                    }
                }
            } else {
                if( ctrlDrag ) {
                    edit	= TimelineVisualEdit.position( this, doc, position ).perform();
                } else {
                    span2	= new Span( Math.min( startPos, position ),
                                        Math.max( startPos, position ));
                    edit	= TimelineVisualEdit.select( this, doc, span2 ).perform();
                }
            }
            doc.getUndoManager().addEdit( edit );
        }

        public void mouseReleased( MouseEvent e )
        {
            super.mouseReleased( e );

            Span span2;

            // resets the position to selection start if (and only if) the selection was
            // made anew, ctrl key is not pressed and transport is not running
            if( dragStarted && !shiftDrag && !ctrlDrag && !transport.isRunning() ) {
                span2 = timelineSel; // doc.timeline.getSelectionSpan();
                if( !span2.isEmpty() && timelinePos != span2.getStart() ) {
                    doc.timeline.editPosition( this, span2.getStart() );
                }
            }

            dragStarted = false;
            validDrag	= false;
        }

        public void mouseClicked( MouseEvent e )
        {
            super.mouseClicked( e );

            if( (e.getClickCount() == 2) && !e.isMetaDown() && !transport.isRunning() ) {
                transport.play( 1.0f );
            }
        }

        // on Mac, Ctrl+Click is interpreted as
        // popup trigger by the system which means
        // no successive mouseDragged calls are made,
        // instead mouseMoved is called ...
        public void mouseMoved( MouseEvent e )
        {
            super.mouseMoved( e );

            mouseDragged( e );
        }
    }

    @SuppressWarnings("serial")
    private static class SetCursorAction extends MenuAction {
        private final Component c;
        private final Cursor	csr;

        public SetCursorAction(String name, KeyStroke stroke, Component c, Cursor csr) {
            super(name, stroke);
            this.c 		= c;
            this.csr	= csr;
        }

        public void actionPerformed(ActionEvent e) {
            c.setCursor(zoomCsr[1]);
        }
    }

    private class TimelineZoomTool
    extends TimelineTool
    {
        private boolean					validDrag	= false, dragStarted = false;
        private long					startPos;
        private Point					startPt;
        private long					position;
        private final javax.swing.Timer	zoomTimer;
        protected final Rectangle		zoomRect	= new Rectangle();
        private MenuAction actionZoomIn		= null;
        private MenuAction actionZoomOut	= null;

        protected TimelineZoomTool()
        {
            zoomTimer = new javax.swing.Timer( 250, new ActionListener() {
                public void actionPerformed( ActionEvent e )
                {
                    setZoomRect( zoomRect );
                }
            });
        }

        public void toolAcquired(final Component c) {
            super.toolAcquired(c);
            c.setCursor(zoomCsr[0]);
            if (c instanceof JComponent) {
                final JComponent jc = (JComponent) c;
                if (actionZoomOut == null) actionZoomOut = new SetCursorAction("zoomOut",
                        KeyStroke.getKeyStroke(KeyEvent.VK_ALT, InputEvent.ALT_DOWN_MASK, false), c, zoomCsr[1]);
                if (actionZoomIn == null) actionZoomIn = new SetCursorAction("zoomIn",
                        KeyStroke.getKeyStroke(KeyEvent.VK_ALT, 0, true), c, zoomCsr[0]);
                actionZoomOut.installOn(jc, JComponent.WHEN_IN_FOCUSED_WINDOW);
                actionZoomIn .installOn(jc, JComponent.WHEN_IN_FOCUSED_WINDOW);
            }
        }

        public void toolDismissed( Component c )
        {
            super.toolDismissed( c );
            if( c instanceof JComponent ) {
                final JComponent jc = (JComponent) c;
                if( actionZoomOut != null ) actionZoomOut.deinstallFrom( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
                if( actionZoomIn != null ) actionZoomIn.deinstallFrom( jc, JComponent.WHEN_IN_FOCUSED_WINDOW );
            }
        }

        public void paintOnTop( Graphics2D g )
        {
            // not necessary
        }

        public void mousePressed( MouseEvent e )
        {
            super.mousePressed( e );

            if( e.isAltDown() ) {
                dragStarted = false;
                validDrag	= false;
                clickZoom( 2.0f, e );
            } else {
                dragStarted = false;
                validDrag	= true;
                processDrag( e, false );
            }
        }

        public void mouseDragged( MouseEvent e )
        {
            super.mouseDragged( e );

            if( validDrag ) {
                if( !dragStarted ) {
                    if( Math.abs( e.getX() - startPt.x ) > 2 ) {
                        dragStarted = true;
                        zoomTimer.restart();
                    } else return;
                }
                processDrag( e, true );
            }
        }

        protected void cancelGesture()
        {
            zoomTimer.stop();
            setZoomRect( null );
            dragStarted = false;
            validDrag	= false;
        }

        public void mouseReleased( MouseEvent e )
        {
            super.mouseReleased( e );

            Span span;

            if( dragStarted ) {
                cancelGesture();
                span = new Span( Math.min( startPos, position ),
                                 Math.max( startPos, position ));
                if( !span.isEmpty() ) {
                    doc.timeline.editScroll( this, span );
                }
            }

            validDrag	= false;
        }

        // zoom to mouse position
        public void mouseClicked(MouseEvent e) {
            super.mouseClicked(e);

            if (!e.isAltDown()) clickZoom(0.5f, e);
        }

        private void clickZoom(float factor, MouseEvent e) {
            long pos, visibleLen, start, stop;
            Span visibleSpan;

            visibleSpan = timelineVis;
            visibleLen 	= visibleSpan.getLength();
            pos 		= visibleSpan.getStart() + (long) ((double) e.getX() / (double) getComponent().getWidth() *
                                visibleSpan.getLength());
            visibleLen 	= (long) (visibleLen * factor + 0.5f);
            if (visibleLen < 2) return;

            start		= Math.max( 0, Math.min( timelineLen, pos - (long) ((pos - visibleSpan.getStart()) * factor + 0.5f) ));
            stop		= start + visibleLen;
            if( stop > timelineLen ) {
                stop	= timelineLen;
                start	= Math.max( 0, stop - visibleLen );
            }
            visibleSpan	= new Span( start, stop );
            if( !visibleSpan.isEmpty() ) {
                doc.timeline.editScroll( this, visibleSpan );
            }
        }

        private void processDrag( MouseEvent e, boolean hasStarted )
        {
            final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );

            Span	span;
            int		zoomX;

            span        = timelineVis;
            position    = span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
                                                    span.getLength());
            position    = Math.max( 0, Math.min( timelineLen, position ));
            if( !hasStarted ) {
                startPos= position;
                startPt	= pt;
            } else {
                zoomX	= Math.min( startPt.x, pt.x );
                zoomRect.setBounds( zoomX, waveView.getY() + 6, Math.abs( startPt.x - pt.x ), waveView.getHeight() - 12 );
                setZoomRect( zoomRect );
            }
        }
    }
}