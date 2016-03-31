/*
 *  ControlRoomFrame.java
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

package de.sciss.eisenkraut.gui;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.Document;
import de.sciss.app.DocumentHandler;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.net.SuperColliderPlayer;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.PeakMeterPanel;
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

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

//import de.sciss.eisenkraut.math.MathUtil;

/**
 *	TODO: could use an explicit GroupAnySync for lmm, which would go into SuperColliderClient
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

    protected final SuperColliderClient	superCollider;
    private Group						grpMeters;
    private final PeakMeterPanel		pmg;

    private RoutingConfig				oCfg;

    private final SpringPanel			b1;
    protected final VolumeFader			ggVolume;

    private final Map<Session, SuperColliderPlayer> mapPlayers = new HashMap<Session, SuperColliderPlayer>();	// key = Session, value = SuperColliderPlayer
    private final PeakMeterManager		lmm;

    private final ActionListener		audioBoxListener;
    private boolean						isListening			= false;

    public ControlRoomFrame()
    {
        super( PALETTE );

        final Application app = AbstractApplication.getApplication();

        superCollider		= SuperColliderClient.getInstance();
        lmm					= new PeakMeterManager( superCollider.getMeterManager() );

        setTitle( app.getResourceString( "paletteCtrlRoom" ));
        setResizable( false );

        final Container			cp				= getContentPane();
        final JPanel			b2				= new JPanel( new BorderLayout() ); // Box.createHorizontalBox();
        audioPrefs								= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
        final Object			comboProto		= "XXXXXXXX";

        b1				= new SpringPanel( 2, 4, 2, 4 );
        lmm.setDynamicComponent( b1 );
        ggVolume		= new VolumeFader();
        ggOutputConfig	= new PrefComboBox();
        ggOutputConfig.putClientProperty( "JComboBox.isSquare", Boolean.TRUE );
        ggAudioBox		= new PrefComboBox();
        ggAudioBox.putClientProperty( "JComboBox.isSquare", Boolean.TRUE );
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
                    final JOptionPane op = new JOptionPane( getResourceString( "optionDlgAudioBoxReboot" ), JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION );
                    if( BasicWindowHandler.showDialog( op, getWindow(), null ) == 0 ) {
                        superCollider.reboot();
                    }
                }
            }
        };

        ggVolume.addChangeListener( new ChangeListener() {
            public void stateChanged( ChangeEvent e ) {
                superCollider.setVolume( ControlRoomFrame.this, ggVolume.getVolumeLinear() );
            }
        });

        final JToggleButton ggLimiter = new JToggleButton("Limiter");
        ggLimiter.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e ) {
                superCollider.setLimiter( ggLimiter.isSelected() );
            }
        });
        if( superCollider.getLimiter() ) ggLimiter.setSelected(true); // setSelectedIndex( 1 );

        pmg = new PeakMeterPanel();
        pmg.setBorder(true);
        pmg.setCaption(true);
        oCfg = superCollider.getOutputConfig();
        rebuildMeters();

		b2.add(pmg, BorderLayout.WEST);
		b2.add(ggVolume, BorderLayout.EAST);

        b1.gridAdd(ggLimiter, 0, 0, -1, 1);
        b1.gridAdd(b2, 0, 1, -1, 1);
        b1.gridAdd(ggOutputConfig, 0, 2, -1, 1);
        b1.gridAdd(ggAudioBox, 0, 3, -1, 1);
        b1.makeCompactGrid();

        cp.add(b1, BorderLayout.CENTER);

//		AbstractWindowHandler.setDeepFont( b1 );

        // ---- listeners -----

        addListener(new AbstractWindow.Adapter() {
            public void windowOpened(AbstractWindow.Event e) {
                startListening();
            }

            public void windowClosing(AbstractWindow.Event e) {
                setVisible(false);
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

    protected Point2D getPreferredLocation() {
        return new Point2D.Float(0.95f, 0.2f);
    }

    public void dispose() {
        AbstractApplication.getApplication().removeComponent(Main.COMP_CTRLROOM);
        lmm.dispose();
        if (grpMeters != null) {
            try {
                grpMeters.free();
            } catch (IOException e1) {
                printError("dispose", e1);
            }
            grpMeters = null;
        }
        stopListening();

        pmg.dispose();
        super.dispose();
    }

    private void updateVolume()
    {
        ggVolume.setVolumeLinear( superCollider.getVolume() );
    }

    private void startMeters()
    {
        final Server				s			= superCollider.getServer();
        final ServerOptions			so			= superCollider.getServerOptions();
        final int					numOutputBusChannels;
        final int[]					channels;
        final Group					mg			= superCollider.getMasterGroup();

        if( (s == null) || (oCfg == null) || (mg == null) ) return;

        channels				= new int[ oCfg.mapping.length ];
        numOutputBusChannels	= so.getNumOutputBusChannels();
        for( int ch = 0; ch < channels.length; ch++ ) {
            if( oCfg.mapping[ ch ] < numOutputBusChannels ) {
                channels[ ch ] = oCfg.mapping[ ch ];
            } else {
                channels[ ch ] = -1;
            }
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
        }
        catch( IOException e1 ) {
            printError( "startMeters", e1 );
        }
    }

    private void stopMeters()
    {
        lmm.clearInputs();
    }

    private static void printError( String name, Throwable t )
    {
        System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
    }

    private void rebuildMeters()
    {
        oCfg	= superCollider.getOutputConfig();

        if( oCfg != null ) {
            pmg.setNumChannels( oCfg.numChannels );
        } else {
            pmg.setNumChannels( 0 );
        }

        b1.makeCompactGrid();
        pack();

        lmm.setView( pmg );
    }

    // @synchronization	must be in event thread
    private void registerTaskSyncs()
    {
        if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

        final DocumentHandler	dh			= AbstractApplication.getApplication().getDocumentHandler();
        Document				doc;
        SuperColliderPlayer		p;

        lmm.clearTaskSyncs();

        for (int i = 0; i < dh.getDocumentCount(); i++) {
            doc = dh.getDocument(i);
            if (doc instanceof Session) {
                Session docS = (Session) doc;
                p = superCollider.getPlayerForDocument(docS);
                if (p != null) {
                    lmm.addTaskSync(p.getOutputSync());
                    mapPlayers.put(docS, p);
                }
            }
        }
    }

    // @synchronization	must be in event thread
    private void unregisterTaskSyncs()
    {
        if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

        lmm.clearTaskSyncs();

        mapPlayers.clear();
    }

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
        }
    }

    public void documentRemoved(de.sciss.app.DocumentEvent e) {
        Document doc = e.getDocument();
        if (doc instanceof Session) {
            Session docS = (Session) doc;
            final SuperColliderPlayer p = mapPlayers.remove(doc);
            if (p != null) {
                lmm.removeTaskSync(p.getOutputSync());
            }
        }
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

    private void refillConfigs()
    {
        refillIOConfigs();
        refillAudioBoxes();
    }

    public void refillIOConfigs()
    {
        final Preferences	childPrefs;
        final String[]		cfgIDs;
        final Set<StringItem> cfgItems;

        try {
            if( isListening ) {
                ggOutputConfig.stopListening();
            }
            childPrefs	= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
            cfgIDs		= childPrefs.childrenNames();
            cfgItems	= new TreeSet<StringItem>( StringItem.valueComparator );
            for (String cfgID : cfgIDs) {
                cfgItems.add(new StringItem(cfgID, childPrefs.node(cfgID).get(RoutingConfig.KEY_NAME, cfgID)));
            }
            ggOutputConfig.removeAllItems();
            for (StringItem cfgItem : cfgItems) {
                ggOutputConfig.addItem(cfgItem);
            }
        }
        catch( BackingStoreException e1 ) {
            System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
        }
        finally {
            if( isListening ) {
                ggOutputConfig.startListening();
            }
        }
    }

    public void refillAudioBoxes()
    {
        final Preferences	childPrefs;
        final String[]		cfgIDs;
        final Set<StringItem> cfgItems;
        Preferences			childPrefs2;

        try {
            if( isListening ) {
                ggAudioBox.stopListening();
                ggAudioBox.removeActionListener( audioBoxListener );
            }
            childPrefs	= audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES );
            cfgIDs		= childPrefs.childrenNames();
            cfgItems	= new TreeSet<StringItem>( StringItem.valueComparator );
            for (String cfgID : cfgIDs) {
                childPrefs2 = childPrefs.node(cfgID);
                if (childPrefs2.getBoolean(AudioBoxConfig.KEY_ACTIVE, false)) {
                    cfgItems.add(new StringItem(cfgID,
                            childPrefs2.get(AudioBoxConfig.KEY_NAME, cfgID)));
                }
            }
            ggAudioBox.removeAllItems();
            for (Object cfgItem : cfgItems) {
                ggAudioBox.addItem(cfgItem);
            }
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