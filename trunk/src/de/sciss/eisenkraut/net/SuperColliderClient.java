/*
 *  SuperColliderClient.java
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
 *		04-Aug-05	created 
 *		15-Sep-05	added limiter
 */

package de.sciss.eisenkraut.net;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.WindowConstants;

import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.session.DocumentFrame;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.BasicEvent;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.EventManager;
import de.sciss.app.LaterInvocationManager;
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCChannel;
import de.sciss.net.OSCMessage;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.ContiguousBlockAllocator;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;
import de.sciss.jcollider.ServerOptions;
import de.sciss.jcollider.Synth;
import de.sciss.jcollider.UGenInfo;
import de.sciss.jcollider.gui.NodeTreePanel;
import de.sciss.util.Param;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 *
 *	@todo		volume should be managed in separate synths pre limiters
 *				so that pre-fader metering becomes possible
 */
public class SuperColliderClient
implements OSCRouter, de.sciss.jcollider.Constants, ServerListener, DocumentListener, EventManager.Processor
{
	private ServerOptions			so;
	private Server					server				= null;
	private boolean					serverIsReady		= false;	// = running + defs have been loaded
	private NodeWatcher				nw					= null;
	private final Preferences		audioPrefs;

	private static final int		DEFAULT_PORT		= 57109;	// our default server udp port

	private final List				collListeners		= new ArrayList();
	
	private final List				collPlayers			= new ArrayList();
	private final Map				mapDocsToPlayers	= new HashMap();
	
	private RoutingConfig			oCfg				= null;
	private Group					grpMaster;
	private Group					grpGain;
	private Group					grpLimiter;
	private boolean					limiter				= false;
//	private boolean					chanMeter			= false;
	
//	private final javax.swing.Timer	meterTimer;

	private int						dumpMode			= kDumpOff;
	private float					volume				= 1.0f;
	
	private final OSCRouterWrapper	osc;
	private static final String		OSC_SUPERCOLLIDER	= "sc";
	
	// Busses that have been allocated by OSC clients
	// key = Integer( busindex ) ; value = Bus
	private final Map				mapOSCBusses		= new HashMap();
	// Buffers that have been allocated by OSC clients
	// key = Integer( bufnum ) ; value = Buffer
	private final Map				mapOSCBuffers		= new HashMap();

	private boolean					reboot				= false;
	
	private final MeterManager		meterManager;
	
	private EventManager			elm					= null;
	
	private static SuperColliderClient instance;

	public SuperColliderClient()
	{
		super();
	
		if( instance != null ) throw new IllegalStateException( "Only one instance allowed" );
		instance	= this;
	
		final Application	app			= AbstractApplication.getApplication();
		final Preferences	userPrefs	= app.getUserPrefs();

		audioPrefs				= userPrefs.node( PrefsUtil.NODE_AUDIO );
		so						= new ServerOptions();
		so.setBlockAllocFactory( new ContiguousBlockAllocator.Factory() );	// deals better with fragmentation
		
		new DynamicPrefChangeManager( audioPrefs, new String[] { PrefsUtil.KEY_OUTPUTCONFIG },
			new LaterInvocationManager.Listener() {
				public void laterInvocation( Object o )
				{
					final PreferenceChangeEvent pce = (PreferenceChangeEvent) o;
					final String				key	= pce.getKey();
					SuperColliderPlayer			p;
					
					if( key.equals( PrefsUtil.KEY_OUTPUTCONFIG )) {
						createOutputConfig();
						setOutputConfig();
						for( int i = 0; i < collPlayers.size(); i++ ) {
							p = (SuperColliderPlayer) collPlayers.get( i );
//							p.setOutputConfig( oCfg, volume );
							p.setOutputConfig( oCfg );
						}
						if( elm != null ) {
							elm.dispatchEvent( new Event( instance, Event.OUTPUTCONFIG, System.currentTimeMillis(), instance ));
						}
					} else {
						assert false : key;
					}
				}
			}
		).startListening();	// fires change and hence createOutputConfig()

//		osc = new OSCRouterWrapper( superRouter, this );
		osc = new OSCRouterWrapper( OSCRoot.getInstance(), this );
		
		meterManager = new MeterManager( this );
	}
	
	public void init()
	throws IOException
	{
		AbstractApplication.getApplication().getDocumentHandler().addDocumentListener( this );
//		UGenInfo.readDefinitions();
		UGenInfo.readBinaryDefinitions();
	}
	
	public static SuperColliderClient getInstance()
	{
		return instance;
	}
	
	public Group getMasterGroup()
	{
		return grpMaster;
	}
	
	public MeterManager getMeterManager()
	{
		return meterManager;
	}

	public ServerOptions getServerOptions()
	{
		return so;
	}
	
	public SuperColliderPlayer getPlayerForDocument( Session doc )
	{
		return (SuperColliderPlayer) mapDocsToPlayers.get( doc );
	}
	
	public void addServerListener( ServerListener l )
	{
		synchronized( collListeners ) {
			collListeners.add( l );
		}
//		if( server != null ) {
//			server.addListener( l );
//		}
	}

	public void removeServerListener( ServerListener l )
	{
		synchronized( collListeners ) {
			collListeners.remove( l );
		}
//		if( server != null ) {
//			server.removeListener( l );
//		}
	}
	
	public void addClientListener( Listener listener )
	{
		synchronized( this ) {
			if( elm == null ) {
				elm = new EventManager( this );
			}
			elm.addListener( listener );
		}
	}

	public void removeClientListener( Listener listener )
	{
		if( elm != null ) elm.removeListener( listener );
	}

	public void setVolume( Object source, float volume )
	{
		final float oldVolume = this.volume;

		if( oldVolume == volume ) return;
		
		this.volume	= volume;
		if( (server != null) && server.isRunning() && (grpGain != null) ) {
			try {
				final OSCBundle	bndl = new OSCBundle();
//				server.getDefaultGroup().set( "volume", volume );	// all dem subgroups geddid
				bndl.addPacket( grpGain.setMsg( "volume", volume ));
				if( oldVolume == 1f ) {
					bndl.addPacket( grpGain.runMsg( true ));
				} else if( volume == 1f ) {
					bndl.addPacket( grpGain.runMsg( false ));
				}
				server.sendBundle( bndl );
			}
			catch( IOException e1 ) {
				printError( "setVolume", e1 );
			}
		}
		if( (source != null) && (elm != null) ) {
			elm.dispatchEvent( new Event( source, Event.VOLUME, System.currentTimeMillis(), this ));
		}
	}
	
	public float getVolume()
	{
		return volume;
	}
	
	public RoutingConfig getOutputConfig()
	{
		return oCfg;
	}
	
	public void setLimiter( boolean onOff )
	{
		if( this.limiter != onOff ) {
			this.limiter = onOff;
			try {
				if( grpLimiter != null ) grpLimiter.run( onOff );
			}
			catch( IOException e1 ) {
				limiter = !limiter;
				printError( "setLimiter", e1 );
			}
		}
	}

	public boolean getLimiter()
	{
		return limiter;
	}
	
	private void setOutputConfig()
	{
		if( !serverIsReady || (oCfg == null) ) return;
	
		OSCBundle	bndl	= new OSCBundle();
		Synth		synth;
	
		bndl.addPacket( grpGain.freeAllMsg() );
		bndl.addPacket( grpLimiter.freeAllMsg() );
		for( int ch = 0; ch < oCfg.numChannels; ch++ ) {
			if( oCfg.mapping[ ch ] < server.getOptions().getNumOutputBusChannels() ) {
				synth	= Synth.basicNew( "eisk-limiter", server );
				bndl.addPacket( synth.newMsg( grpLimiter, new String[] { "i_aBus" }, new float[] { oCfg.mapping[ ch ]}));
				nw.register( synth );
				synth	= Synth.basicNew( "eisk-gain", server );
				bndl.addPacket( synth.newMsg( grpGain, new String[] { "i_aBus", "volume" }, new float[] { oCfg.mapping[ ch ], volume }));
				nw.register( synth );
			}
		}
		
		try {
			server.sendBundle( bndl );
		}
		catch( IOException e1 ) {
			printError( "setOutputConfig", e1 );
		}
	}

	// @synchronization	must be called in the event thread!
	private void dispose()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

//System.err.println( "//////////// dispose /////////////" );

// throws ConcurrentModificationException !
//		for( Iterator iter = mapDocsToPlayers.keySet().iterator(); iter.hasNext(); ) {
//			disposePlayer( (Session) iter.next() );
//		}
		final Object[] docs =  mapDocsToPlayers.keySet().toArray();

		for( int i = 0; i < docs.length; i++ ) {
			disposePlayer( (Session) docs[ i ]);
		}
		
		grpLimiter = null;
		
		try {
			if( (grpMaster != null) && (server != null) && server.isRunning() ) grpMaster.free();
		}
		catch( IOException e1 ) {
			printError( "dispose", e1 );
		}
		grpMaster = null;
		
//		nw.clear();
		if( nw != null ) {
//System.err.println( "disposing nw " + nw.hashCode() );
			nw.dispose();
			nw		= null;
		}
		if( server != null ) {
			server.dispose();
			server	= null;
		}
		
		serverIsReady = false;
	}

	public void dumpOSC( int mode )
	{
		this.dumpMode	= mode;
	
//		if( (server != null) && (server.getDumpMode() != mode) ) {
		if( server != null ) {
//			try {
				server.dumpIncomingOSC( mode );
// scsynth's dumpOSC is buggy at the moment
//				server.dumpOSC( mode );
				server.dumpOutgoingOSC( mode );
//			}
//			catch( IOException e1 ) {
//				printError( "dumpOSC", e1 );
//			}
		}
	}
	
	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	public Server getServer()
	{
		return server;
	}

	public NodeWatcher getNodeWatcher()
	{
		return nw;
	}

	/**
	 * 	Queries the current server status.
	 * 
	 *	@return	the current status or null if the server is not running
	 */
	public Server.Status getStatus()
	{
		if( server != null && server.isRunning() ) {
			return server.getStatus();
		} else {
			return null;
		}
	}

	public void quit()
	{
		Server.quitAll();
	}
	
	public void reboot()
	{
		reboot = true;
		stop();
	}

	public void stop()
	{
		if( (server != null) && (server.isRunning() || server.isBooting()) ) {
			try {
//System.err.println( "//////////// stop /////////////" );
				server.quitAndWait();
			}
			catch( IOException e1 ) {
				printError( "stop", e1 );
			}
		}
	}

	/**
	 *	@synchronization	must be called in the event thread
	 */
	public boolean boot()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		if( (server != null) && (server.isRunning() || server.isBooting()) ) return false;
		
//System.err.println( "//////////// boot /////////////" );
		dispose();
	
		String					val;
		int						serverPort;	// , idx;
		Param					p;
		final String			abCfgID		= audioPrefs.get( PrefsUtil.KEY_AUDIOBOX, AudioBoxConfig.ID_DEFAULT );
		final AudioBoxConfig	abCfg		= new AudioBoxConfig( audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES ).node( abCfgID ));
		
		p	= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_AUDIORATE, null );
		if( p != null ) so.setSampleRate( p.val );
		so.setNumInputBusChannels( abCfg.numInputChannels );
		so.setNumOutputBusChannels( abCfg.numOutputChannels );
		p	= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_AUDIOBUSSES, null );
		if( p != null ) so.setNumAudioBusChannels( Math.max( abCfg.numInputChannels + abCfg.numOutputChannels, (int) p.val ));
		p	= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_SCMEMSIZE, null );
		if( p != null ) so.setMemSize( (int) p.val << 10 );
		p	= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_SCBLOCKSIZE, null );
		if( p != null ) so.setBlockSize( (int) p.val );
		if( !abCfg.name.equals( "Default" )) so.setDevice( abCfg.name );
		so.setLoadDefs( false );
//System.err.println( "abCfgID ="+abCfgID+" ("+abCfgID.equals( AudioBoxConfig.NAME_DEFAULT )+") ; in "+abCfg.numInputChannels+"; out "+abCfg.numOutputChannels );

		// udp-port-number
//		val					= audioPrefs.get( PrefsUtil.KEY_SUPERCOLLIDEROSC, "" );
//		idx					= val.indexOf( ':' );
//		serverPort			= DEFAULT_PORT;
//		try {
//			if( idx >= 0 ) serverPort = Integer.parseInt( val.substring( idx + 1 ));
//		}
//		catch( NumberFormatException e1 ) {
//			printError( "boot", e1 );
//		}
		p					= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_SCPORT, null );
		serverPort			= p == null ? DEFAULT_PORT : (int) p.val;
		val					= audioPrefs.get( PrefsUtil.KEY_SCPROTOCOL, OSCChannel.TCP );
		so.setProtocol( val );
		
//		so.setEnv( "SC_JACK_NAME", "Eisenkraut" );
		
		val		= audioPrefs.get( PrefsUtil.KEY_SUPERCOLLIDERAPP, null );
		if( val == null ) {
			System.err.println( getResourceString( "errSCSynthAppNotFound" ));
			return false;
		}
		Server.setProgram( val );

//		if( server != null ) {
//			server.dispose();	// removes listeners as well
//			server = null;
//		}
//		if( nw != null ) {
//			nw.dispose();
//			nw = null;
//		}

		try {
			// check for automatic port assignment
			if( serverPort == 0 ) {
				if( so.getProtocol().equals( OSCChannel.TCP )) {
					final ServerSocket ss = new ServerSocket( 0 );
					serverPort = ss.getLocalPort();
					ss.close();
				} else if( so.getProtocol().equals( OSCChannel.UDP )) {
					final DatagramSocket ds = new DatagramSocket();
					serverPort = ds.getLocalPort();
					ds.close();
				} else {
					throw new IllegalArgumentException( "Illegal protocol : " + so.getProtocol() );
				}
			}
		
			// loopback is sufficient here
			server	= new Server( AbstractApplication.getApplication().getName(),
								  new InetSocketAddress( "127.0.0.1", serverPort ), so );
//			for( int i = 0; i < collListeners.size(); i++ ) {
//				server.addListener( (ServerListener) collListeners.get( i ));
//			}
			server.addListener( this );
			if( dumpMode != kDumpOff ) dumpOSC( dumpMode );
			nw	= NodeWatcher.newFrom( server );
//System.err.println( "new nw " + nw.hashCode() );
//nw.VERBOSE	= true;
//			nw.start();
			
//			final List cmdArray = server.getOptions().toOptionList( serverPort );
//			for( int i = 0; i < cmdArray.size(); i++ ) {
//				System.out.println( i + ": \"" + cmdArray.get( i ) + "\"" );
//			}
			
			server.boot();
			return true;
		}
		catch( IOException e1 ) {
			printError( "boot", e1 );
		}
		return false;
	}
	
	private void createOutputConfig()
	{
		final String cfgName	= audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null );

		oCfg	= null;

		try {
			if( cfgName != null && audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).nodeExists( cfgName )) {
				oCfg	= new RoutingConfig( audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).node( cfgName ));
			}
		}
		catch( BackingStoreException e1 ) {
			printError( "createOutputConfig", e1 );
		}
	}
	
	private static void printError( String name, Throwable t )
	{
//System.err.print( name + " : " );
//t.printStackTrace();
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	public Action getDebugLoadDefsAction()
	{
		return new actionLoadDefsClass();
	}
	
	public Action getDebugNodeTreeAction()
	{
		return new actionNodeTreeClass();
	}

	public Action getDebugKillAllAction()
	{
		return new actionKillAllClass();
	}
	
	protected OSCMessage loadDefsMsg()
	{
		return new OSCMessage( "/d_loadDir", new Object[] { new File( "synthdefs" ).getAbsolutePath() });
	}

	private void createNewPlayer( Session doc )
	throws IOException
	{
		final SuperColliderPlayer	p;
//		final MeterListener			ml;
//		final Bus					b;
		final DocumentFrame			f;

		if( !mapDocsToPlayers.containsKey( doc )) {
//			p = new SuperColliderPlayer( doc, server, oCfg, volume );
			p = new SuperColliderPlayer( doc, server, oCfg );
			collPlayers.add( p );
			mapDocsToPlayers.put( doc, p );
			f = doc.getFrame();
			if( f != null ) f.playerCreated( p );
//			if( chanMeter && (f != null) ) {
////				p.addMeterListener( doc.getFrame() );
////				meterTimer.restart();
//				b = p.getInputBus();
//				if( b != null ) {
//					meterManager.addMeterListener( f, b, null );
//				}
//			}
		}
	}

	private void disposePlayer( Session doc )
	{
		final SuperColliderPlayer	p	= (SuperColliderPlayer) mapDocsToPlayers.remove( doc );
		final DocumentFrame			f	= doc.getFrame();

		if( f != null ) f.playerDestroyed( p );
//		if( f != null ) meterManager.removeMeterListener( f );
		if( p != null ) {
			collPlayers.remove( p );
			p.dispose();
//			if( chanMeter && collPlayers.isEmpty() ) meterTimer.stop();
		}
	}

// ------------- EventManager.Processor interface -------------

	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed.
	 */
	public void processEvent( BasicEvent e )
	{
		final Event		scle = (Event) e;
		Listener		listener;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
			listener = (Listener) elm.getListener( i );
			listener.clientAction( scle );
		}
	}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		if( server == null ) return;
	
		switch( e.getID() ) {
		case ServerEvent.STOPPED:	// --------------------------------------- stopped
			dispose();
			
			synchronized( collListeners ) {
				for( int i = 0; i < collListeners.size(); i++ ) {
					((ServerListener) collListeners.get( i )).serverAction( e );
				}
			}
			
			if( reboot ) {
				reboot = false;
//				final javax.swing.Timer timer = new javax.swing.Timer( 2000, new ActionListener() {
//					public void actionPerformed( ActionEvent e )
//					{
						boot();
//					}
//				});
//				timer.setRepeats( false );
//				timer.start();
			}
			break;
			
		case ServerEvent.RUNNING:	// --------------------------------------- started
			final Group		grpRoot = server.getDefaultGroup();
			final OSCBundle	bndl	= new OSCBundle();
			nw.register( grpRoot );
			try {
				if( !server.sendMsgSync( loadDefsMsg(), 4.0f )) {
				
					System.err.println( getResourceString( "errOSCTimeOut" ) + " : /d_loadDir" );
					return;
				}
				
				grpMaster		= Group.basicNew( server );
				grpGain			= Group.basicNew( server );
				grpLimiter		= Group.basicNew( server );
				grpMaster.setName( "Master" );
				grpGain.setName( "Gain" );
				grpLimiter.setName( "Limiter" );
				nw.register( grpMaster );
				nw.register( grpGain );
				nw.register( grpLimiter );
				bndl.addPacket( grpMaster.addToTailMsg( grpRoot ));
				bndl.addPacket( grpGain.addToHeadMsg( grpMaster ));
				bndl.addPacket( grpLimiter.addToTailMsg( grpMaster ));
				if( !limiter ) bndl.addPacket( grpLimiter.runMsg( false ));
				if( volume == 1f ) bndl.addPacket( grpGain.runMsg( false ));
				server.sendBundle( bndl );
				serverIsReady	= true;
				setOutputConfig();
		
				final de.sciss.app.DocumentHandler dh = AbstractApplication.getApplication().getDocumentHandler();
				for( int i = 0; i < dh.getDocumentCount(); i++ ) {
					createNewPlayer( (Session) dh.getDocument( i ));
				}
			}
			catch( IOException e1 ) {
				printError( "ServerEvent.RUNNING", e1 );
			}

			synchronized( collListeners ) {
				for( int i = 0; i < collListeners.size(); i++ ) {
					((ServerListener) collListeners.get( i )).serverAction( e );
				}
			}
			break;
			
		default:					// --------------------------------------- other
			synchronized( collListeners ) {
				for( int i = 0; i < collListeners.size(); i++ ) {
					((ServerListener) collListeners.get( i )).serverAction( e );
				}
			}
			break;
		}
	}

	// ------------ OSCRouter interface ------------

	public String oscGetPathComponent()
	{
		return OSC_SUPERCOLLIDER;
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
	
	public Object oscQuery_port()
	{
// schwachsinn, alles laeuft im event thread!
//		final Server s = server;
		return new Integer( server == null ? 0 : server.getAddr().getPort() );
	}

	public Object oscQuery_protocol()
	{
		return( server == null ? (Object) new Integer( 0 ) : (Object) server.getOptions().getProtocol() );
	}

	public Object oscQuery_running()
	{
//		final Server s = server;
//		return new Integer( (s != null && s.isRunning()) ? 1 : 0 );
		return new Integer( serverIsReady ? 1 : 0 );
	}

	public Object oscQuery_volume()
	{
		return new Float( getVolume() );
	}

	public void oscCmd_allocBus( RoutedOSCMessage rom )
	{
		int argIdx = 1;
		try {
			final Object rate;
			if( rom.msg.getArg( argIdx ).toString().equals( "audio" )) {
				rate = kAudioRate;
			} else if( rom.msg.getArg( argIdx ).toString().equals( "control" )) {
				rate = kControlRate;
			} else {
				OSCRoot.failedArgValue( rom, argIdx );
				return;
			}
			argIdx++;
			final Bus		b;
			final Server	s	= server;
			if( s != null ) {
				b = Bus.alloc( s, rate, rom.msg.getArgCount() > argIdx ?
					((Integer) rom.msg.getArg( argIdx )).intValue() : 1 );
			} else {
				b = null;
			}
			if( b != null ) {
				final Integer idx = new Integer( b.getIndex() );
				mapOSCBusses.put( idx, b );
				rom.replyDone( 1, new Object[] { idx });
			} else {
				rom.replyFailed();
			}
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
		catch( IOException e1 ) {
			OSCRoot.failed( rom, e1 );
		}
	}

	public void oscCmd_freeBus( RoutedOSCMessage rom )
	{
		try {
			final Bus b = (Bus) mapOSCBusses.remove( rom.msg.getArg( 1 ));
			if( b != null ) {
				b.free();
			} else {
				OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCBusNotFound" ));
			}
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
	}

	public void oscCmd_allocBuf( RoutedOSCMessage rom )
	{
		int argIdx = 1;
		try {
			final int numFrames	= ((Integer) rom.msg.getArg( argIdx )).intValue();
			final int numChannels;
			argIdx++;
			if( rom.msg.getArgCount() > argIdx ) {
				numChannels	= ((Integer) rom.msg.getArg( argIdx )).intValue();
			} else {
				numChannels	= 1;
			}
			final de.sciss.jcollider.Buffer b = de.sciss.jcollider.Buffer.alloc( server, numFrames, numChannels );
			if( b != null ) {
				final Integer num = new Integer( b.getBufNum() );
				mapOSCBuffers.put( num, b );
				rom.replyDone( 1, new Object[] { num });
			} else {
				rom.replyFailed();
			}
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
		catch( IOException e1 ) {
			OSCRoot.failed( rom, e1 );
		}
	}

	public void oscCmd_freeBuf( RoutedOSCMessage rom )
	{
		try {
			final de.sciss.jcollider.Buffer b = (de.sciss.jcollider.Buffer) mapOSCBuffers.remove( rom.msg.getArg( 1 ));
			if( b != null ) {
				b.free();
			} else {
				OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCBufNotFound" ));
			}
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
		catch( IOException e1 ) {
			OSCRoot.failed( rom, e1 );
		}
	}

	public void oscCmd_volume( RoutedOSCMessage rom )
	{
		try {
			setVolume( this, ((Number) rom.msg.getArg( 1 )).floatValue() );
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, 1 );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
	}

	public void oscCmd_boot( RoutedOSCMessage rom )
	{
		boot();
	}

	public void oscCmd_terminate( RoutedOSCMessage rom )
	{
		stop();
	}

// ---------------- DocumentListener interface ---------------- 

	public void documentAdded( de.sciss.app.DocumentEvent e )
	{
//		if( (server != null) && server.isRunning() ) {
		// only create player when graph has been initialized and defs are available
		// ; otherwise player will be created in serverAction() anyways!
		if( serverIsReady ) {
			try {
				createNewPlayer( (Session) e.getDocument() );
			}
			catch( IOException e1 ) {
				printError( "documentAdded", e1 );
			}
		}
	}

	public void documentRemoved( de.sciss.app.DocumentEvent e )
	{
		disposePlayer( (Session) e.getDocument() );
	}

	public void documentFocussed( de.sciss.app.DocumentEvent e ) {}

// -------------- internal classes --------------

	public static class Event
	extends BasicEvent
	{
		// --- ID values ---

		public static final int OUTPUTCONFIG	= 0;
		public static final int VOLUME			= 1;
		
		private final SuperColliderClient client;

		private Event( Object source, int id, long when, SuperColliderClient client )
		{
			super( source, id, when );
		
			this.client	= client;
		}

		public SuperColliderClient getClient()
		{
			return client;
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			if( oldEvent instanceof Event &&
				this.getSource() == oldEvent.getSource() &&
				this.getID() == oldEvent.getID() ) {
				
				// XXX beware, when the actionID and actionObj
				// are used, we have to deal with them here
				
				return true;

			} else return false;
		}
	}

	private class actionLoadDefsClass
	extends AbstractAction
	{
		public actionLoadDefsClass()
		{
			super( "Reload Synth Defs" );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( server != null ) {
				try {
					server.sendMsg( new OSCMessage( "/d_loadDir", new Object[] {
						new File( "synthdefs" ).getAbsolutePath() }));
				}
				catch( IOException e1 ) {
					printError( getValue( NAME ).toString(), e1 );
				}
			}
		}
	}

	private class actionKillAllClass
	extends AbstractAction
	{
		public actionKillAllClass()
		{
			super( "killall scsynth" );
		}

		public void actionPerformed( ActionEvent e )
		{
			try {
				Runtime.getRuntime().exec( "killall scsynth" );
			}
			catch( IOException e1 ) {
				e1.printStackTrace();
			}
		}
	}

	private class actionNodeTreeClass
	extends AbstractAction
	{
		public actionNodeTreeClass()
		{
			super( "View Node Graph" );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( (server != null) && (nw != null) ) {
//System.err.println( "server.hashCode() = "+server.hashCode()+ "; nw.hashCode() = "+nw.hashCode() );
//final List coll = nw.getAllNodes();
//for( int i = 0; i < coll.size(); i++ ) {
//	System.err.println( coll.get( i ).toString() );
//}
//				nw.setFireAllNodes( true );
//				nw.queryAllNodes( 4.0f, null );
				final NodeTreePanel ntp = new NodeTreePanel( nw, server.getDefaultGroup() );
				ntp.makeWindow().setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
//				ntp.getManager().VERBOSE = true;
//				nw.VERBOSE = true;
			}
		}
	}

// -------------------------- inner Listener interface --------------------------

	public interface Listener
	extends EventListener
	{
		public void clientAction( SuperColliderClient.Event e );
	}
}
