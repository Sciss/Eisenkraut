/*
 *  DocumentFrame.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2007 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *      25-Jan-05	created from de.sciss.meloncillo.timeline.TimelineFrame
 *		22-Mar-05	fixed but in vertical zoom
 *		15-Jul-05	lot's of. removed some exclusive locks (fixes overview update in catch mode)
 *					; overview loading loads one more sample frame, so display
 *					is in coherence with timeline selection and TimelineAxis
 *		02-Aug-05	renamed to DocumentFrame ; conforms to new document handler
 *		23-Sep-05	correctly disposes overview display images ; abandons backing store
 *					image approach in TimelineViewport (seems to fix memory leak)
 */

package de.sciss.eisenkraut.session;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.text.MessageFormat;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.MouseInputAdapter;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

import de.sciss.eisenkraut.*;
import de.sciss.eisenkraut.edit.*;
import de.sciss.eisenkraut.gui.*;
import de.sciss.eisenkraut.io.*;
import de.sciss.eisenkraut.net.*;
import de.sciss.eisenkraut.realtime.*;
import de.sciss.eisenkraut.render.*;
import de.sciss.eisenkraut.timeline.*;
import de.sciss.eisenkraut.util.*;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.GraphicsHandler;
import de.sciss.app.LaterInvocationManager;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.common.ShowWindowAction;
import de.sciss.gui.*;
import de.sciss.io.*;
import de.sciss.timebased.*;
import de.sciss.util.*;

import org.unicode.Normalizer;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 06-Nov-07
 */
public class DocumentFrame
extends AppWindow
implements	ProgressComponent, TimelineListener,	// SessionChangeListener
			ClipboardOwner, ToolActionListener,	// RealtimeConsumer
			// ServerListener,	// SessionCollection.Listener, MeterListener
			DecimatedTrail.AsyncListener,
			TransportListener
{
	private final Session					doc;
	
    private final TimelineAxis				timeAxis;
    private final MarkerAxis				markAxis;
	private final TrackRowHeader			markAxisHeader;
	private final TimelineScroll			scroll;
	private final Transport					transport;
	
	private Span							timelineSel;
	private Span							timelineVis;
	private long							timelinePos;
	private long							timelineLen;
	private double							timelineRate;

	private final JPanel					ggTrackPanel;
	private final WaveformView				waveView;
	private final ComponentHost				wavePanel;
//	private final SpringPanel				waveHeaderView;
	private final JPanel					waveHeaderPanel;
	private final JPanel					channelHeaderPanel;
	private final JPanel					flagsPanel;
	private final JPanel					rulersPanel;
	private final JPanel					metersPanel;
	private final java.util.List			collChannelHeaders		= new ArrayList();
	private final java.util.List			collChannelRulers		= new ArrayList();
//	private final java.util.List			collChannelMeters		= new ArrayList();
	private LevelMeter[]					channelMeters			= new LevelMeter[ 0 ];
	
	private final JLabel					lbSRC;

//	private DecimationInfo					info					= null;
//	private float[][]						frameBuf				= new float[2][0];
//	private float[][]						emptyVector				= new float[1][0];

	// --- tools ---
	
	private final   Map						tools					= new HashMap();
	private			AbstractTool			activeTool				= null;
	private final	TimelinePointerTool		pointerTool;

	// --- actions ---
	private final static String				plugInPackage			= "de.sciss.eisenkraut.render.";
	private final static String				fscapePackage			= "de.sciss.fscape.render.";

	private final actionRevealFileClass		actionRevealFile;
	private final actionNewFromSelClass		actionNewFromSel;
	private final actionCloseClass			actionClose;
	private final actionSaveClass			actionSave;
	private final actionSaveAsClass			actionSaveAs;
	private final actionSaveAsClass			actionSaveCopyAs;
	private final actionSaveAsClass			actionSaveSelectionAs;
//	private final actionImportMarkersClass	actionImportMarkers;
//	private final actionCutClass			actionCut;
//	private final actionCopyClass			actionCopy;
//	private final actionPasteClass			actionPaste;
//	private final actionDeleteClass			actionDelete;
	private final actionSelectAllClass		actionSelectAll;
//	private final actionTrimToSelectionClass actionTrimToSelection;
//	private final actionInsertSilenceClass	actionInsertSilence;
	private final MenuAction				actionProcess, actionFadeIn, actionFadeOut, actionGain,
											actionInvert, actionMix,
											actionReverse, actionRotateChannels, // actionSilence, 
											actionFScNeedlehole,
											actionDebugDump, actionDebugVerify, actionInsertRec;
	private final actionProcessAgainClass	actionProcessAgain;

	private final actionSpanWidthClass		actionIncWidth, actionDecWidth;
	private final actionScrollClass			actionZoomAllOut;
	private final Action					actionIncVertical;
	private final Action					actionDecVertical;

//	private final LaterInvocationManager	lim;

//	private final ComponentListener			rowHeightListener;
	private final AbstractWindow.Adapter	winListener;

	private	final DocumentFrame				enc_this				= this;

	private final JLabel					lbWriteProtected;
//	private final MutableIcon				icnWriteProtected;
	private boolean							writeProtected			= false;
	private boolean							wpHaveWarned			= false;
	
	private final ShowWindowAction			actionShowWindow;
		
	private static final String smpPtrn			= "ch.{3} @ {0,number,0}";
	private static final String timePtrn		= "ch.{3} @ {1,number,integer}:{2,number,00.000}";
	private final MessageFormat msgCsr1			= new MessageFormat( timePtrn, Locale.US );
	private final MessageFormat msgCsr2PCMFloat	= new MessageFormat( "{4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
	private final MessageFormat msgCsr3PCMInt	= new MessageFormat( "= {6,number,0} @ {7,number,integer}-bit int", Locale.US );
	private final MessageFormat msgCsr2Peak		= new MessageFormat( "peak {4,number,0.000} ({5,number,0.00} dBFS)", Locale.US );
	private final MessageFormat msgCsr3RMS		= new MessageFormat( "eff {6,number,0.000} ({7,number,0.00} dBFS)", Locale.US );
	private int					csrInfoBits;
	private boolean				csrInfoIsInt;
	private static final double TwentyByLog10	= 20 / Math.log( 10 );

	private final Color colrClear				= new Color( 0xA0, 0xA0, 0xA0, 0x00 );
	
	// --------- former viewport ---------
	// --- painting ---
	private final Color colrSelection			= GraphicsUtil.colrSelection;
//		private final Color colrSelection2		= new Color( 0xB0, 0xB0, 0xB0, 0x3F );  // selected timeline span over unselected trns
	private final Color colrSelection2			= new Color( 0x00, 0x00, 0x00, 0x20 );  // selected timeline span over unselected trns
//	private final Color colrPosition			= new Color( 0xFF, 0x00, 0x00, 0x4F );
	private final Color colrPosition			= new Color( 0xFF, 0x00, 0x00, 0x7F );
	private final Color colrZoom				= new Color( 0xA0, 0xA0, 0xA0, 0x7F );
//		private final Color colrPosition		= Color.red;
	private Rectangle   vpRecentRect			= new Rectangle();
	private int			vpPosition				= -1;
	private Rectangle   vpPositionRect			= new Rectangle();
	private final ArrayList	vpSelections		= new ArrayList();
	private final ArrayList	vpSelectionColors	= new ArrayList();
	private Rectangle	vpSelectionRect			= new Rectangle();
	
	private Rectangle   vpUpdateRect			= new Rectangle();
	private Rectangle   vpZoomRect				= null;
	private float[]		vpDash					= { 3.0f, 5.0f };
	private float		vpScale;

	private final Stroke[] vpZoomStroke			= {
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 0.0f ),
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 4.0f ),
		new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, vpDash, 6.0f ),
	};
	private int			vpZoomStrokeIdx			= 0;

	private boolean		waveExpanded			= true;
	private boolean		markVisible;
	private boolean		chanMeters				= false;
	private boolean		forceMeters				= false;
	
	private final TimelineToolBar			timeTB;
	private final TransportToolBar			transTB;

	// --- progress bar ---
	
	private final JTextField				ggAudioFileDescr;
	private final ProgressPanel				pProgress;
	private final CrossfadePanel			pOverlay;

	private final boolean					internalFrames;

	private final BasicApplication			app;
	private final SuperColliderClient		superCollider;
	private final LevelMeterManager			lmm;

	private boolean							disposed		= false;
	
//	private boolean							metersStarted	= false;
//	private long							timeMetersPause;
	
	private final Timer						playTimer;
	private double							playRate		= 1.0;

	/**
	 *  Constructs a new timeline window with
	 *  all the sub elements. Installs the
	 *  global key commands. (a DocumentFrame
	 *  should be created only once in the application).
	 *
	 *  @param  doc		session Session
	 */
	public DocumentFrame( final Session doc )
	{
		super( REGULAR );
		
		app					= (BasicApplication) AbstractApplication.getApplication();

		this.doc			= doc;
		transport			= doc.getTransport();
		timelinePos			= doc.timeline.getPosition();
		timelineSel			= doc.timeline.getSelectionSpan();
		timelineVis			= doc.timeline.getVisibleSpan();
		timelineRate		= doc.timeline.getRate();
		timelineLen			= doc.timeline.getLength();
		
		superCollider		= SuperColliderClient.getInstance();

//		lim					= new LaterInvocationManager( new LaterInvocationManager.Listener() {
//			// o egal
//			public void laterInvocation( Object o )
//			{
//				updatePositionAndRepaint();
//			}
//		});
		
		lmm					= new LevelMeterManager( superCollider.getMeterManager() );

		final Container					cp			= getContentPane();
//		final JRootPane					rp			= getRootPane();
		final InputMap					imap		= getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap					amap		= getActionMap();
//		final Box						topPane		= Box.createHorizontalBox();
		final AbstractButton			ggAudioInfo, ggRevealFile;
		final int						myMeta		= MenuFactory.MENU_SHORTCUT == KeyEvent.CTRL_MASK ?
			KeyEvent.CTRL_MASK | KeyEvent.SHIFT_MASK : MenuFactory.MENU_SHORTCUT;	// META on Mac, CTRL+SHIFT on PC
		final TreeExpanderButton		ggTreeExp;
		final TopPainter				trackPainter;
		final MenuRoot					mr;
		final GradientPanel				topPane		= new GradientPanel();
		final LookAndFeel				laf			= UIManager.getLookAndFeel();
		final boolean					isAqua		= laf == null ? false : laf.getID().equals( "Aqua" );
		final GradientPaint				grad		= isAqua ? new GradientPaint( 0f, 0f, new Color( 0xF3, 0xF3, 0xF3 ), 0f, 69f, new Color( 0xC4, 0xC4, 0xC4 )) : null;
		Box								box;
//		Dimension						d;

		internalFrames		= app.getWindowHandler().usesInternalFrames();

		timeTB		= new TimelineToolBar( doc );
		transTB		= new TransportToolBar( doc );

		topPane.setLayout( new BoxLayout( topPane, BoxLayout.X_AXIS ));
		topPane.setGradient( grad );
		
// !! not used at the moment !!
//		rowHeightListener	= new ComponentAdapter() {
//			public void componentResized( ComponentEvent e ) {
////				vpTrackPanel.updateSelectionAndRepaint();
//			}
//
//			public void componentShown( ComponentEvent e ) {
//				updateSelectionAndRepaint();
//			}
//
//			public void componentHidden( ComponentEvent e ) {
//				updateSelectionAndRepaint();
//			}
//		};

		wavePanel			= new ComponentHost();
        timeAxis			= new TimelineAxis( doc, wavePanel );
		markAxis			= new MarkerAxis( doc, wavePanel );
		markVisible			= app.getUserPrefs().getBoolean( PrefsUtil.KEY_VIEWMARKERS, false );
		markAxisHeader		= new TrackRowHeader( doc.markerTrack, doc.tracks, doc.selectedTracks, doc.getUndoManager() );
		markAxisHeader.setPreferredSize( new Dimension( 63, markAxis.getPreferredSize().height ));	// XXX
		markAxisHeader.setMaximumSize( new Dimension( 128, markAxis.getMaximumSize().height ));		// XXX
		if( markVisible ) {
			markAxis.startListening();
		} else {
			markAxis.setVisible( false );
			markAxisHeader.setVisible( false );
		}
//		waveHeaderView		= new SpringPanel( 0, 0, 1, 1 );
		flagsPanel			= new JPanel( new GridLayout( 0, 1, 1, 1 ));
		metersPanel			= new JPanel( new GridLayout( 0, 1, 1, 1 )); // SpringPanel( 0, 0, 1, 1 );
		rulersPanel			= new JPanel( new GridLayout( 0, 1, 1, 1 ));
		lmm.setDynamicComponent( metersPanel );
		waveHeaderPanel		= new JPanel( new BorderLayout() );
//		waveHeaderPanel.setLayout( new BoxLayout( waveHeaderPanel, BoxLayout.Y_AXIS ));
//		waveHeaderPanel.add( Box.createVerticalStrut( timeAxis.getPreferredSize().height ));
//		waveHeaderPanel.add( markAxisHeader );
//		waveHeaderPanel.add( waveHeaderView );
		channelHeaderPanel	= new JPanel();
		channelHeaderPanel.setLayout( new BoxLayout( channelHeaderPanel, BoxLayout.X_AXIS ));
final Box bbb = Box.createVerticalBox();
//bbb.add( Box.createVerticalStrut( timeAxis.getPreferredSize().height ));
final GradientPanel gp = new GradientPanel();
gp.setBottomBorder( true );
gp.setGradient( grad );
//GUIUtil.constrainHeight( gp, timeAxis.getPreferredSize().height );
gp.setPreferredSize( new Dimension( 0, timeAxis.getPreferredSize().height ));
bbb.add( gp );
bbb.add( markAxisHeader );
		waveHeaderPanel.add( bbb, BorderLayout.NORTH );
		channelHeaderPanel.add( flagsPanel );
		channelHeaderPanel.add( metersPanel );
		channelHeaderPanel.add( rulersPanel );
		waveHeaderPanel.add( channelHeaderPanel, BorderLayout.CENTER );

		waveView			= new WaveformView( doc, wavePanel );
		wavePanel.setLayout( new BoxLayout( wavePanel, BoxLayout.Y_AXIS ));
		wavePanel.add( timeAxis );
		wavePanel.add( markAxis );
		wavePanel.add( waveView );

        scroll				= new TimelineScroll( doc );
		ggTrackPanel		= new JPanel( new BorderLayout() );
		ggTrackPanel.add( wavePanel, BorderLayout.CENTER );
		ggTrackPanel.add( waveHeaderPanel, BorderLayout.WEST );
		ggTrackPanel.add( scroll, BorderLayout.SOUTH );
        
		lbWriteProtected	= new JLabel();
		ggAudioInfo			= new ModificationButton( ModificationButton.SHAPE_INFO );
		ggAudioInfo.setAction( new actionAudioInfoClass() );
		ggRevealFile		= new ModificationButton( ModificationButton.SHAPE_REVEAL );
		actionRevealFile	= new actionRevealFileClass();
		ggRevealFile.setAction( actionRevealFile );
//        HelpGlassPane.setHelp( ggAudioInfo, "AudioHeaderInfo" );
		ggAudioFileDescr	= new JTextField( 32 );
		ggAudioFileDescr.setEditable( false );
		ggAudioFileDescr.setFocusable( false );
		ggAudioFileDescr.setBackground( null );
		ggAudioFileDescr.setBorder( null );

		lbSRC				= new JLabel( getResourceString( "buttonSRC" ));
		lbSRC.setForeground( colrClear );
//        HelpGlassPane.setHelp( lbSRC, "SampleRateConversion" );
		box					= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( 4 ));
		box.add( lbWriteProtected );
		box.add( ggAudioInfo );
		box.add( ggRevealFile );
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
		final JButton ggExportAFR = new JButton( getResourceString( "buttonDragRegion" ), new ImageIcon( getClass().getResource( "dragicon.png" )));
//		ggExportAFR.setVerticalTextPosition( SwingConstants.CENTER );
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
//				if( !dndStarted && contains( e.getPoint() )) showFileChooser();
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

//		timeTB.addSeparator( new Dimension( 4, 4 ));
		timeTB.add( Box.createHorizontalStrut( 4 ));
		timeTB.addButton( ggExportAFR );
// ----------
		
		topPane.setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
		timeTB.setOpaque( false );
		topPane.add( timeTB );
//		topPane.add( Box.createHorizontalStrut( 8 ));
//		topPane.add( ggExportAFR );
//		topPane.add( Box.createHorizontalStrut( 8 ));
		transTB.setOpaque( false );
		topPane.add( transTB );
		topPane.add( Box.createHorizontalGlue() );
//		topPane.add( Box.createHorizontalStrut( 16 ));
//		topPane.add( lbProgress );
//		topPane.add( pb );
//		topPane.add( new ModificationButton( ModificationButton.SHAPE_ABORT ));
		ggTreeExp	= new TreeExpanderButton();
		ggTreeExp.setExpandedToolTip( getResourceString( "buttonExpWaveTT" ));
		ggTreeExp.setCollapsedToolTip( getResourceString( "buttonCollWaveTT" ));
		ggTreeExp.setExpanded( true );
		ggTreeExp.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final Dimension d	= getSize();
				
				waveExpanded = ggTreeExp.isExpanded();
				
				if( waveExpanded ) {
					waveView.setVisible( true );
					channelHeaderPanel.setVisible( true );
					if( markVisible ) {
						markAxis.setVisible( true );
						markAxisHeader.setVisible( true );
					}
					scroll.setVisible( true );
					timeTB.setVisible( true );
//					waveHeaderView.makeCompactGrid();
//					ggTrackPanel.revalidate();
//getRootPane().revalidate();
					pack();
//					startStopMeters();
				} else {
//					startStopMeters();
//					waveView.setPreferredSize( waveView.getSize() );

					setPreferredSize( getSize() );

					waveView.setVisible( false );
					channelHeaderPanel.setVisible( false );
					markAxis.setVisible( false );
					markAxisHeader.setVisible( false );
					scroll.setVisible( false );
					timeTB.setVisible( false );
// System.err.println( "wavePanel.getPreferredSize : " +wavePanel.getPreferredSize() );
//					waveHeaderView.makeCompactGrid();
					actionZoomAllOut.perform();
//					ggTrackPanel.revalidate();
//ggTrackPanel.setSize( ggTrackPanel.getPreferredSize() );
//Component gp = getRootPane().getGlassPane();
//if( gp != null ) gp.setSize( 1, 1 ); // getRootPane().setGlassPane( null );
//getRootPane().revalidate();
//System.err.println( "ggTrackPanel.getSize() : " +ggTrackPanel.getPreferredSize() );
//System.err.println( "getContentPane().getPreferredSize() : " +getContentPane().getPreferredSize() );
//System.err.println( "getRootPane().getPreferredSize() : " +getRootPane().getPreferredSize() );
//System.err.println( "getRootPane().getGlassPane() : " +getRootPane().getGlassPane() );
//System.err.println( "getRootPane().getLayeredPane().getPreferredSize() : " +getRootPane().getLayeredPane().getPreferredSize() );
//					pack();
//System.err.println( "getRootPane().getPreferredSize() : " +getRootPane().getPreferredSize() );
//if( gp != null ) getRootPane().setGlassPane( gp );

					setSize( new Dimension( d.width - timeTB.getWidth(), d.height - (waveView.getHeight() + scroll.getHeight() +
							 (markVisible ? markAxis.getHeight() : 0)) ));
				}
//				getRootPane().revalidate();
			}
		});
		topPane.add( ggTreeExp );
		
		gp.setGradientShift( 0, topPane.getPreferredSize().height );
		
		cp.add( topPane, BorderLayout.NORTH );
		cp.add( ggTrackPanel, BorderLayout.CENTER );
		cp.add( box, BorderLayout.SOUTH );
		
		// --- Tools ---
		
		pointerTool = new TimelinePointerTool();
		tools.put( new Integer( ToolAction.POINTER ), pointerTool );
		tools.put( new Integer( ToolAction.ZOOM ), new TimelineZoomTool() );

		// ---- TopPainter ----

		trackPainter	= new TopPainter() {
			public void paintOnTop( Graphics2D g2 )
			{
				Rectangle r;

				r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ); // getViewRect();
				if( !vpRecentRect.equals( r )) {
					recalcTransforms( r );
				}

				for( int i = 0; i < vpSelections.size(); i++ ) {
					r = (Rectangle) vpSelections.get( i );
					g2.setColor( (Color) vpSelectionColors.get( i ));
					g2.fillRect( vpSelectionRect.x, r.y - vpRecentRect.y, vpSelectionRect.width, r.height );
				}
				
				if( markVisible ) {
					markAxis.paintFlagSticks( g2, vpRecentRect );
				}
				
				g2.setColor( colrPosition );
				g2.drawLine( vpPosition, 0, vpPosition, vpRecentRect.height );

				if( vpZoomRect != null ) {
					g2.setColor( colrZoom );
					g2.setStroke( vpZoomStroke[ vpZoomStrokeIdx ]);
					g2.drawRect( vpZoomRect.x, vpZoomRect.y, vpZoomRect.width, vpZoomRect.height );
				}
			}
		};
		wavePanel.addTopPainter( trackPainter );

		// ---- listeners ----

		doc.timeline.addTimelineListener( this );
//		doc.addListener( this );
		doc.getDecimatedTrail().addAsyncListener( this );

		doc.audioTracks.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				documentUpdate();
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}

			public void sessionObjectChanged( SessionCollection.Event e )
			{
//				if( e.getModificationType() == AudioTrack.OWNER_WAVE ) {
//					updateOverviews( false, false );
//				}
			}
		});
		
		doc.selectedTracks.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				updateSelectionAndRepaint();
			}

			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		});
	
//		transport.addRealtimeConsumer( this );
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
//				final Span span	 = e.getAffectedSpan().shift( -timelineVis.start );
//				final Rectangle updateRect = new Rectangle(
//					(int) (span.start * vpScale), 0,
//					(int) (span.getLength() * vpScale) + 2, wavePanel.getHeight() ).
//						intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
//				if( !updateRect.isEmpty() ) {
//					// update markAxis in any case, even if it's invisible
//					// coz otherwise the flag stakes are not updated!
//					wavePanel.update( markAxis );
//					wavePanel.repaint( updateRect );
//				}
			}
		});

		winListener = new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e ) {
				actionClose.perform();
			}
			
//			public void windowGainedFocus( AbstractWindow.Event e )
			public void windowActivated( AbstractWindow.Event e )
			{
				// need to check 'disposed' to avoid runtime exception in doc handler if document was just closed
				if( !disposed ) {
//System.err.println( "DocumentFrame.winListener.windowActivated(). hashCode = "+enc_this.hashCode() );
					app.getDocumentHandler().setActiveDocument( enc_this, doc );
//					app.getMenuFactory().setSelectedWindow( actionShowWindow );
					((BasicWindowHandler) app.getWindowHandler()).setMenuBarBorrower( enc_this );
//				actionShowWindow.setSelected( true );
				}
			}
		};
		this.addListener( winListener );
//		this.addWindowFocusListener( winListener );
		
//		superCollider.addServerListener( this );

		waveView.addComponentListener( new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				updateSelectionAndRepaint();
			}
		});
		
		timeTB.addToolActionListener( this );
		timeTB.selectTool( ToolAction.POINTER );

//		new DynamicAncestorAdapter
		addDynamicListening( new DynamicPrefChangeManager( app.getUserPrefs(), new String[] {
			PrefsUtil.KEY_VIEWNULLLINIE, PrefsUtil.KEY_VIEWVERTICALRULERS, PrefsUtil.KEY_VIEWMARKERS,
			PrefsUtil.KEY_TIMEUNITS, PrefsUtil.KEY_VIEWCHANMETERS },
			new LaterInvocationManager.Listener() {

			public void laterInvocation( Object o )
			{
				final PreferenceChangeEvent e			= (PreferenceChangeEvent) o;
				final String				key			= e.getKey();
				
				if( key == PrefsUtil.KEY_VIEWNULLLINIE ) {
					waveView.setNullLinie( e.getNode().getBoolean( e.getKey(), false ));
				} else if( key == PrefsUtil.KEY_VIEWVERTICALRULERS ) {
					final boolean visible = e.getNode().getBoolean( e.getKey(), false );
					rulersPanel.setVisible( visible );
//					for( int ch = 0; ch < collChannelRulers.size(); ch++ ) {
//						((JComponent) collChannelRulers.get( ch )).setVisible( visible );
//					}
//					waveHeaderView.makeCompactGrid();
//					GUIUtil.makeCompactSpringGrid( waveHeaderView, collChannelRulers.size(), 3, 0, 0, 1, 1 );
				} else if( key == PrefsUtil.KEY_VIEWCHANMETERS ) {
					chanMeters = e.getNode().getBoolean( e.getKey(), false );
					showHideMeters();
//					GUIUtil.makeCompactSpringGrid( waveHeaderView, collChannelMeters.size(), 3, 0, 0, 1, 1 );
				} else if( key == PrefsUtil.KEY_VIEWMARKERS ) {
					markVisible = e.getNode().getBoolean( e.getKey(), false );
					if( ggTreeExp.isExpanded() ) {
						markAxis.setVisible( markVisible );
						markAxisHeader.setVisible( markVisible );
						wavePanel.updateAll();
					}
					if( markVisible ) {
						markAxis.startListening();
					} else {
						markAxis.stopListening();
					}
				} else if( key == PrefsUtil.KEY_TIMEUNITS ) {
					final int timeUnits = e.getNode().getInt( key, 0 );
					msgCsr1.applyPattern( timeUnits == 0 ? smpPtrn : timePtrn );
				}
			}
		})); // .addTo( rp );
		
		playTimer = new Timer( 33, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				timelinePos = transport.getCurrentFrame();
				updatePositionAndRepaint();
				scroll.setPosition( timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
			}
		});
		
		// --- Actions ---
//final InputMap					imap		= ggTrackPanel.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
//final ActionMap					amap		= ggTrackPanel.getActionMap();

		actionNewFromSel	= new actionNewFromSelClass();
		actionClose			= new actionCloseClass();
		actionSave			= new actionSaveClass();
		actionSaveAs		= new actionSaveAsClass( false, false );
		actionSaveCopyAs	= new actionSaveAsClass( true, false );
		actionSaveSelectionAs = new actionSaveAsClass( true, true );
//		actionImportMarkers	= new actionImportMarkersClass();
//		actionCut			= new actionCutClass();
//		actionCopy			= new actionCopyClass();
//		actionPaste			= new actionPasteClass();
//		actionDelete		= new actionDeleteClass();
		actionSelectAll		= new actionSelectAllClass();
//		actionTrimToSelection=new actionTrimToSelectionClass();
//		actionInsertSilence	= new actionInsertSilenceClass();
		actionInsertRec		= new actionInsertRecClass();

		actionProcess		= new actionProcessClass();
		actionProcessAgain	= new actionProcessAgainClass();
		actionFadeIn		= new actionPlugInClass( plugInPackage + "FadeIn" );
		actionFadeOut		= new actionPlugInClass( plugInPackage + "FadeOut" );
		actionGain			= new actionPlugInClass( plugInPackage + "Gain" );
		actionInvert		= new actionPlugInClass( plugInPackage + "Invert" );
		actionMix			= new actionPlugInClass( plugInPackage + "Mix" );
		actionReverse		= new actionPlugInClass( plugInPackage + "Reverse" );
		actionRotateChannels = new actionPlugInClass( plugInPackage + "RotateChannels" );
//		actionSilence		= new actionPlugInClass( plugInPackage + "Silence" );
		actionFScNeedlehole	= new actionPlugInClass( fscapePackage + "Needlehole" );

		actionDebugDump		= new actionDebugDumpClass();
		actionDebugVerify	= new actionDebugVerifyClass();

		actionIncVertical	= new actionVerticalZoomClass( 2.0f );
		actionDecVertical	= new actionVerticalZoomClass( 0.5f );
		actionIncWidth		= new actionSpanWidthClass( 2.0f );
		actionDecWidth		= new actionSpanWidthClass( 0.5f );
		actionZoomAllOut	= new actionScrollClass( SCROLL_ENTIRE_SESSION );

		actionShowWindow	= new ShowWindowAction( this );

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.CTRL_MASK ), "inch" );
		amap.put( "inch", actionIncVertical );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, KeyEvent.CTRL_MASK ), "dech" );
		amap.put( "dech", actionDecVertical );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.CTRL_MASK ), "incw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, MenuFactory.MENU_SHORTCUT ), "incw" );
		amap.put( "incw", actionIncWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, KeyEvent.CTRL_MASK ), "decw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, MenuFactory.MENU_SHORTCUT ), "decw" );
		amap.put( "decw", actionDecWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, myMeta ), "samplvl" );
		amap.put( "samplvl", new actionSpanWidthClass( 0.0f ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
		amap.put( "retn", new actionScrollClass( SCROLL_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
		amap.put( "left", new actionScrollClass( SCROLL_SELECTION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
		amap.put( "right", new actionScrollClass( SCROLL_SELECTION_STOP ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, KeyEvent.ALT_MASK ), "fit" );
		amap.put( "fit", new actionScrollClass( SCROLL_FIT_TO_SELECTION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, KeyEvent.ALT_MASK ), "entire" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, myMeta ), "entire" );
		amap.put( "entire", actionZoomAllOut );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK ), "seltobeg" );
		amap.put( "seltobeg", new actionSelectClass( SELECT_TO_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK + KeyEvent.ALT_MASK ), "seltoend" );
		amap.put( "seltoend", new actionSelectClass( SELECT_TO_SESSION_END ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ), "postoselbegc" );
		amap.put( "postoselbegc", doc.timeline.getPosToSelAction( true, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ), "postoselendc" );
		amap.put( "postoselendc", doc.timeline.getPosToSelAction( false, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, KeyEvent.ALT_MASK ), "postoselbeg" );
		amap.put( "postoselbeg", doc.timeline.getPosToSelAction( true, false ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.ALT_MASK ), "postoselend" );
		amap.put( "postoselend", doc.timeline.getPosToSelAction( false, false ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_M, 0 ), "dropmark" );
		amap.put( "dropmark", new actionDropMarkerClass() );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, 0 ), "selnextreg" );
		amap.put( "selnextreg", new actionSelectRegionClass( SELECT_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.ALT_MASK ), "selprevreg" );
		amap.put( "selprevreg", new actionSelectRegionClass( SELECT_PREV_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.SHIFT_MASK ), "extnextreg" );
		amap.put( "extnextreg", new actionSelectRegionClass( EXTEND_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.ALT_MASK + KeyEvent.SHIFT_MASK ), "extprevreg" );
		amap.put( "extprevreg", new actionSelectRegionClass( EXTEND_PREV_REGION ));

		setFocusTraversalKeysEnabled( false ); // we want the tab! we gotta have that tab! ouwe!

		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );

		setPreferredSize( new Dimension( 576, 288 )); // XXX
				
		// ---- menus and actions ----
		mr = app.getMenuBarRoot();
		
		mr.putMimic( "file.new.fromSelection", this, actionNewFromSel );
//		mr.putMimic( "file.import.markers", this, actionImportMarkers );
		mr.putMimic( "file.close", this, actionClose );
		mr.putMimic( "file.save", this, actionSave );
		mr.putMimic( "file.saveAs", this, actionSaveAs );
		mr.putMimic( "file.saveCopyAs", this, actionSaveCopyAs );
		mr.putMimic( "file.saveSelectionAs", this, actionSaveSelectionAs );

		mr.putMimic( "edit.undo", this, doc.getUndoManager().getUndoAction() );
		mr.putMimic( "edit.redo", this, doc.getUndoManager().getRedoAction() );
		mr.putMimic( "edit.cut", this, doc.getCutAction() );
		mr.putMimic( "edit.copy", this, doc.getCopyAction() );
		mr.putMimic( "edit.paste", this, doc.getPasteAction() );
		mr.putMimic( "edit.clear", this, doc.getDeleteAction() );
		mr.putMimic( "edit.selectAll", this, actionSelectAll );

		mr.putMimic( "timeline.insertSilence", this, doc.getSilenceAction() );
		mr.putMimic( "timeline.insertRecording", this, actionInsertRec );
		mr.putMimic( "timeline.trimToSelection", this, doc.getTrimAction() );

		mr.putMimic( "process.again", this, actionProcessAgain );
		mr.putMimic( "process.fadeIn", this, actionFadeIn );
		mr.putMimic( "process.fadeOut", this, actionFadeOut );
		mr.putMimic( "process.gain", this, actionGain );
		mr.putMimic( "process.invert", this, actionInvert );
		mr.putMimic( "process.mix", this, actionMix );
		mr.putMimic( "process.reverse", this, actionReverse );
		mr.putMimic( "process.rotateChannels", this, actionRotateChannels );
		mr.putMimic( "process.fscape.needlehole", this, actionFScNeedlehole );

		mr.putMimic( "debug.dumpRegions", this, actionDebugDump );
		mr.putMimic( "debug.verifyRegions", this, actionDebugVerify );
		
		updateEditEnabled( false );
//
//		if( superCollider.getStatus() != null ) serverStarted();

		AbstractWindowHandler.setDeepFont( cp, Collections.singletonList( timeTB ));
		GUIUtil.setDeepFont( timeTB, app.getGraphicsHandler().getFont( GraphicsHandler.FONT_SYSTEM | GraphicsHandler.FONT_MINI ));
//		pack();
		app.getMenuFactory().addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!
		init();

		updateTitle();
		documentUpdate();

		setVisible( true );
		toFront();
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

	protected boolean alwaysPackSize()
	{
		return false;
	}

//	protected Action replaceDummyAction( int ID, Action dummyAction )
//	{
//		case MenuFactory.M_PROCESS:
//			actionProcess.mimic( dummyAction );
//			return actionProcess;
//
//		case MenuFactory.MI_PROCESS_AGAIN:
//			actionProcessAgain.mimic( dummyAction );
//			return actionProcessAgain;
//
//		case MenuFactory.MI_PROCESS_FADEIN:
//			actionFadeIn.mimic( dummyAction );
//			actionFadeIn.setEnabled( true );
//			return actionFadeIn;
//
//		case MenuFactory.MI_PROCESS_FADEOUT:
//			actionFadeOut.mimic( dummyAction );
//			actionFadeOut.setEnabled( true );
//			return actionFadeOut;
//
//		case MenuFactory.MI_PROCESS_GAIN:
//			actionGain.mimic( dummyAction );
//			actionGain.setEnabled( true );
//			return actionGain;
//
//		case MenuFactory.MI_PROCESS_INVERT:
//			actionInvert.mimic( dummyAction );
//			actionInvert.setEnabled( true );
//			return actionInvert;
//
//		case MenuFactory.MI_PROCESS_MIX:
//			actionMix.mimic( dummyAction );
//			actionMix.setEnabled( true );
//			return actionMix;
//
//		case MenuFactory.MI_PROCESS_REVERSE:
//			actionReverse.mimic( dummyAction );
//			actionReverse.setEnabled( true );
//			return actionReverse;
//
//		case MenuFactory.MI_PROCESS_ROTATECHANNELS:
//			actionRotateChannels.mimic( dummyAction );
//			actionRotateChannels.setEnabled( true );
//			return actionRotateChannels;
//
////		case MenuFactory.MI_PROCESS_SILENCE:
////			actionSilence.mimic( dummyAction );
////			actionSilence.setEnabled( true );
////			return actionSilence;
////
//		case MenuFactory.MI_PROCESS_FSC_NEEDLEHOLE:
//			actionFScNeedlehole.mimic( dummyAction );
//			actionFScNeedlehole.setEnabled( true );
//			return actionFScNeedlehole;
//
//		case MenuFactory.MI_DEBUG_DUMPTRACK:
//			actionDebugDump.mimic( dummyAction );
//			actionDebugDump.setEnabled( true );
//			return actionDebugDump;
//		}
//
//		return dummyAction;
//	}

	public void setSRCEnabled( boolean onOff )
	{
		lbSRC.setForeground( onOff ? Color.red : colrClear );
	}

	public void setForceMeters( boolean onOff )
	{
		if( onOff != forceMeters ) {
			forceMeters = onOff;
			showHideMeters();
			final int holdDur = forceMeters ? -1 : LevelMeter.DEFAULT_HOLD_DUR;
			for( int i = 0; i < channelMeters.length; i++ ) {
				channelMeters[ i ].setHoldDuration( holdDur );
			}
//			if( !forceMeters && !transport.isRunning() ) {
//				timeMetersPause = System.currentTimeMillis() + 5000;
//			}
		}
	}
	
	public float getMaxMeterHold()
	{
		float hold = Float.NEGATIVE_INFINITY;
		for( int i = 0; i < channelMeters.length; i++ ) {
			hold = Math.max( hold, channelMeters[ i ].getHoldDecibels() );
		}
		return hold;
	}
	
	public void clearMeterHold()
	{
		for( int i = 0; i < channelMeters.length; i++ ) {
			channelMeters[ i ].clearHold();
		}
	}
	
	private void showHideMeters()
	{
		final boolean visible	= chanMeters || forceMeters;

		if( metersPanel.isVisible() != visible ) {
			metersPanel.setVisible( visible );
//			if( visible) metersPanel.revalidate();
//			if( visible) {
//				metersPanel.setSize( metersPanel.getPreferredSize().width, waveHeaderPanel.getHeight() ); // pack();
//				for( int i = 0; i < channelMeters.length; i++ ) {
//					channelMeters[ i ].setSize( channelMeters[ i ].getPreferredSize().width, waveHeaderPanel.getHeight() / channelMeters.length );
//					channelMeters[ i ].setVisible( true );
//				}
//				metersPanel.revalidate();
//			}
//System.err.println( "metersPanel.getComponentCount() = "+metersPanel.getComponentCount()+"; size = "+metersPanel.getWidth()+", "+metersPanel.getHeight() );
//			waveHeaderView.makeCompactGrid();
//			startStopMeters();
		}
	}
	
	/**
	 *  Recreates the main frame's title bar
	 *  after a sessions name changed (clear/load/save as session)
	 */
	public void updateTitle()
	{
		final File				fDisp	= doc.getDisplayDescr().file;
		final AudioFileDescr[]	afds	= doc.getDescr();
		final String			name;
		final Icon				icn;
		File					f;

		writeProtected	= false;
		
		actionRevealFile.setFile( afds.length == 0 ? null : afds[ 0 ].file );
		
		if( fDisp == null ) {
			name			= getResourceString( "frameUntitled" );
		} else {
			name			= fDisp.getName();
			try {
				for( int i = 0; i < afds.length; i++ ) {
					f = afds[ i ].file;
					if( f == null ) continue;
					writeProtected |= !f.canWrite() || ((f.getParentFile() != null) && !f.getParentFile().canWrite());
				}
			} catch( SecurityException e ) {}
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
		}
		actionShowWindow.putValue( Action.NAME, name );
		actionSave.setEnabled( !writeProtected && doc.isDirty() );
		setDirty( doc.isDirty() );
		
		if( writeProtected && !wpHaveWarned && doc.isDirty() ) {
//			MutableIcon warnIcon = new MutableIcon( 128 );
//			warnIcon.setID( MutableIcon.WRITE_PROTECTED );
			JOptionPane.showMessageDialog( getWindow(), getResourceString( "warnWriteProtected" ),
				getResourceString( "msgDlgWarn" ), JOptionPane.WARNING_MESSAGE, null );
			wpHaveWarned = true;
		}
	}
	
	// sync: attempts exclusive on MTE and shared on TIME!
	private void updateOverviews( boolean justBecauseOfResize, boolean allTracks )
	{
//System.err.println( "update" );
		waveView.update( timelineVis );
		if( allTracks ) wavePanel.updateAll();
	}

	private String getResourceString( String key )
	{
		return app.getResourceString( key );
	}

//	private BlendContext createBlendContext( long maxLength )
//	{
//		return BlendingAction.createBlendContext(
//			AbstractApplication.getApplication().getUserPrefs().node( BlendingAction.DEFAULT_NODE ),
//			timelineRate, maxLength );
//	}

	private void documentUpdate()
	{
		boolean					revalidate	= false;

		final java.util.List	collChannelMeters;
		LevelMeter[]			meters;
		AudioTrackRowHeader		chanHead;
		AudioTrack				t;
		int						oldChannels, newChannels;
		Axis					chanRuler;
		LevelMeter				chanMeter;
	
//		try {
//			doc.bird.waitShared( Session.DOOR_TRACKS );

			newChannels = doc.getDisplayDescr().channels;
			oldChannels	= collChannelHeaders.size();
//			oldChannels = ggTrackPanel.getNumChannels(); // collOverviews.size();
//
//			assert collChannelHeaders.size() == oldChannels : collChannelHeaders.size();

//System.err.println( "oldChannels = "+oldChannels+"; newChannels = "+newChannels );

			meters				= channelMeters;
			collChannelMeters	= new ArrayList( meters.length );
			for( int ch = 0; ch < meters.length; ch++ ) {
				collChannelMeters.add( meters[ ch ]);
			}
			
//			if( metersPanel.getParent() != null ) waveHeaderView.remove( metersPanel );

			// first kick out editors whose tracks have been removed
			for( int ch = 0; ch < oldChannels; ch++ ) {
				chanHead	= (AudioTrackRowHeader) collChannelHeaders.get( ch );
				t			= (AudioTrack) chanHead.getTrack();
				if( !doc.audioTracks.contains( t )) {
					revalidate	= true;
					chanHead	= (AudioTrackRowHeader) collChannelHeaders.remove( ch );
					chanMeter	= (LevelMeter) collChannelMeters.remove( ch );
					chanRuler	= (Axis) collChannelRulers.remove( ch );
//					chanHead.removeComponentListener( rowHeightListener );
					oldChannels--;
                    // XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
//					ggTrackPanel.remove( overview );
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
newLp:		for( int ch = 0; ch < newChannels; ch++ ) {
				t			= (AudioTrack) doc.audioTracks.get( ch );
				for( int ch2 = 0; ch2 < oldChannels; ch2++ ) {
					chanHead = (AudioTrackRowHeader) collChannelHeaders.get( ch );
					if( chanHead.getTrack() == t ) continue newLp;
				}
				
				revalidate = true;

				chanHead = new AudioTrackRowHeader( t, doc.tracks, doc.selectedTracks, doc.getUndoManager() );
//				chanHead.addComponentListener( rowHeightListener );
				collChannelHeaders.add( chanHead );
//				ggTrackPanel.add( overview, ch );
//				waveHeaderView.add( chanHead, ch * 3 );
//				waveHeaderView.gridAdd( chanHead, 0, ch + AUDIOTRACK_OFF );
				flagsPanel.add( chanHead, ch );

				chanMeter = new LevelMeter();
// THEY DON'T WORK FOR SOME REASON (ALL TTs DON'T WORK IN THIS FRAME ...)
//				meter.setToolTipEnabled( true );
				collChannelMeters.add( chanMeter );
//				waveHeaderView.add( chanMeter, (ch * 3) + 1 );
//				waveHeaderView.gridAdd( chanMeter, 1, ch + AUDIOTRACK_OFF );
//				metersPanel.gridAdd( chanMeter, 0, ch );
				metersPanel.add( chanMeter, ch );
//System.err.println( "ADD "+ch );

				chanRuler = new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
				chanRuler.setSpace( VectorSpace.createLinSpace( 0.0, 1.0, -100.0, 100.0, null, null, null, null ));
				collChannelRulers.add( chanRuler );
//				waveHeaderView.add( chanRuler, (ch * 3) + 2 );
//				waveHeaderView.gridAdd( chanRuler, 2, ch + AUDIOTRACK_OFF );
				rulersPanel.add( chanRuler, ch );

				initStrip( chanRuler, chanMeter );
			}
			
			meters	= new LevelMeter[ collChannelMeters.size() ];
			for( int ch = 0; ch < meters.length; ch++ ) {
				meters[ ch ] = (LevelMeter) collChannelMeters.get( ch );
			}
			channelMeters	= meters;
			lmm.setMeters( meters );

//			if( metersPanel.getParent() == null ) waveHeaderView.gridAdd( metersPanel, 1, AUDIOTRACK_OFF, 1, newChannels );
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRACKS );
//		}

		if( revalidate ) {
//			metersPanel.makeCompactGrid();
//metersPanel.revalidate();
//			waveHeaderView.makeCompactGrid();
//			GUIUtil.makeCompactSpringGrid( waveHeaderView, newChannels, 3, 0, 0, 1, 1 ); // initX, initY, padX, padY
//			GUIUtil.makeCompactSpringGrid( ggTrackPanel, newChannels, 1, 0, 0, 0, 1 ); // initX, initY, padX, padY
//			waveHeaderView.revalidate();
//			ggTrackPanel.revalidate();
		}

		updateOverviews( false, true );
	}

	private void initStrip( Axis chanRuler, LevelMeter chanMeter )
	{
//		final Preferences prefs = app.getUserPrefs();
//	
//		chanMeter.setVisible( prefs.getBoolean( PrefsUtil.KEY_VIEWCHANMETERS, false ));
//		chanRuler.setVisible( prefs.getBoolean( PrefsUtil.KEY_VIEWVERTICALRULERS, false ));
	}
	
	public ProcessingThread closeDocument( boolean force, Flag wasClosed )
	{
		doc.getTransport().stop();
		if( !force ) {
			final ProcessingThread pt = confirmUnsaved( getResourceString( "menuClose" ), wasClosed );
			if( pt != null ) {
				pt.addListener( new ProcessingThread.Listener() {
					public void processStarted( ProcessingThread.Event e ) {}
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
	
	private void documentClosed()
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
			chanHead = (AudioTrackRowHeader) collChannelHeaders.remove( 0 );
			chanHead.dispose();
		}
		while( !collChannelRulers.isEmpty() ) {
			chanRuler = (Axis) collChannelRulers.remove( 0 );
			chanRuler.dispose();
		}
		for( int ch = 0; ch < channelMeters.length; ch++ ) {
			channelMeters[ ch ].dispose();
		}
		channelMeters = new LevelMeter[ 0 ];
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

//	public ProcessingThread insertSilence( long pos, long numFrames )
//	{
//		return actionInsertSilence.initiate( pos, numFrames );
//	}

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
		
//		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		final String[]					options	= { getResourceString( "buttonSave" ),
													getResourceString( "buttonCancel" ),
													getResourceString( "buttonDontSave" ) };
		int								choice;
//		ProcessingThread				proc;
		AudioFileDescr					displayAFD	= doc.getDisplayDescr();
		AudioFileDescr[]				afds		= doc.getDescr();
		String							name;
		
		if( displayAFD.file == null ) {
			name = getResourceString( "frameUntitled" );
		} else {
			name = displayAFD.file.getName();
		}
		
		choice = JOptionPane.showOptionDialog( getWindow(), name + " :\n" + getResourceString( "optionDlgUnsaved" ),
											   actionName, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
											   options, options[1] );
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
			if( (displayAFD.file == null) || writeProtected ) {
				afds = actionSaveAs.query( afds, false, false, null );
			}
			if( afds != null ) {
				return actionSave.initiate( actionSave.getValue( Action.NAME ).toString(), null, afds, false, false );
			}
			return null;
			
		default:
			assert false : choice;
			return null;
		}
	}

	/**
	 *  Only call in the Swing thread!
	 */
	private void updatePositionAndRepaint()
	{
		boolean pEmpty, cEmpty;
		int		x, x2;
		
		pEmpty = (vpPositionRect.x + vpPositionRect.width < 0) || (vpPositionRect.x > vpRecentRect.width);
		if( !pEmpty ) vpUpdateRect.setBounds( vpPositionRect );

//			recalcTransforms();
		if( vpScale > 0f ) {
			vpPosition	= (int) ((timelinePos - timelineVis.getStart()) * vpScale + 0.5f);
//				positionRect.setBounds( position, 0, 1, recentRect.height );
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
//ggTrackPanel.repaint( updateRect );
		}
//			if( !updateRect.isEmpty() ) paintImmediately( updateRect );
//			Graphics g = getGraphics();
//			if( g != null ) {
//				paintDirty( g, updateRect );
//				g.dispose();
//			}
	}

	/**
	 *  Only call in the Swing thread!
	 */
	private void updateSelectionAndRepaint()
	{
		final Rectangle r = new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() );
	
		vpUpdateRect.setBounds( vpSelectionRect );
		recalcTransforms( r );
//			try {
//				doc.bird.waitShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
			updateSelection();
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
//			}
		if( vpUpdateRect.isEmpty() ) {
			vpUpdateRect.setBounds( vpSelectionRect );
		} else if( !vpSelectionRect.isEmpty() ) {
			vpUpdateRect = vpUpdateRect.union( vpSelectionRect );
		}
		vpUpdateRect = vpUpdateRect.intersection( new Rectangle( 0, 0, wavePanel.getWidth(), wavePanel.getHeight() ));
		if( !vpUpdateRect.isEmpty() ) {
			wavePanel.repaint( vpUpdateRect );
//ggTrackPanel.repaint( updateRect );
		}
//			if( !updateRect.isEmpty() ) {
//				Graphics g = getGraphics();
//				if( g != null ) {
//					paintDirty( g, updateRect );
//				}
//				g.dispose();
//			}
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
//ggTrackPanel.repaint( updateRect );
		}
	}
	
	private void recalcTransforms( Rectangle newRect )
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

	public void setZoomRect( Rectangle r )
	{
		vpZoomRect		= r;
		vpZoomStrokeIdx	= (vpZoomStrokeIdx + 1) % vpZoomStroke.length;

		wavePanel.repaint();
	}
	
//	private void startStopMeters()
//	{
//		final MeterManager			mm		= superCollider.getMeterManager();
//		final SuperColliderPlayer	p		= superCollider.getPlayerForDocument( doc );
//		final Bus					b		= p == null ? null : p.getInputBus();
//		final boolean				start	= (b != null) && ((chanMeters && waveExpanded) || forceMeters) && !metersStarted;
//		final boolean				stop	= ((b == null) || ((!chanMeters || !waveExpanded) && !forceMeters)) && metersStarted;
//		
//		if( start ) {
//			final boolean task = transport.isRunning() || forceMeters;
//			mm.addListener( this, b, null, task );
//			metersStarted	= true;
//			if( task ) timeMetersPause = Long.MAX_VALUE;
//		} else if( stop ) {
//			mm.removeListener( this );
//			metersStarted = false;
//		}
//	}
	
//	private void startMeters()
//	{
//		final MeterManager			mm	= superCollider.getMeterManager();
//		final SuperColliderPlayer	p	= superCollider.getPlayerForDocument( doc );
//		final boolean				task;
//		
//		if( p != null ) {
//			b	= p.getInputBus();
//			if( b != null ) {
//				task			= transport.isRunning() || forceMeters;
//				mm.addListener( this, b, null, task );
//				metersStarted	= true;
//				if( task ) timeMetersPause = Long.MAX_VALUE;
//			}
//		}
//	}
//

//	private void stopMeters()
//	{
//		superCollider.getMeterManager().removeListener( this );
//		metersStarted = false;
//	}

// ------------- DecimatedTrail.AsyncListener interface -------------

	public void asyncFinished( DecimatedTrail.AsyncEvent e )
	{
		doc.getDecimatedTrail().removeAsyncListener( this );
		updateOverviews( false, true );
	}

	public void asyncUpdate( DecimatedTrail.AsyncEvent e )
	{
		updateOverviews( false, true );
	}
	
// ------------- ServerListener interface -------------

//	public void serverAction( ServerEvent e )
//	{
//		switch( e.getID() ) {
//		case ServerEvent.STOPPED:
//			lmm.clearInputs();
////			startStopMeters();
////			if( chanMeters && waveExpanded ) {
////				stopMeters();
////			}
//			break;
//			
//		case ServerEvent.RUNNING:
//			serverStarted();
////			startStopMeters();
////			if( chanMeters && waveExpanded ) {
////				startMeters();
////			}
//			break;
//			
//		default:
//			break;
//		}
//	}

// ---------------- MeterListener interface ---------------- 

//	public void meterUpdate( float[] peakRMSPairs )
//	{
//		final LevelMeter[]	meters		= channelMeters;
//		final int			numMeters	= Math.min( meters.length, peakRMSPairs.length >> 1 );
//		final long			now			= System.currentTimeMillis();
//		
//		if( now > timeMetersPause ) {
//			superCollider.getMeterManager().setListenerTask( this, false, null );
//		}
//		
//		for( int ch = 0, j = 0; ch < numMeters; ch++ ) {
////System.err.println( "Y! " + peakRMSPairs[ j ] );
////			meters[ ch ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ]);
//			meters[ ch ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ], now );
//		}
//	}

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
		pProgress.finishProgression( result );
		pOverlay.performFade( 0f, 4000, 250 );
	}
	
	public void setProgressionText( String text )
	{
		pProgress.setProgressionText( text );
	}
	
	public void showMessage( int type, String text )
	{
		pProgress.showMessage( type, text );
	}
	
	public void displayError( Exception e, String processName )
	{
		GUIUtil.displayError( getWindow(), e, processName );
	}

	private void updateAFDGadget()
	{
		final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
		final AudioFileDescr[]	afds		= doc.getDescr();
		displayAFD.rate						= timelineRate;
		displayAFD.length					= timelineLen;
		for( int i = 0; i < afds.length; i++ ) {
			afds[ i ].rate					= displayAFD.rate;
			afds[ i ].length				= displayAFD.length;
		}
		ggAudioFileDescr.setText( displayAFD.getFormat() );
		
		pOverlay.performFade( 0f, 1000, 250 );
//		if( !ggAudioFileDescr.isVisible() ) {
//			hideProgressBarTimer.stop();
//			hideProgressBarListener.actionPerformed( null );
//		}
	}

	private void updateCursorFormat()
	{
		final AudioFileDescr displayAFD	= doc.getDisplayDescr();
		csrInfoBits						= displayAFD.bitsPerSample;
		csrInfoIsInt					= displayAFD.sampleFormat == AudioFileDescr.FORMAT_INT;
	}

// ---------------- SessionChangeListener interface ---------------- 

//	public void sessionChanged( SessionChangeEvent e )
//	{
//		ggAudioFileDescr.setText( e.getDocument().getAudioFileDescr().getFormat() );
//		documentUpdate( e.getDocument() );
//	}

 // ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		final boolean	wasEmpty = timelineSel.isEmpty();
		final boolean	isEmpty;
	
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
			timelineSel	= doc.timeline.getSelectionSpan();
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}

		updateSelectionAndRepaint();
		isEmpty	= timelineSel.isEmpty();
		if( wasEmpty != isEmpty ) {
			updateEditEnabled( !isEmpty );
		}
    }

	// warning : don't call doc.setAudioFileDescr, it will restore the old markers!
	public void timelineChanged( TimelineEvent e )
    {
//		if( !doc.bird.attemptExclusive( Session.DOOR_ALL, 250 )) return;
//		try {
			timelineRate				= doc.timeline.getRate();
			timelineLen					= doc.timeline.getLength();
			playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
			updateAFDGadget();
			updateOverviews( false, true );
//		}
//		finally {
//			doc.bird.releaseExclusive( Session.DOOR_ALL );
//		}
    }

	public void timelinePositioned( TimelineEvent e )
	{
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
			timelinePos = doc.timeline.getPosition();
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
		
		updatePositionAndRepaint();
//		scroll.setPosition( timelinePos, 0, TimelineScroll.TYPE_UNKNOWN );
		scroll.setPosition( timelinePos, 0, pointerTool.validDrag ?
			TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
	}

    public void timelineScrolled( TimelineEvent e )
    {
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
			timelineVis	= doc.timeline.getVisibleSpan();
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}

		updateOverviews( false, true );
		updateTransformsAndRepaint( false );
    }

// ---------------- TransportListener interface ---------------- 

	public void transportPlay( Transport transport, long pos, double rate )
	{
//		if( metersStarted ) {
//			superCollider.getMeterManager().setListenerTask( this, true, null );
//			timeMetersPause = Long.MAX_VALUE;
//	 	}
//		
		playRate = rate;
		playTimer.setDelay( Math.min( (int) (1000 / (vpScale * timelineRate * playRate)), 33 ));
		playTimer.restart();
	}
	
	public void transportStop( Transport transport, long pos )
	{
		playTimer.stop();
//		if( metersStarted ) {
//			if( !forceMeters ) timeMetersPause = System.currentTimeMillis() + 5000;
//		}
	}

	public void transportPosition( Transport transport, long pos, double rate ) {}
	public void transportReadjust( Transport transport, long pos, double rate ) {}

	public void transportQuit( Transport transport )
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
	public void toolChanged( ToolActionEvent e )
	{
//		Transmitter			trns;
//		TransmitterEditor	trnsEdit;
	
		if( activeTool != null ) {
			activeTool.toolDismissed( waveView );
		}

		// forward event to all editors that implement ToolActionListener
//		if( !doc.bird.attemptShared( Session.DOOR_TRNS | Session.DOOR_GRP, 250 )) return;
//		try {
//			for( int i = 0; i < doc.activeTransmitters.size(); i++ ) {
//				trns		= (Transmitter) doc.activeTransmitters.get( i );
//				trnsEdit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
//				if( trnsEdit instanceof ToolActionListener ) {
//					((ToolActionListener) trnsEdit).toolChanged( e );
//				}
//			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRNS | Session.DOOR_GRP );
//		}

		activeTool = (AbstractTool) tools.get( new Integer( e.getToolAction().getID() ));
		if( activeTool != null ) {
			waveView.setCursor( e.getToolAction().getDefaultCursor() );
			activeTool.toolAcquired( waveView );
		} else {
			waveView.setCursor( null );
		}
	}

// ---------------- ClipboardOwner interface ---------------- 

	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
		// XXX evtl. dispose() aufrufen
	}
	
// ---------------- internal action classes ---------------- 

	private class actionDebugDumpClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
//			try {
//				doc.bird.waitShared( Session.DOOR_MTE );
				System.err.println( "------------ Document: "+doc.getDisplayDescr().file+" ------------" );
				doc.getAudioTrail().debugDump();
				System.err.println( "   --------- decimated ---------" );
				doc.getDecimatedTrail().debugDump();
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_MTE );
//			}
		}
	}

	private class actionDebugVerifyClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			System.err.println( "------------ Document: " + doc.getDisplayDescr().file + " ------------" );
			doc.getAudioTrail().debugVerifyContiguity();
			System.err.println("   --------- decimated ---------");
			doc.getDecimatedTrail().debugVerifyContiguity();
		}
	}

	private class actionNewFromSelClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			final ClipboardTrackList	tl		= doc.getSelectionAsTrackList();
			final Session				doc2;
			final AudioFileDescr		afd, afd2;
//			final Track.Info			ti;
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
					public void processStarted( ProcessingThread.Event e ) {}
					public void processStopped( ProcessingThread.Event e ) {
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
	private class actionCloseClass
	extends MenuAction
	{
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
	private class actionSaveClass
	extends MenuAction
	{
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
				afds = actionSaveAs.query( doc.getDescr(), false, false, null );
			} else {
				afds = doc.getDescr();
			}
			if( afds != null ) {
				perform( getValue( NAME ).toString(), null, afds, false, false );
			}
		}
		
		private void perform( String name, Span span, AudioFileDescr[] afds, boolean asCopy, boolean openAfterSave )
		{
			final ProcessingThread pt = initiate( name, span, afds, asCopy, openAfterSave );
			if( pt != null ) doc.start( pt );
		}

		private ProcessingThread initiate( String name, final Span span, final AudioFileDescr[] afds, final boolean asCopy, final boolean openAfterSave )
		{
			final ProcessingThread pt = doc.procSave( name, span, afds, asCopy );
			if( pt == null ) return null;
			
			pt.addListener( new ProcessingThread.Listener() {
				public void processStopped( ProcessingThread.Event e )
				{
					if( !e.isDone() ) return;

					wpHaveWarned = false;

					if( !asCopy ) {
//						doc.getUndoManager().discardAllEdits();
						if( afds.length == 1 ) app.getMenuFactory().addRecent( afds[ 0 ].file );
//						doc.setDescr( afds );
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
				
				public void processStarted( ProcessingThread.Event e ) {}
			});
			return pt;
		}
	}
	
	// action for the Save-Session-As menu item
	private class actionSaveAsClass
	extends MenuAction
	{
		private final boolean	asCopy;
		private final boolean	selection;
		private final Flag		openAfterSave;
	
		private actionSaveAsClass( boolean asCopy, boolean selection )
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
			final AudioFileDescr[] afds = query( doc.getDescr(), asCopy, selection, openAfterSave );
			if( afds != null ) {
				actionSave.perform( getValue( NAME ).toString(), selection ? timelineSel : null, afds, asCopy, openAfterSave.isSet() );
			}
		}
		
		/**
		 *  Open a file chooser so the user
		 *  can select a new output file and format for the session.
		 *
		 *  @return the AudioFileDescr representing the chosen file path
		 *			and format or <code>null</code>
		 *			if the dialog was cancelled.
		 */
		protected AudioFileDescr[] query( AudioFileDescr[] protoType, boolean asCopy, boolean selection, Flag openAfterSave )
		{
			if( protoType.length == 0 ) return null;
		
//			final FileDialog			fDlg;
			final AudioFileDescr[]		afds;
			final AudioFileFormatPane	affp;
//			final JOptionPane			dlg;
			final SpringPanel			msgPane;
			final PathField[]			ggPathFields;
			final JCheckBox				ggOpenAfterSave;
			final JPanel				p;
			File						f, f2;
			String[]					queryOptions = { getResourceString( "buttonSave" ),
														 getResourceString( "buttonCancel" )};
			int							i, result;
			String						str;
			JLabel						lb;
			int							y	= 0;
			
			msgPane			= new SpringPanel( 4, 2, 4, 2 );
			ggPathFields	= new PathField[ protoType.length ];
			affp			= new AudioFileFormatPane( AudioFileFormatPane.FORMAT | AudioFileFormatPane.ENCODING );
			affp.fromDescr( protoType[0] );
			lb				= new JLabel( getResourceString( "labelOutputFile" ), JLabel.RIGHT );
//			lb.setLabelFor( ggPathField );
			msgPane.gridAdd( lb, 0, y );
			for( int j = 0; j < protoType.length; j++, y++ ) {
				ggPathFields[ j ] = new PathField( PathField.TYPE_OUTPUTFILE, getValue( NAME ).toString() );
				if( protoType[ j ].file == null ) {
					f	= new File( System.getProperty( "user.home" ));
					f2	= new File( f, "Desktop" );
					ggPathFields[ j ].setPath( new File( f2.isDirectory() ? f2 : f, getResourceString( "frameUntitled" )));
				} else {
					if( asCopy || selection ) {
						str	= protoType[ j ].file.getName();
						i	= str.lastIndexOf( '.' );
						if( i == -1 ) i = str.length();
						ggPathFields[ j ].setPath( new File( protoType[ j ].file.getParentFile(), str.substring( 0, i ) +
							 (selection ? getResourceString( "fileDlgCut" ) : " " + getResourceString( "fileDlgCopy" )))); // suffix is appended by affp!
					} else {
						ggPathFields[ j ].setPath( protoType[ j ].file );
					}
				}
				affp.automaticFileSuffix( ggPathFields[ j ] );
				if( (protoType[ j ].file == null) || asCopy || selection ) {	// create non-existent file name
					ggPathFields[ j ].setPath( IOUtil.nonExistentFileVariant( ggPathFields[ j ].getPath(), -1,
						selection ? null : " ", null ));
				}
				ggPathFields[ j ].selectFileName( false );
				msgPane.gridAdd( ggPathFields[ j ], 1, y );
				if( j == 0 ) GUIUtil.setInitialDialogFocus( ggPathFields[ j ]);
			}
			lb = new JLabel( getResourceString( "labelFormat" ), JLabel.RIGHT );
			msgPane.gridAdd( lb, 0, y );
			msgPane.gridAdd( affp, 1, y, -1, 1 );
			lb.setLabelFor( affp );
			y++;
			
			if( asCopy ) {
				ggOpenAfterSave = new JCheckBox( getResourceString( "labelOpenAfterSave" ));
				ggOpenAfterSave.setSelected( openAfterSave.isSet() );
				msgPane.gridAdd( ggOpenAfterSave, 1, y );
			} else {
				ggOpenAfterSave	= null;
//				msgPane.gridAdd( new JLabel( " " ), 1, y );
			}

			AbstractWindowHandler.setDeepFont( msgPane );
			msgPane.makeCompactGrid();
			p			= new JPanel( new BorderLayout() );
			p.add( msgPane, BorderLayout.NORTH );

			result		= JOptionPane.showOptionDialog( getWindow(), p, getValue( NAME ).toString(),
														JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
														null, queryOptions, queryOptions[0] );

			if( ggOpenAfterSave != null ) {
				openAfterSave.set( ggOpenAfterSave.isSelected() );
			}

			if( result == 0 ) {
				afds	= new AudioFileDescr[ protoType.length ];
				for( int j = 0; j < ggPathFields.length; j++ ) {
					f = ggPathFields[ j ].getPath();
					if( f.exists() ) {
						queryOptions = new String[] { getResourceString( "buttonOverwrite" ),
													  getResourceString( "buttonCancel" )};
						result = JOptionPane.showOptionDialog( getWindow(), getResourceString( "warnFileExists" ) +
							":\n" + f.getAbsolutePath() + "\n" + getResourceString( "warnOverwriteFile" ),
							getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
							null, queryOptions, queryOptions[1] );
						if( result != 0 ) return null;
					}
					afds[ j ]		= new AudioFileDescr( protoType[ j ]);
					affp.toDescr( afds[ j ]);
					afds[ j ].file	= f;
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

	private class actionSelectAllClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			doc.timeline.editSelect( this, new Span( 0, timelineLen ));
		}
	}

	private class actionInsertRecClass
	extends MenuAction
	{
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
				GUIUtil.displayError( getWindow(), e1, getValue( NAME ).toString() );
				return;
			}
			recFile	= recDlg.getResult();
			
			if( recFile != null ) {
				try {
//					if( true ) throw new IOException( "test" );
					tmpDoc	= Session.newFrom( recFile, false, false, false );
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
						public void processStarted( ProcessingThread.Event e ) {}
						public void processStopped( ProcessingThread.Event e )
						{
							tl.dispose();
							tmpDoc.dispose();
							if( e.isDone() ) {
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
		
		private void confirmDelete( File path )
		{
			final int		choice;
			final Object[]	options	= new String[] { getResourceString( "buttonKeepFile" ), getResourceString( "buttonDeleteFile" )};
			choice = JOptionPane.showOptionDialog( getWindow(), getResourceString( "optionDlgKeepRec1" ) + path.getAbsolutePath() + getResourceString( "optionDlgKeepRec2" ),
					   getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null,
					   options, options[ 0 ]);
			if( choice == 1 ) {
				deleteFile( path );
			}
		}
		
		private void deleteFile( File path )
		{
			if( !path.delete() ) {
				JOptionPane.showMessageDialog( getWindow(), path.getAbsolutePath() + ":\n" + getResourceString( "errDeleteFile" ), getValue( NAME ).toString(), 
					JOptionPane.WARNING_MESSAGE );
			}
		}
	} // class actionInsertRecClass
	
	private class actionProcessClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e ) {}
	}

	private class actionPlugInClass
	extends MenuAction
	{
		private final String plugInClassName;
		
		private actionPlugInClass( String plugInClassName )
		{
			this.plugInClassName	= plugInClassName;
		}

		public void actionPerformed( ActionEvent e )
		{
			FilterDialog filterDlg = (FilterDialog) app.getComponent( Main.COMP_FILTER );
		
			if( filterDlg == null ) {
				filterDlg = new FilterDialog();
			}
			filterDlg.process( plugInClassName, doc, (e.getModifiers() & ActionEvent.ALT_MASK) != 0, false );
			actionProcessAgain.setPlugIn( filterDlg.getPlugIn() );
		}
	}

	private class actionProcessAgainClass
	extends MenuAction
	{
		private String plugInClassName = null;
	
		public void actionPerformed( ActionEvent e )
		{
			final FilterDialog filterDlg = (FilterDialog) app.getComponent( Main.COMP_FILTER );
			if( (filterDlg != null) && (plugInClassName != null) ) {
				filterDlg.process( plugInClassName, doc, false, true );
			}
		}
		
		private void setPlugIn( RenderPlugIn plugIn )
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

//	private class actionShowWindowClass
//	extends MenuAction	// SyncedMenuAction
//	{
//		private actionShowWindowClass()
//		{
//			super( null, null );
//		}
//
//		public void actionPerformed( ActionEvent e )
//		{
////			boolean state   = ((AbstractButton) e.getSource()).isSelected();
////
////			setSelected( true );
//			enc_this.setVisible( true );
//			enc_this.toFront();
//		}
//	}

	private class actionAudioInfoClass
	extends MenuAction
	{
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

	private class actionRevealFileClass
	extends MenuAction
	{
		private File f;
	
		private actionRevealFileClass()
		{
			super( "Reveal File in Finder" );
			setFile( null );
		}
	
		// osascript -e 'tell application "Finder"' -e 'activate' -e 'open location "file:///Volumes/Claude/audio"'
		//   -e 'select file "Sine441HzGain.aif" of folder of the front window'
		//   -e 'end tell'

		/**
		 *  Shows the file in the finder (Mac OS only)
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( f == null ) return;
			
			try {
				// File.toURL() ends up with sth. like "file:/Volumes/Claude/..." omitting the two initial slashes
//				final String[] cmdArray = { "osascript", "-e", "tell application \"Finder\"", "-e", "activate",
//											"-e", "open location \"" + f.getAbsoluteFile().toURL().toString() + "\"",
//											"-e", "end tell" };
				// make sure space characters are escaped as %20 in URL stylee
				final Normalizer n		= new Normalizer( Normalizer.C, false );
//				final String parentDir	= n.normalize( f.getParentFile().getAbsolutePath() ).replaceAll( " ", "%20" );
				String path				= n.normalize( f.getParentFile().getAbsoluteFile().toURL().toExternalForm() ); // getAbsolutePath() ).replaceAll( " ", "%20" );
//				String path				= n.normalize( f.getAbsoluteFile().toURL().toExternalForm() ); // getAbsolutePath() ).replaceAll( " ", "%20" );
				path					= path.substring( 5 );
StringBuffer sb = new StringBuffer();
//char ch;
int  chI;
byte[] hex = "0123456789abcdef".getBytes();
byte[] enc = path.getBytes( "UTF-8" );
for( int i = 0; i < enc.length; i++ ) {
	chI = enc[ i ]; // parentDir.charAt( i );
//	chI = (int) ch;
	if( (chI < 33) || (chI > 127) ) {
		sb.append( "%" + (char) hex[ (chI >> 4) & 0x0F ] + (char) hex[ chI & 0x0F ]);
	} else {
		sb.append( (char) chI );
	}
}
path = sb.toString();
//int i = path.lastIndexOf( '/' ) + 1;
final String parentDir = path;
final String fileName = n.normalize( f.getName() ); // .getBytes( "ISO-8859-1" ));
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
		
		private void setFile( File f )
		{
			this.f	= f;
			setEnabled( f != null );
		}
	}

	/**
	 *  Increase or decrease the height
	 *  of the rows of the selected transmitters
	 */
	private class actionVerticalZoomClass
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the row height,
		 *					factors < 1 decrease.
		 */
		private actionVerticalZoomClass( float factor )
		{
			super();
			this.factor = factor;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			float					min, max;
			final VectorSpace		spc;
			Axis					chanRuler;
						
			min = waveView.getMin();
			max = waveView.getMax();

			if( ((factor >= 1.0f) && (min > -1.0e6f) && (max < 1.0e6f)) || (factor < 1.0f && (min < -1.0e-4f) && (max > 1.0e-4f)) ) {
				min	   *= factor;
				max	   *= factor;
				waveView.setMinMax( min, max );
				spc		= VectorSpace.createLinSpace( 0.0, 1.0, min * 100, max * 100, null, null, null, null );

				for( int i = 0; i < collChannelRulers.size(); i++ ) {
					chanRuler	= (Axis) collChannelRulers.get( i );
					chanRuler.setSpace( spc );
				}
			}
		}
	} // class actionVerticalZoomClass

	/**
	 *  Increase or decrease the width
	 *  of the visible time span
	 */
	private class actionSpanWidthClass
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the span width (zoom out)
		 *					factors < 1 decrease (zoom in).
		 *					special value 0.0 means zoom to sample level
		 */
		private actionSpanWidthClass( float factor )
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
//					doc.getUndoManager().addEdit( TimelineVisualEdit.scroll( this, doc, visiSpan ));
			}
		}
	} // class actionSpanWidthClass

	private static final int SCROLL_SESSION_START	= 0;
	private static final int SCROLL_SELECTION_START	= 1;
	private static final int SCROLL_SELECTION_STOP	= 2;
	private static final int SCROLL_FIT_TO_SELECTION= 3;
	private static final int SCROLL_ENTIRE_SESSION	= 4;

	private class actionScrollClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionScrollClass( int mode )
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

	private class actionSelectClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionSelectClass( int mode )
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

	private class actionSelectRegionClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionSelectRegionClass( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan;
			UndoableEdit	edit;
			long			start, stop; // , pos;
//			boolean			b;
			Marker			mark;
			int				idx;
//
//System.out.println( "action! ");

			if( !markVisible ) return;
		
			selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
			if( selSpan.isEmpty() ) selSpan = new Span( timelinePos, timelinePos );
			
			start		= selSpan.getStart();
			stop		= selSpan.getStop();
			
			switch( mode ) {
			case SELECT_NEXT_REGION:
			case EXTEND_NEXT_REGION:
				idx		= doc.markers.indexOf( stop + 1 );	// XXX check
//System.err.println( "stop idx "+idx );
				if( idx < 0 ) idx = -(idx + 1);

				if(	idx == doc.markers.getNumStakes() ) {
					stop	= timelineLen;
				} else {
//						do {
//							b		= false;
						mark	= doc.markers.get( idx );
//							if( mark.pos == stop ) {
//								if( ++idx == doc.markers.getNumStakes() ) {
//									stop = timelineLen;
//								} else {
//									b	= true;
//								}
//							} else {
							stop	= mark.pos;
//							}
//						} while( b );
				}
				// (-(insertion point) - 1)

				if( mode == SELECT_NEXT_REGION ) {
					idx		= doc.markers.indexOf( stop - 1 );	// XXX check
//System.err.println( "start idx "+idx );
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
//						do {
//							b		= false;
						mark	= doc.markers.get( idx );
//							if( mark.pos == start ) {
//								if( --idx <= 0 ) {
//									start = 0;
//								} else {
//									b	= true;
//								}
//							} else {
							start	= mark.pos;
//							}
//						} while( b );
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
		
	private class actionDropMarkerClass
	extends AbstractAction
	{
		public void actionPerformed( ActionEvent e )
		{
			if( markVisible ) {
				markAxis.addMarker( timelinePos );
			}
		}
	} // class actionDropMarkerClass

// -------------- AFR Transfer Handler --------------

	private class AFRTransferHandler
	extends TransferHandler
	{
		private AFRTransferHandler() {}

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
		}

//		public boolean canImport( JComponent c, DataFlavor[] flavors )
//		{
//			return false;
//		}
	}

	private abstract class TimelineTool
	extends AbstractTool
	{
		private final List	collObservedComponents	= new ArrayList();
	
		private boolean adjustCatchBypass	= false;
		
		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );
			
			if( c instanceof Container ) addMouseListeners( (Container) c );
		}
		
		// additionally installs mouse input listeners on child components
		private void addMouseListeners( Container c )
		{
			Component	c2;
//			Cursor		csr	= c.getCursor();
			
			for( int i = 0; i < c.getComponentCount(); i++ ) {
				c2 = c.getComponent( i );
				collObservedComponents.add( c2 );
//				collOldCursors.add( csr );
				c2.addMouseListener( this );
				c2.addMouseMotionListener( this );
//				c2.setCursor( c.getCursor() );
				if( c2 instanceof Container ) addMouseListeners( (Container) c2 );	// recurse
			}
		}
		
		// additionally removes mouse input listeners from child components
		private void removeMouseListeners()
		{
			Component	c;
//			Cursor		csr;
		
			while( !collObservedComponents.isEmpty() ) {
				c	= (Component) collObservedComponents.remove( 0 );
//				csr	= (Cursor) collOldCursors.remove( 0 );
				c.removeMouseListener( this );
				c.removeMouseMotionListener( this );
//				c.setCursor( csr );
			}
		}

		public void toolDismissed( Component c )
		{
			super.toolDismissed( c );

			removeMouseListeners();
			
			if( adjustCatchBypass ) {
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
		private boolean shiftDrag, ctrlDrag, validDrag = false, dragStarted = false;
		private long startPos;
		private int startX;

		private final Object[] argsCsr	= new Object[8];
		private final String[] csrInfo	= new String[3];
	
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
//				editSelectAll( null );
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
		
//			selectionStart  = -1;
//			dragTimelinePosition( e );
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
//			if( observer.isVisible() && (observer.getShownTab() == ObserverPalette.CURSOR_TAB) &&
//					(e.getComponent() instanceof OverviewDisplay) && (info != null) ) {
			observer = (ObserverPalette) app.getComponent( Main.COMP_OBSERVER );
			if( (observer != null) && observer.isVisible() && (observer.getShownTab() == ObserverPalette.CURSOR_TAB) ) {				
				showCursorInfo( SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), waveView ));
			}
		}
		
//		private void showCursorInfo( OverviewDisplay overview, Point screenPt )
		private void showCursorInfo( Point screenPt )
		{
			final ObserverPalette	observer;
			
			final int				ch		= waveView.channelForPoint( screenPt );
			if( ch == -1 ) return;

			final DecimationInfo	info	= waveView.getDecimationInfo();
			if( info == null ) return;

			final long				pos		= timelineVis.getStart() + (long) 
										((double) screenPt.getX() / (double) waveView.getWidth() *
										 (double) timelineVis.getLength());
			if( (pos < 0) || (pos >= timelineLen) ) return;
		
			final String			chName	= doc.audioTracks.get( ch ).getName();
			final double			seconds	= (double) pos / timelineRate;
			final AudioTrail 		at;
			final DecimatedTrail	dt;
			final float[][]			data;
			final float[]			frame;
			float					f1;
			
			argsCsr[3]		= chName;
			argsCsr[0]		= new Long( pos );
			argsCsr[1]		= new Integer( (int) (seconds / 60) );
			argsCsr[2]		= new Float( seconds % 60 );
			
			csrInfo[0]		= msgCsr1.format( argsCsr );
			
			switch( info.model ) {
			case DecimatedTrail.MODEL_PCM:
//			if( info.getDecimationFactor() == 1 ) {
				at			= doc.getAudioTrail();
				data		= new float[ at.getChannelNum() ][];
				data[ ch ]	= new float[ 1 ];
				try {
					at.readFrames( data, 0, new Span( pos, pos + 1 ));
				}
				catch( IOException e1 ) { return; }
				f1			= data[ ch ][ 0 ];
				argsCsr[4]	= new Float( f1 );
				argsCsr[5]	= new Float( Math.log( Math.abs( f1 )) * TwentyByLog10 );
				csrInfo[1]	= msgCsr2PCMFloat.format( argsCsr );
				if( csrInfoIsInt ) {
					argsCsr[6]	= new Long( (long) (f1 * (1L << (csrInfoBits - 1))) );
					argsCsr[7]	= new Integer( csrInfoBits );
					csrInfo[2]	= msgCsr3PCMInt.format( argsCsr );
				} else {
					csrInfo[2]	= "";
				}
				break;
				
			case DecimatedTrail.MODEL_FULLWAVE_PEAKRMS:
				dt			= doc.getDecimatedTrail();
				frame		= new float[ dt.getNumModelChannels() ];
//				dt.readFrame( info.idx, pos, ch, frame );
				try {
//					dt.readFrame( Math.max( 0, info.idx ), pos, ch, frame );
					dt.readFrame( Math.min( dt.getNumDecimations() - 1, info.idx + 1 ), pos, ch, frame );
				}
				catch( IOException e1 ) { return; }
				f1			= Math.max( frame[ 0 ], -frame[ 1 ] );	// peak pos/neg
				argsCsr[4]	= new Float( f1 );
				argsCsr[5]	= new Float( Math.log( f1 ) * TwentyByLog10 );
				f1			= (float) Math.sqrt( frame[ 2 ]);	// mean sqr pos/neg
//				f1			= (vector[2][x] + vector[3][x]) / 2;	// rms pos/neg
				argsCsr[6]	= new Float( f1 );
				argsCsr[7]	= new Float( Math.log( f1 ) * TwentyByLog10 );
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
//System.err.println( "setting to "+position );
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
//						doc.getUndoManager().addEdit( TimelineVisualEdit.position( this, doc, span2.getStart() ));
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

	private class TimelineZoomTool
	extends TimelineTool
	{
		private boolean					validDrag	= false, dragStarted = false;
		private long					startPos;
		private Point					startPt;
		private long					position;
		private final javax.swing.Timer	zoomTimer;
		private final Rectangle			zoomRect	= new Rectangle();

		private TimelineZoomTool()
		{
			zoomTimer = new javax.swing.Timer( 250, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					setZoomRect( zoomRect );
				}
			});
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
//						doc.getUndoManager().addEdit( TimelineVisualEdit.scroll( this, doc, span ));
					doc.timeline.editScroll( this, span );
				}
			}
			
			validDrag	= false;
		}

		// zoom to mouse position
		public void mouseClicked( MouseEvent e )
		{
			super.mouseClicked( e );

			if( !e.isAltDown() ) clickZoom( 0.5f, e );
		}

		private void clickZoom( float factor, MouseEvent e )
		{
			long	pos, visiLen, start, stop;
			Span	visiSpan;
			
			visiSpan	= timelineVis;
			visiLen		= visiSpan.getLength();
			pos			= visiSpan.getStart() + (long) ((double) e.getX() / (double) getComponent().getWidth() *
													visiSpan.getLength());
			visiLen		= (long) (visiLen * factor + 0.5f);
			if( visiLen < 2 ) return;
			
			start		= Math.max( 0, Math.min( timelineLen, pos - (long) ((pos - visiSpan.getStart()) * factor + 0.5f) ));
			stop		= start + visiLen;
			if( stop > timelineLen ) {
				stop	= timelineLen;
				start	= Math.max( 0, stop - visiLen );
			}
			visiSpan	= new Span( start, stop );
			if( !visiSpan.isEmpty() ) {
				doc.timeline.editScroll( this, visiSpan );
//					doc.getUndoManager().addEdit( TimelineVisualEdit.scroll( this, doc, visiSpan ));
			}
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), wavePanel );
			
			Span	span;
			int		zoomX; // , zoomY;
		   
			span        = timelineVis; // doc.timeline.getVisibleSpan();
			position    = span.getStart() + (long) (pt.getX() / getComponent().getWidth() *
													span.getLength());
			position    = Math.max( 0, Math.min( timelineLen, position ));
			if( !hasStarted ) {
				startPos= position;
				startPt	= pt;
			} else {
				zoomX	= Math.min( startPt.x, pt.x );
//					zoomY	= Math.min( startPt.y, pt.y );
//					zoomRect.setBounds( zoomX, zoomY, Math.abs( startPt.x - pt.x ),
//													  Math.abs( startPt.y - pt.y ));
//				zoomRect.setBounds( zoomX, 6, Math.abs( startPt.x - pt.x ),
//											   wavePanel.getHeight() - 12 );
				zoomRect.setBounds( zoomX, waveView.getY() + 6, Math.abs( startPt.x - pt.x ), waveView.getHeight() - 12 );
				setZoomRect( zoomRect );
			}
		}
	}
}