/*
 *  ControlRoomFrame.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		14-Apr-06	created from MainFrame
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
//import de.sciss.eisenkraut.math.MathUtil;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.net.SuperColliderPlayer;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.Document;
import de.sciss.app.DocumentHandler;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.MultiStateButton;
import de.sciss.gui.PeakMeter;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StringItem;

import de.sciss.jcollider.Constants;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;
import de.sciss.jcollider.ServerOptions;
import de.sciss.net.OSCBundle;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 10-Apr-08
 *
 *	@todo		could use an explicit GroupAnySync for lmm, which would go into SuperColliderClient
 *				so sc-client whould be able to pause master synths according to the sync
 */
public class ControlRoomFrame
extends AppWindow
implements	DynamicListening, Constants, ServerListener, SuperColliderClient.Listener,
			DocumentListener // , TransportListener, MeterListener
{
	private final PrefComboBox			ggOutputConfig;
	private final PrefComboBox			ggAudioBox;
	private final Preferences			audioPrefs;

	private PeakMeter[]					masterMeters;
	protected final SuperColliderClient	superCollider;
	private Group						grpMeters;
//	private final Box					pMeters;
	private final PeakMeterGroup		pmg;

	private RoutingConfig				oCfg;
	
	private final SpringPanel			b1;
	protected final VolumeFader			ggVolume;
	
//	private final Set					allTransports		= new HashSet();	// element = (Transport)
//	private final Set					transportsRunning	= new HashSet();
	private final Map					mapPlayers			= new HashMap();	// key = Session, value = SuperColliderPlayer
//	private long						timeMetersPause;
	private final PeakMeterManager		lmm;
	
	private final ActionListener		audioBoxListener;
	private boolean						isListening			= false;

	public ControlRoomFrame()
	{
		super( PALETTE );
		
		final Application app = AbstractApplication.getApplication();
	
		superCollider		= SuperColliderClient.getInstance();
		lmm					= new PeakMeterManager( superCollider.getMeterManager() );
	
//		super( AbstractApplication.getApplication().getResourceString( "paletteCtrlRoom" ), HORIZONTAL, false );
		setTitle( app.getResourceString( "paletteCtrlRoom" ));
		setResizable( false );

		final Container			cp				= getContentPane();
//		final JPanel			b1				= new JPanel( new BorderLayout( 0, 2 )); // Box.createVerticalBox();
		final JPanel			b2				= new JPanel( new BorderLayout() ); // Box.createHorizontalBox();
		final MultiStateButton	ggLimiter;
		audioPrefs								= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
//		final Preferences		ocPrefs			= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
//		final Preferences		abPrefs			= audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES );
		final ControlRoomFrame	enc_this		= this;
		final Object			comboProto		= "XXXXXXXX";

		b1				= new SpringPanel( 2, 4, 2, 4 );
		lmm.setDynamicComponent( b1 );
		ggVolume		= new VolumeFader();
		ggOutputConfig	= new PrefComboBox();
		ggAudioBox		= new PrefComboBox();
		ggOutputConfig.setPrototypeDisplayValue( comboProto );
		ggAudioBox.setPrototypeDisplayValue( comboProto );
		refillConfigs();
		ggOutputConfig.setPreferences( audioPrefs, PrefsUtil.KEY_OUTPUTCONFIG );
		ggAudioBox.setPreferences( audioPrefs, PrefsUtil.KEY_AUDIOBOX );
		audioBoxListener = new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final Server server = superCollider.getServer();
				if( (server != null) && server.isRunning() ) {
					if( JOptionPane.showConfirmDialog( getWindow(), getResourceString( "optionDlgAudioBoxReboot" ), null, JOptionPane.YES_NO_OPTION ) == 0 ) {
//						superCollider.addListener( new ServerListener() {
//						    public abstract void serverAction( ServerEvent e )
//							{
//								superCollider.boot();
//							}
//
//						superCollider.quit();
						superCollider.reboot();
					}
				}
			}
		};
		
		ggVolume.addChangeListener( new ChangeListener() {
			public void stateChanged( ChangeEvent e ) {
				superCollider.setVolume( enc_this, ggVolume.getVolumeLinear() );
//				updateVolumeInfo();
			}
		});

//		ggLimiter	= new JCheckBox( getResourceString( "labelLimiter" ));
		
		ggLimiter = new MultiStateButton();
		ggLimiter.setNumColumns( 8 );
		ggLimiter.addItem( "Limiter" );
		//ggLimit2.addItem( "Limiter", Color.black, new Color( 0xE3, 0xFF, 0x9E ), new Color( 0xFC, 0xFF, 0x9E ));
// NOTE: BUG WITH CUSTOM COMPOSITE ON WIN-XP!!!
//		ggLimiter.addItem( "Limiter", null, new Color( 0xFF, 0xFA, 0x9D ), new Color( 0xFA, 0xE7, 0x9D ));
ggLimiter.addItem( "Limiter", null, new Color( 0xFF, 0xFA, 0x9D ));
		ggLimiter.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e ) {
				superCollider.setLimiter( ggLimiter.getSelectedIndex() == 1 );
			}
		});
		if( superCollider.getLimiter() ) ggLimiter.setSelectedIndex( 1 );
		
//		pMeters	= Box.createHorizontalBox();
//		pMeters.setBackground( Color.black );
//		pMeters.setBorder( new RecessedBorder() );
		pmg	= new PeakMeterGroup();
		pmg.setBorder( true );
		pmg.setCaption( true );
		oCfg = superCollider.getOutputConfig();
		rebuildMeters();
		
//		b2.add( pMeters, BorderLayout.WEST );
		b2.add( pmg, BorderLayout.WEST );
		b2.add( ggVolume, BorderLayout.EAST );
		
//		b1.add( ggLimiter, BorderLayout.NORTH );
//		b1.add( b2, BorderLayout.CENTER );
		b1.gridAdd( ggLimiter, 0, 0, -1, 1 );
		b1.gridAdd( b2, 0, 1, -1, 1 );
//		b1.add( new JLabel( getResourceString( "labelGain" )));
		b1.gridAdd( ggOutputConfig, 0, 2, -1, 1 );
		b1.gridAdd( ggAudioBox, 0, 3, -1, 1 );
//		b1.add( b3, BorderLayout.SOUTH );
		b1.makeCompactGrid();
//		if( app.getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
//    		b3.add( Box.createVerticalStrut( 16 ));
//        }
		
//		b1.setBorder( BorderFactory.createEmptyBorder( 2, 4, 2, 4 ));
		cp.add( b1, BorderLayout.CENTER );

		AbstractWindowHandler.setDeepFont( b1 );

		// ---- listeners -----

		addListener( new AbstractWindow.Adapter() {
			public void windowOpened( AbstractWindow.Event e )
			{
				startListening();
			}
//
//			public void windowClosed( AbstractWindow.Event e )
//			{
//				stopListening();
//			}

			public void windowClosing( AbstractWindow.Event e )
			{
				setVisible( false );
				dispose();
			}
		});


//		DISPOSE_ON_CLOSE doesn't work: the BasicFrame doesn't catch
//		the visible preferences. instead we hide and then dispose the window!
//		setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );

		updateVolume();

		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!
		init();
		app.addComponent( Main.COMP_CTRLROOM, this );
	}
	
	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected boolean restoreVisibility()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.95f, 0.2f );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_CTRLROOM );
		lmm.dispose();
		if( grpMeters != null ) {
			try {
				grpMeters.free();
			}
			catch( IOException e1 ) {
				printError( "dispose", e1 );
			}
			grpMeters = null;
		}
		stopListening();
		
		for( int i = 0; i < masterMeters.length; i++ ) {
			masterMeters[ i ].dispose();
		}
		
		super.dispose();
	}
	
//	protected void updateVolumeInfo()
//	{
//		ggVolume.setToolTipText( String.valueOf( ggVolume.getVolumeDecibels() ));
//	}

	private void updateVolume()
	{
		ggVolume.setVolumeLinear( superCollider.getVolume() );
//		updateVolumeInfo();
	}

	private void startMeters()
	{
		final Server				s			= superCollider.getServer();
		final ServerOptions			so			= superCollider.getServerOptions();
		final int					numOutputBusChannels;
		final int[]					channels;
		final Group					mg			= superCollider.getMasterGroup();
//		final boolean				task;
	
		if( (s == null) || (oCfg == null) || (mg == null) ) return;

		channels				= new int[ oCfg.mapping.length ];
		numOutputBusChannels	= so.getNumOutputBusChannels();
		for( int ch = 0; ch < channels.length; ch++ ) {
			if( oCfg.mapping[ ch ] < numOutputBusChannels ) {
				channels[ ch ] = oCfg.mapping[ ch ];
			} else {
				channels[ ch ] = -1;
			}
//System.err.println( "ch = "+ch+"; map = "+channels[ ch ]);
		}
		
		try {
			if( grpMeters == null ) {
				grpMeters = Group.basicNew( s );
				final OSCBundle bndl = new OSCBundle();
				bndl.addPacket( grpMeters.addBeforeMsg( mg ));
				grpMeters.setName( "CtrlRmMeters" );
				NodeWatcher.newFrom( s ).register( grpMeters );
				s.sendBundle( bndl );
			}
			lmm.setGroup( grpMeters );
			lmm.setInputs( s, channels );
//			task	= !transportsRunning.isEmpty();
//			superCollider.getMeterManager().addListener( this, s, channels, grpMeters, task );
//			timeMetersPause = task ? Long.MAX_VALUE : 0;
		}
		catch( IOException e1 ) {
			printError( "startMeters", e1 );
		}
	}

	private void stopMeters()
	{
		lmm.clearInputs();
//		superCollider.getMeterManager().removeListener( this );
	}

	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}
	
	private void rebuildMeters()
	{
//		final PeakMeter[]		meters;
//		final PeakMeterCaption	caption;
//		final Border			b1		= BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 1 );
//		final Border			b2		= BorderFactory.createEmptyBorder( 1, 1, 1, 0 ) : BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 0 );
		
		oCfg	= superCollider.getOutputConfig();
//		pMeters.removeAll();
		
		if( oCfg != null ) {
			pmg.setNumChannels( oCfg.numChannels );
//			meters	= new PeakMeter[ oCfg.numChannels ];
//			caption	= new PeakMeterCaption();
//			caption.setBorder( BorderFactory.createEmptyBorder( 5, 1, 4, 0 ));
//			pMeters.add( caption );
//			for( int ch = 0; ch < meters.length; ch++ ) {
//				meters[ ch ] = new PeakMeter();
//				meters[ ch ].setRefreshParent( true );
//				meters[ ch ].setBorder( BorderFactory.createEmptyBorder( 5, 1, 4, 1 ));
//				meters[ ch ].setTicks( 101 );
//				pMeters.add( meters[ ch ]);
//			}
//			masterMeters = meters;
			masterMeters = pmg.getMeters();
		} else {
			pmg.setNumChannels( 0 );
			masterMeters = new PeakMeter[ 0 ];
		}
		
		b1.makeCompactGrid();
		pack();
		
		lmm.setMeters( masterMeters );
	}
	
	// @synchronization	must be in event thread
	private void registerTaskSyncs()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		final DocumentHandler	dh			= AbstractApplication.getApplication().getDocumentHandler();
		Document				doc;
		SuperColliderPlayer		p;
		
//		transportsRunning.clear();
		lmm.clearTaskSyncs();
		
		for( int i = 0; i < dh.getDocumentCount(); i++ ) {
			doc = dh.getDocument( i );
			if( doc instanceof Session ) {
				p	= superCollider.getPlayerForDocument( (Session) doc );
				if( p != null ) {
					lmm.addTaskSync( p.getOutputSync() );
					mapPlayers.put( doc, p );
				}
//				allTransports.add( t );
//				mapPlayers.put( doc, t );
//				t.addTransportListener( this );
//				if( t.isRunning() ) transportsRunning.add( t );
			}
		}
	}

	// @synchronization	must be in event thread
	private void unregisterTaskSyncs()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		lmm.clearTaskSyncs();
		
//		Transport t;
//
//		for( Iterator iter = allTransports.iterator(); iter.hasNext(); ) {
//			t = (Transport) iter.next();
//			t.removeTransportListener( this );
//		}
//		
//		allTransports.clear();
//		transportsRunning.clear();
		mapPlayers.clear();
	}

// ---------------- TransportListener interface ---------------- 
//
//	public void transportPlay( Transport transport, long pos, double rate )
//	{
//		if( allTransports.contains( transport )) {
//			final boolean wasEmpty = transportsRunning.isEmpty();
//			transportsRunning.add( transport );
//			if( wasEmpty ) {
//				superCollider.getMeterManager().setListenerTask( this, true );
//				timeMetersPause = Long.MAX_VALUE;
//			}
//		}
//	}
//	
//	public void transportStop( Transport transport, long pos )
//	{
//		if( transportsRunning.remove( transport ) && transportsRunning.isEmpty() ) {
//			timeMetersPause = System.currentTimeMillis() + 5000;
//		}
//	}
//
//	public void transportPosition( Transport transport, long pos, double rate ) {}
//	public void transportQuit( Transport transport ) {}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		switch( e.getID() ) {
		case ServerEvent.STOPPED:
			grpMeters = null;
			stopMeters();
			unregisterTaskSyncs();
			break;
			
		case ServerEvent.RUNNING:
			registerTaskSyncs();
			startMeters();
			break;
			
		default:
			break;
		}
	}

// ------------- SuperCollderClient.Listener interface -------------

	public void clientAction( SuperColliderClient.Event e )
	{
		switch( e.getID() ) {
		case SuperColliderClient.Event.OUTPUTCONFIG:
			stopMeters();
			rebuildMeters();
			startMeters();
			break;
			
		case SuperColliderClient.Event.VOLUME:
			if( e.getSource() != this ) {
				updateVolume();
			}
			break;
			
		default:
			break;
		}
	}

// ---------------- DocumentListener interface ---------------- 

	public void documentAdded( de.sciss.app.DocumentEvent e )
	{
		if( e.getDocument() instanceof Session ) {
			final Session				doc = (Session) e.getDocument();
			final SuperColliderPlayer	p	= superCollider.getPlayerForDocument( doc );
			if( (p != null) && !mapPlayers.containsKey( doc )) {
				lmm.addTaskSync( p.getOutputSync() );
				mapPlayers.put( doc, p );
			}
//			final boolean	wasEmpty	= transportsRunning.isEmpty();
//			final Transport	t			= ((Session) e.getDocument()).getTransport();
//			allTransports.add( t );
//			mapPlayers.put( e.getDocument(), t );
//			t.addTransportListener( this );
//			if( t.isRunning() ) {
//				transportsRunning.add( t );
//				if( wasEmpty ) {
//					superCollider.getMeterManager().setListenerTask( this, true );
//					timeMetersPause = Long.MAX_VALUE;
//				}
//			}
		}
	}

	public void documentRemoved( de.sciss.app.DocumentEvent e )
	{
		final SuperColliderPlayer p	= (SuperColliderPlayer) mapPlayers.remove( e.getDocument() );
		if( p != null ) {
//System.err.println( "removeTaskSync" );
			lmm.removeTaskSync( p.getOutputSync() );
		}
//		if( p == null ) return;
//			
//		final boolean	wasEmpty	= transportsRunning.isEmpty();
//		allTransports.remove( t );
//		t.removeTransportListener( this );
//		if( !wasEmpty && transportsRunning.remove( t )) {
//			timeMetersPause = System.currentTimeMillis() + 5000;
//		}
	}

	public void documentFocussed( de.sciss.app.DocumentEvent e ) { /* empty */ }

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
    	isListening = true;
		superCollider.addServerListener( this );
		superCollider.addClientListener( this );
		ggAudioBox.addActionListener( audioBoxListener );
		AbstractApplication.getApplication().getDocumentHandler().addDocumentListener( this );
		registerTaskSyncs();
		startMeters();
    }

    public void stopListening()
    {
    	isListening = false;
		stopMeters();
		unregisterTaskSyncs();
		AbstractApplication.getApplication().getDocumentHandler().removeDocumentListener( this );
		ggAudioBox.removeActionListener( audioBoxListener );
		superCollider.removeClientListener( this );
		superCollider.removeServerListener( this );
    }
	
// ---------------- MeterListener interface ---------------- 
//
//	public void meterUpdate( float[] peakRMSPairs )
//	{
//		final LevelMeter[]	meters		= masterMeters;
//		final int			numMeters	= Math.min( meters.length, peakRMSPairs.length >> 1 );
//		final long			now			= System.currentTimeMillis();
//
//		if( now > timeMetersPause ) {
//			superCollider.getMeterManager().setListenerTask( this, false, null );
//		}
//
//		for( int i = 0, j = 0; i < numMeters; i++ ) {
////			meters[ i ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ]);
//			meters[ i ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ], now );
//		}
//	}

	private void refillConfigs()
	{
		refillIOConfigs();
		refillAudioBoxes();
	}
	
	public void refillIOConfigs()
	{
		final Preferences	childPrefs;
		final String[]		cfgIDs;
//		final String		cfgID	= audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null );
		
		try {
			if( isListening ) {
				ggOutputConfig.stopListening();
//				ggOutputConfig.removeActionListener( ... );
			}
			childPrefs	= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
			cfgIDs		= childPrefs.childrenNames();
			ggOutputConfig.removeAllItems();
			for( int i = 0; i < cfgIDs.length; i++ ) {
//				if( cfgIDs[ i ].equals( cfgID )) {
//					
//				}
//				oCfg = new RoutingConfig( childPrefs.node( cfgID ));
				ggOutputConfig.addItem( new StringItem( cfgIDs[ i ], childPrefs.node( cfgIDs[ i ]).get( RoutingConfig.KEY_NAME, cfgIDs[ i ])));
			}
		}
		catch( BackingStoreException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
		finally {
			if( isListening ) {
				ggOutputConfig.startListening();
//				// add ActionListener _after_ startListening() so no superfluous
//				// event is caught (resulting in the reboot-question-dialog)
//				ggAudioBox.addActionListener( audioBoxListener );
			}
			// so SuperColliderClient is notified
//			audioPrefs.put( PrefsUtil.KEY_OUTPUTCONFIG, audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null ));
		}
	}

	public void refillAudioBoxes()
	{
		final Preferences	childPrefs;
		final String[]		cfgIDs;
//		final String		oldID;
//		int					newIdx = -1;
		Preferences			childPrefs2;
		
		try {
			if( isListening ) {
				ggAudioBox.stopListening();
				ggAudioBox.removeActionListener( audioBoxListener );
			}
			childPrefs	= audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES );
			cfgIDs		= childPrefs.childrenNames();
//			oldID		= audioPrefs.get( PrefsUtil.KEY_AUDIOBOX, null );
			ggAudioBox.removeAllItems();
			for( int i = 0; i < cfgIDs.length; i++ ) {
				childPrefs2 = childPrefs.node( cfgIDs[ i ]);
				if( childPrefs2.getBoolean( AudioBoxConfig.KEY_ACTIVE, false )) {
					ggAudioBox.addItem( new StringItem( cfgIDs[ i ],
						childPrefs2.get( AudioBoxConfig.KEY_NAME, cfgIDs[ i ])));
//					if( cfgIDs[ i ].equals( oldID )) newIdx = i; // j;
//					j++;
				}
			}
//System.err.println( "oldID = " + oldID + "; newIdx = "+newIdx );	
// if( newIdx >= 0 ) ggAudioBox.setSelectedIndex( newIdx );
//			if( newIdx >= 0 ) audioPrefs.put( PrefsUtil.KEY_AUDIOBOX, cfgIDs[ newIdx ]);
		}
		catch( BackingStoreException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
		finally {
			if( isListening ) {
				ggAudioBox.startListening();
				// add ActionListener _after_ startListening() so no superfluous
				// event is caught (resulting in the reboot-question-dialog)
				ggAudioBox.addActionListener( audioBoxListener );
			}
		}
	}
	
//	protected boolean alwaysPackSize()
//	{
//		return false;
//	}

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
	
	
//	private static class TestSliderUI
//	extends javax.swing.plaf.basic.BasicSliderUI
//	{
//		final Image imgFader;
//		final Image imgTrack;
//		final Image imgTrackShadow;
//		final Paint	pntTrack;
//	
//		private TestSliderUI( JSlider s )
//		{
//			super( s );
//			imgFader		= Toolkit.getDefaultToolkit().getImage( getClass().getResource( "blackfader.png" ));	// 32 x 44
//			imgTrack		= Toolkit.getDefaultToolkit().getImage( getClass().getResource( "fadertrack.png" ));	// 5 x 73
//			imgTrackShadow	= Toolkit.getDefaultToolkit().getImage( getClass().getResource( "fadertracksh.png" ));	// 5 x 51
//final MediaTracker mt = new MediaTracker( s );
//mt.addImage( imgTrack, 0 );
//try { mt.waitForAll(); } catch( InterruptedException e1 ) {}
//			final BufferedImage bufImg = new BufferedImage( 5, 16, BufferedImage.TYPE_INT_ARGB );
//			final Graphics g2 = bufImg.createGraphics();
//			g2.drawImage( imgTrack, 0, 0, 5, 16, 0, 39, 5, 32, s );
//			g2.dispose();
////			pntTrack	= new TexturePaint( bufImg, new Rectangle( 0, 0, 5, 16 ));
//			pntTrack	= new TexturePaint( bufImg, new Rectangle( 9, 0, 5, 16 ));
//			focusInsets	= new Insets( 2, 1, 5, 13 );
//		}
//		
//		protected Dimension getThumbSize()
//		{
////			return new Dimension( 32, 44 );
//			return new Dimension( 32, 51 );
//		}
//
////		public Dimension getPreferredHorizontalSize() {
////			return PREFERRED_HORIZONTAL_SIZE;
////		}
////		public Dimension getPreferredVerticalSize() {
////			return PREFERRED_VERTICAL_SIZE;
////		}
////		
////		public Dimension getMinimumHorizontalSize() {
////			return MINIMUM_HORIZONTAL_SIZE;
////		}
////		
////		public Dimension getMinimumVerticalSize() {
////			return MINIMUM_VERTICAL_SIZE;
////		}
//
////		protected void calculateTrackRect()
////		{
////			trackRect = new Rectangle( 8, 8, 5, slider.getHeight() - 16 );
////		}
//		
//		public void paintTrack( Graphics g )
//		{
//			final Graphics2D g2 = (Graphics2D) g;
//			
//			g2.setPaint( pntTrack );
////			g2.translate( trackRect.x, trackRect.y );
////			g2.translate( 8, trackRect.y );
////			g2.fillRect( 0, 0, trackRect.width, trackRect.height );
//			g2.fillRect( 9, 48, 5, trackRect.height - 23 );
//			g2.drawImage( imgTrack, 9, 9, 14, 48, 0, 0, 5, 39, slider );
//			g2.drawImage( imgTrack, 9, contentRect.y + contentRect.height - 27, 14, contentRect.y + contentRect.height - 9, 0, 55, 5, 73, slider );
////			g2.translate( -trackRect.x, -trackRect.y );
////			g2.translate( -8, -trackRect.y );
////			g.drawImage( imgTrackShadow, 9, thumbRect.y - 5, slider ); 
//		}
//		
//		public void paintThumb( Graphics g )
//		{
//			g.drawImage( imgTrackShadow, 9, thumbRect.y - 9, slider ); 
//			g.drawImage( imgFader, thumbRect.x, thumbRect.y - 4, slider ); 
//		}
//
//		public void paintFocus( Graphics g ) {}
//	}
}