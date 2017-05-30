/*
 *  SuperColliderClient.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.BasicEvent;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.EventManager;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.session.DocumentFrame;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.jcollider.Buffer;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Constants;
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
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCChannel;
import de.sciss.net.OSCMessage;
import de.sciss.util.Param;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

public class SuperColliderClient
		implements OSCRouter, Constants, ServerListener, DocumentListener {

	private ServerOptions			so;
	protected Server				server				= null;
	private boolean					serverIsReady		= false;	// = running + defs have been loaded
	protected NodeWatcher			nw					= null;
	private final Preferences		audioPrefs;

	private static final int		DEFAULT_PORT		= 57109;	// our default server udp port

	private final List<ServerListener> collListeners		= new ArrayList<ServerListener>();
	
	protected final List<SuperColliderPlayer> collPlayers  = new ArrayList<SuperColliderPlayer>();
	private final Map<Session, SuperColliderPlayer> mapDocsToPlayers	= new HashMap<Session, SuperColliderPlayer>();
	
	protected RoutingConfig			oCfg				= null;
	private Preferences				oCfgNode			= null;
	private final DynamicPrefChangeManager oCfgDynPrefs;
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
	private final Map<Integer, Bus> mapOSCBusses		= new HashMap<Integer, Bus>();
	// Buffers that have been allocated by OSC clients
	// key = Integer( bufnum ) ; value = Buffer
	private final Map<Integer, Buffer> mapOSCBuffers	= new HashMap<Integer, Buffer>();

	private boolean					reboot				= false;
	
	private final MeterManager		meterManager;
	
	protected EventManager			elmClient					= null;

	protected static SuperColliderClient instance;

	public SuperColliderClient()
	{
		super();

		if( instance != null ) throw new IllegalStateException( "Only one instance allowed" );
		instance	= this;
	
		final Application				app			= AbstractApplication.getApplication();
		final Preferences				userPrefs	= app.getUserPrefs();
		final PreferenceChangeListener	oCfgListener;

		audioPrefs				= userPrefs.node( PrefsUtil.NODE_AUDIO );
		so						= new ServerOptions();
		so.setBlockAllocFactory( new ContiguousBlockAllocator.Factory() );	// deals better with fragmentation
		
		oCfgListener = new PreferenceChangeListener() {
			public void preferenceChange( PreferenceChangeEvent e )
			{
//				System.out.println( "DOING " + e.getKey() + " -> " + e.getNewValue() );
				outputConfigChanged();
			}
		};
		
		// note: we are using the DynamicPrefChangeManager instead of direct
		// registration with oCfgNode because the sucky Preferences class
		// is not using the EventThread
		oCfgDynPrefs = new DynamicPrefChangeManager( null, RoutingConfig.KEYS, oCfgListener, false );
		
		new DynamicPrefChangeManager( audioPrefs, new String[] { PrefsUtil.KEY_OUTPUTCONFIG },
		                              oCfgListener, false ).startListening();	// fires change and hence createOutputConfig()

//		osc = new OSCRouterWrapper( superRouter, this );
		osc = new OSCRouterWrapper( OSCRoot.getInstance(), this );
		
		meterManager = new MeterManager( this );
		
//		elmServer = new EventManager( new EventManager.Processor() {
//			public void processEvent( BasicEvent e )
//			{
////				serverAction( (ServerEvent) e );
//			}
//		});
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

	public SuperColliderPlayer getPlayerForDocument(Session doc) {
		return mapDocsToPlayers.get(doc);
	}

	public void addServerListener(ServerListener l) {
		synchronized (collListeners) {
			collListeners.add(l);
		}
	}

	public void removeServerListener(ServerListener l) {
		synchronized (collListeners) {
			collListeners.remove(l);
		}
	}
	
	public void addClientListener( Listener listener )
	{
		synchronized( this ) {
			if( elmClient == null ) {
				elmClient = new EventManager( new EventManager.Processor() {
					public void processEvent( BasicEvent e )
					{
						clientAction( (Event) e );
					}
				});
			}
			elmClient.addListener( listener );
		}
	}

	public void removeClientListener( Listener listener )
	{
		if( elmClient != null ) elmClient.removeListener( listener );
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
		if( (source != null) && (elmClient != null) ) {
			elmClient.dispatchEvent( new Event( source, Event.VOLUME, System.currentTimeMillis(), this ));
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
	
	protected void setOutputConfig()
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

	protected void outputConfigChanged()
	{
		SuperColliderPlayer p;
		
		final String		cfgID		= audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null );
		final Preferences	childPrefs	= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
		RoutingConfig		newCfg		= null;

//		oCfg = null;
		
		if( oCfgNode != null ) {
			oCfgDynPrefs.stopListening();
			oCfgDynPrefs.setPreferences( null );
			oCfgNode = null;
		}

		try {
			if( (cfgID != null) && childPrefs.nodeExists( cfgID )) {
				oCfgNode	= childPrefs.node( cfgID );
				newCfg		= new RoutingConfig( oCfgNode );
//				oCfgNode.addPreferenceChangeListener( oCfgListener );
				oCfgDynPrefs.setPreferences( oCfgNode );
				oCfgDynPrefs.startListening();
			}
		}
		catch( BackingStoreException e1 ) {
			printError( "createOutputConfig", e1 );
		}
		
		if( newCfg == null ) {
			newCfg = new RoutingConfig( "none", "none" );
		}
		
		if( (oCfg != null) && oCfg.equals( newCfg )) return;

//System.out.println( "CHANGED" );
//System.out.println( oCfg );
//System.out.println( newCfg );
		oCfg = newCfg;
		setOutputConfig();
		for (Object collPlayer : collPlayers) {
			p = (SuperColliderPlayer) collPlayer;
			//		p.setOutputConfig( oCfg, volume );
			p.setOutputConfig(oCfg);
		}
		if( elmClient != null ) {
			elmClient.dispatchEvent( new Event( instance, Event.OUTPUTCONFIG, System.currentTimeMillis(), instance ));
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

		for (Object doc : docs) {
			disposePlayer((Session) doc);
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

	public void dumpOSC(int mode) {
		this.dumpMode = mode;

		if (server != null) {
			server.dumpIncomingOSC(mode);
			server.dumpOutgoingOSC(mode);
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

	public boolean boot() {
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		if( (server != null) && (server.isRunning() || server.isBooting()) ) return false;
		
		dispose();
	
		String					val;
		int						serverPort;	// , idx;
		Param					p;
		final String			abCfgID		= audioPrefs.get( PrefsUtil.KEY_AUDIOBOX, AudioBoxConfig.ID_DEFAULT );
		final AudioBoxConfig	abCfg		= new AudioBoxConfig( audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES ).node( abCfgID ));

		p = Param.fromPrefs(audioPrefs, PrefsUtil.KEY_AUDIORATE, null);
		if (p != null) so.setSampleRate(p.val);
		so.setNumInputBusChannels(abCfg.numInputChannels);
		so.setNumOutputBusChannels(abCfg.numOutputChannels);
		p = Param.fromPrefs(audioPrefs, PrefsUtil.KEY_AUDIOBUSSES, null);
		if (p != null)
			so.setNumAudioBusChannels(Math.max(abCfg.numInputChannels + abCfg.numOutputChannels, (int) p.val));
		p = Param.fromPrefs(audioPrefs, PrefsUtil.KEY_SCMEMSIZE, null);
		if (p != null) so.setMemSize((int) p.val << 10);
		p = Param.fromPrefs(audioPrefs, PrefsUtil.KEY_SCBLOCKSIZE, null);
		if (p != null) so.setBlockSize((int) p.val);
		if (!abCfg.name.equals("Default")) so.setDevice(abCfg.name);
		so.setLoadDefs(false);
		so.setRendezvous(audioPrefs.getBoolean(PrefsUtil.KEY_SCRENDEZVOUS, false));

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
		p 			= Param.fromPrefs(audioPrefs, PrefsUtil.KEY_SCPORT, null);
		serverPort 	= p == null ? DEFAULT_PORT : (int) p.val;
		val 		= audioPrefs.get(PrefsUtil.KEY_SCPROTOCOL, OSCChannel.TCP);
		so.setProtocol(val);
		
//		so.setEnv( "SC_JACK_NAME", "Eisenkraut" );

		val = audioPrefs.get(PrefsUtil.KEY_SUPERCOLLIDERAPP, null);
		if (val == null) {
			System.err.println(getResourceString("errSCSynthAppNotFound"));
			return false;
		}
		Server.setProgram(val);

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
	
	protected static void printError( String name, Throwable t )
	{
//System.err.print( name + " : " );
//t.printStackTrace();
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	public Action getDebugLoadDefsAction()
	{
		return new ActionLoadDefs();
	}
	
	public Action getDebugNodeTreeAction()
	{
		return new ActionNodeTree();
	}

	public Action getDebugKillAllAction()
	{
		return new ActionKillAll();
	}

	protected OSCMessage loadDefsMsg()
			throws IOException {
		final InputStream is = getClass().getResourceAsStream("eisk-all.scsyndef");
		final int size = is.available();
		final byte[] data = new byte[size];
		is.read(data);
		is.close();
		return new OSCMessage("/d_recv", new Object[]{data});
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

	private void disposePlayer(Session doc) {
		final SuperColliderPlayer p = mapDocsToPlayers.remove(doc);
		final DocumentFrame f = doc.getFrame();

		if (f != null) f.playerDestroyed(p);
		if (p != null) {
			collPlayers.remove(p);
			p.dispose();
		}
	}

// ------------- EventManager.Processor interface -------------

	/*
	 *  This is called by the EventManager
	 *  if new events are to be processed.
	 */
	protected void clientAction( Event e )
	{
		Listener listener;
		
		for( int i = 0; i < elmClient.countListeners(); i++ ) {
			listener = (Listener) elmClient.getListener( i );
			listener.clientAction( e );
		}
	}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		if( server == null ) return;
	
		switch( e.getID() ) {
		case ServerEvent.STOPPED:	// --------------------------------------- stopped
			dispose();

			synchronized (collListeners) {
				for (ServerListener collListener : collListeners) {
					collListener.serverAction(e);
				}
			}

			if (reboot) {
				reboot = false;
				boot();
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
				for (ServerListener collListener : collListeners) {
					collListener.serverAction(e);
				}
			}
			break;
			
		default:					// --------------------------------------- other
			synchronized (collListeners) {
				for (ServerListener collListener : collListeners) {
					collListener.serverAction(e);
				}
			}
			break;
		}
	}

	// ------------ OSCRouter interface ------------

	public String oscGetPathComponent() {
		return OSC_SUPERCOLLIDER;
	}

	public void oscRoute(RoutedOSCMessage rom) {
		osc.oscRoute(rom);
	}

	public void oscAddRouter(OSCRouter subRouter) {
		osc.oscAddRouter(subRouter);
	}

	public void oscRemoveRouter(OSCRouter subRouter) {
		osc.oscRemoveRouter(subRouter);
	}

	public Object oscQuery_port() {
		return server == null ? 0 : server.getAddr().getPort();
	}

	public Object oscQuery_protocol() {
		return (server == null ? (Object) 0 : server.getOptions().getProtocol());
	}

	public Object oscQuery_running() {
		return serverIsReady ? 1 : 0;
	}

	public Object oscQuery_volume() {
		return getVolume();
	}

	public void oscCmd_allocBus(RoutedOSCMessage rom) {
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
			final Server s = server;
			if (s != null) {
				b = Bus.alloc(s, rate, rom.msg.getArgCount() > argIdx ?
						(Integer) rom.msg.getArg(argIdx) : 1);
			} else {
				b = null;
			}
			if (b != null) {
				final Integer idx = b.getIndex();
				mapOSCBusses.put(idx, b);
				rom.replyDone(1, new Object[]{idx});
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
			int i = (Integer) rom.msg.getArg(1);
			final Bus b = mapOSCBusses.remove(i);
			if( b != null ) {
				b.free();
			} else {
				OSCRoot.failed(rom.msg, OSCRoot.getResourceString("errOSCBusNotFound"));
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
			final int numFrames	= (Integer) rom.msg.getArg(argIdx);
			final int numChannels;
			argIdx++;
			if( rom.msg.getArgCount() > argIdx ) {
				numChannels	= (Integer) rom.msg.getArg(argIdx);
			} else {
				numChannels	= 1;
			}
			final de.sciss.jcollider.Buffer b = de.sciss.jcollider.Buffer.alloc( server, numFrames, numChannels );
			if (b != null) {
				final Integer num = b.getBufNum();
				mapOSCBuffers.put(num, b);
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

	public void oscCmd_freeBuf(RoutedOSCMessage rom) {
		try {
			int i = (Integer) rom.msg.getArg(1);
			final de.sciss.jcollider.Buffer b = mapOSCBuffers.remove(i);
			if (b != null) {
				b.free();
			} else {
				OSCRoot.failed(rom.msg, OSCRoot.getResourceString("errOSCBufNotFound"));
			}
		} catch (IndexOutOfBoundsException e1) {
			OSCRoot.failedArgCount(rom);
		} catch (IOException e1) {
			OSCRoot.failed(rom, e1);
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

	public void documentFocussed( de.sciss.app.DocumentEvent e ) { /* ignored */ }

// -------------- internal classes --------------

	@SuppressWarnings("serial")
	public static class Event
			extends BasicEvent {
		// --- ID values ---

		public static final int OUTPUTCONFIG	= 0;
		public static final int VOLUME			= 1;
		
		private final SuperColliderClient client;

		protected Event( Object source, int id, long when, SuperColliderClient client )
		{
			super( source, id, when );
		
			this.client	= client;
		}

		public SuperColliderClient getClient()
		{
			return client;
		}

		public boolean incorporate(BasicEvent oldEvent) {
			// XXX beware, when the actionID and actionObj
			// are used, we have to deal with them here
			return oldEvent instanceof Event &&
					this.getSource() == oldEvent.getSource() &&
					this.getID() == oldEvent.getID();
		}
	}

	@SuppressWarnings("serial")
	private class ActionLoadDefs
	extends AbstractAction
	{
		public ActionLoadDefs()
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

	@SuppressWarnings("serial")
	private class ActionKillAll
	extends AbstractAction
	{
		public ActionKillAll()
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

	@SuppressWarnings("serial")
	private class ActionNodeTree
	extends AbstractAction
	{
		public ActionNodeTree()
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
