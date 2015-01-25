/*
 *  SuperColliderPlayer.java
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

package de.sciss.eisenkraut.net;

import java.awt.EventQueue;
import java.net.SocketAddress;
import java.io.File;
import java.io.IOException;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.realtime.Transport;
import de.sciss.eisenkraut.realtime.TransportListener;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.session.SessionObject;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;
import de.sciss.eisenkraut.util.MapManager;

import de.sciss.app.AbstractApplication;
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;
import de.sciss.io.Span;
import de.sciss.jcollider.Buffer;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Control;
import de.sciss.jcollider.GraphElem;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.OSCResponderNode;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.Synth;
import de.sciss.jcollider.SynthDef;
import de.sciss.jcollider.UGen;
import de.sciss.util.Disposable;

public class SuperColliderPlayer
		implements de.sciss.jcollider.Constants,
		TransportListener, // RealtimeConsumer,
		SessionCollection.Listener, Disposable,
		TimelineListener, OSCRouter {

	private static final boolean DEBUG_FOLD		= false;
	
	private static final int DISKBUF_PAD		= 4;	// be sure to match with phasor synth def!!
	protected final int DISKBUF_SIZE;
	protected final int DISKBUF_SIZE_H;
	protected final int DISKBUF_SIZE_HM;

	private final int MIN_LOOP_LEN				= 4410;	// rather arbitrary ... we should better check that osc bundles don't overflow

	protected final Server			server;
	private final NodeWatcher		nw;
	private final Session			doc;
	protected final Transport		transport;
	
	private RoutingConfig			oCfg;
	protected volatile Context		ct	= null;

	private double					serverRate;
	private double					sourceRate;
	private double					srcFactor			= 1.0;
	protected long					playOffset; //			= -1;
	protected int					clock;
	
	private static final Span[]		emptySpans			= new Span[0];
	
	protected Span[][]				lastBufSpans		= new Span[][] { emptySpans, emptySpans };

	private final Group				grpRoot;
	private final Group				grpInput;
	private final Group				grpOutput;
	private final Bus				busPhasor;
	
	private final int				numInputChannels;		// XXX static for now
	private final int[][]			channelMaps;
	
	private final OSCResponderNode	trigResp;
	
	private final GroupSync			syncInput;
	private final GroupSync			syncOutput;
	private boolean					activeInput		= false;
	private boolean					activeOutput	= false;
	
	protected volatile int			trigNodeID		= -1;	// current synthPhasor ID or -1
	protected volatile OSCMessage	trigMsg			= null;	// most recently received /tr message
	
	private static final String		OSC_SUPERCOLLIDER	= "sc";
	private final OSCRouterWrapper	osc;
	
	private static final float		TIMEOUT			= 2f;

	public SuperColliderPlayer( final Session doc, final Server server, RoutingConfig oCfg )
	throws IOException
	{
		this.server			= server;
		this.doc			= doc;

		final AudioTrail	at			= doc.getAudioTrail();
		final Runnable		runTrigger;
		final SynthDef[]	defs;
		OSCBundle			bndl;
		
		transport			= doc.getTransport();
		nw					= NodeWatcher.newFrom( server );
		numInputChannels	= at.getChannelNum();			// XXX sync?
		channelMaps			= at.getChannelMaps();
		sourceRate			= doc.timeline.getRate();		// XXX sync?
		serverRate			= server.getSampleRate();
		
		DISKBUF_SIZE		= (Math.max( 44100, (int) sourceRate ) + DISKBUF_PAD) << 1;	// buffer size in frames
		DISKBUF_SIZE_H		= DISKBUF_SIZE >> 1;
		DISKBUF_SIZE_HM		= DISKBUF_SIZE_H - DISKBUF_PAD;
		
		bndl				= new OSCBundle();
		grpRoot				= Group.basicNew( server );
		grpRoot.setName( "Root-" + doc.getName() );
		nw.register( grpRoot );
		bndl.addPacket( grpRoot.addToHeadMsg( server.getDefaultGroup() ));
		grpInput			= Group.basicNew( server );
		grpInput.setName( "Input" );
		nw.register( grpInput );
		bndl.addPacket( grpInput.addToTailMsg( grpRoot ));
		grpOutput			= Group.basicNew( server );
		grpOutput.setName( "Output" );
		nw.register( grpOutput );
		bndl.addPacket( grpOutput.addToTailMsg( grpRoot ));
		bndl.addPacket( grpOutput.runMsg( false ));
		
		server.sendBundle( bndl );
		
		busPhasor			= Bus.audio( server );

		runTrigger			= new Runnable() {
			public void run()
			{
				final OSCMessage		msg		= trigMsg;	// this way we synchronize the access
				final int				nodeID	= ((Number) msg.getArg( 0 )).intValue();
				final int				nextClock, fill, bufOff;
				final long				pos, start;
				final OSCBundle			bndl2;
				final int				even;
				final Span[]			bufSpans;
				int						numCh;

				try {
					clock		= ((Number) msg.getArg( 2 )).intValue();
//System.err.println( "clock = " + clock );
					nextClock	= clock + 1;
					even		= nextClock & 1; // == 0;
					bndl2		= new OSCBundle();

					if( (ct == null) || (ct.synthPhasor == null) || (nodeID != ct.synthPhasor.getNodeID()) ) return;
					if( trigNodeID == -1 ) return;	// transport not running anymore
						pos		= nextClock * DISKBUF_SIZE_HM - ((1 - even) * DISKBUF_PAD) + playOffset;
						start	= Math.max( 0, pos );
						fill	= (int) (start - pos);
						bufOff	= even * DISKBUF_SIZE_H;
						if( fill > 0 ) {
							for( int j = 0; j < ct.bufsDisk.length; j++ ) {
								numCh = ct.bufsDisk[ j ].getNumChannels();
								bndl2.addPacket( ct.bufsDisk[ j ].fillMsg( bufOff * numCh, fill * numCh, 0.0f ));
							}
						}
						bufSpans = transport.foldSpans( new Span( start, pos + DISKBUF_SIZE_H ), MIN_LOOP_LEN );
						doc.getAudioTrail().addBufferReadMessages( bndl2, bufSpans, ct.bufsDisk, bufOff + fill );

						lastBufSpans[ even ] = bufSpans;
if( DEBUG_FOLD ) {
	System.out.println( "------C "+ nextClock + ", " + even + ", " + playOffset + ", " + pos );
for( int k = 0, m = bufOff + fill; k < bufSpans.length; k++ ) {
	System.out.println( "i = " + k + "; " + bufSpans[ k ] + " -> " + m );
		m += bufSpans[ k ].getLength();
	}
	System.out.println();
}
						if( !server.sync( bndl2, TIMEOUT )) {
							printTimeOutMsg( "bufUpdate" );
					}
				}
				catch( IOException e1 ) {
					printError( "Receive /tr", e1 );
				}
				catch( ClassCastException e2 ) {
					printError( "Receive /tr", e2 );
				}
			}
		};

		trigResp			= new OSCResponderNode( server, "/tr", new OSCListener() {
			public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
			{
				final int	nodeID	= ((Number) msg.getArg( 0 )).intValue();
				
				if( nodeID == trigNodeID ) {
					trigMsg	= msg;
					EventQueue.invokeLater( runTrigger );
				}
			}
		});
		
		defs = createInputDefs( channelMaps );
		if( defs != null ) {
			bndl	= new OSCBundle();
			for (SynthDef def : defs) {
				bndl.addPacket(def.recvMsg());
			}
			if( !server.sync( bndl, TIMEOUT )) {
				printTimeOutMsg( "defs" );
			}
		}
		
		srcFactor	= sourceRate / serverRate;
		updateSRC();
		setOutputConfig( oCfg );

		syncInput	= new GroupSync();
		syncOutput	= new GroupSync();
		
		transport.addTransportListener( this );
		doc.audioTracks.addListener( this );
		doc.timeline.addTimelineListener( this );

		osc			= new OSCRouterWrapper( doc, this );
	}

	private SynthDef[] createInputDefs(int[][] chMaps) {
		final int[] numInCh = new int[chMaps.length];
		final SynthDef[] defs;
		int numDefs = 0;

	numDefsLp:
		for (int[] chMap : chMaps) {
			numInCh[numDefs] = chMap.length;
			for (int j = 0; j < numDefs; j++) {
				if (numInCh[j] == numInCh[numDefs]) continue numDefsLp;
			}
			numDefs++;
		}
		
		defs = new SynthDef[ numDefs ];
		for( int i = 0; i < numDefs; i++ ) {
			final Control		ctrlI	= Control.ir( new String[] { "i_aInBf", "i_aOtBs", "i_aPhBs", "i_intrp" }, new float[] { 0f, 0f, 0f, 1f });
			final GraphElem		graph;
			
			if( numInCh[ i ] > 0 ) {
				final GraphElem	phase	= UGen.ar( "In", ctrlI.getChannel( "i_aPhBs" ));
				final GraphElem	bufRd	= UGen.ar( "BufRd", numInCh[ i ], ctrlI.getChannel( "i_aInBf" ), phase, UGen.ir( 0f ), ctrlI.getChannel( "i_intrp" ));
				graph	= UGen.ar( "Out", ctrlI.getChannel( "i_aOtBs" ), bufRd );
			} else {
				graph	= ctrlI;
			}
			defs[ i ]	= new SynthDef( "eisk-input" + numInCh[ i ], graph );
		}
		return defs;
	}

	private SynthDef[] createOutputDefs(int numOutputChannels) {
		final Control		ctrlI	= Control.ir( new String[] { "i_aInBs", "i_aOtBs" }, new float[] { 0f, 0f });
		final Control		ctrlK	= Control.kr( new String[] { "pos", "width", "orient", "volume" }, new float[] { 0f, 2f, 0f, 1f });
		final GraphElem		graph;
		final SynthDef		def;
		
		if( numOutputChannels > 0 ) {
			final GraphElem	in		= UGen.ar( "In", ctrlI.getChannel( "i_aInBs" ));
			final GraphElem	pan;
			if( numOutputChannels > 1 ) {
				pan					= UGen.ar( "PanAz", numOutputChannels, in, ctrlK.getChannel( "pos" ), ctrlK.getChannel( "volume" ),
																			   ctrlK.getChannel( "width" ), ctrlK.getChannel( "orient" ));
			} else {
				pan					= UGen.ar( "*", in, ctrlK.getChannel( "volume" ));
			}
			graph = UGen.ar( "Out", ctrlI.getChannel( "i_aOtBs" ), pan );
		} else {
			graph = UGen.array( ctrlI, ctrlK );
		}
		def	= new SynthDef( "eisk-pan" + numOutputChannels, graph );

		return new SynthDef[] { def };
	}
	
	public Session getDocument()
	{
		return doc;
	}

	public void dispose() {
		osc.remove();

		// we free the nodes even if the server is
		// not running because we may simply have lost
		// connection and in fact it's still playing!
		//		if( server.isRunning() ) {
		try {
			trigResp.remove();
			grpRoot.free();
			disposeServerStuff();
		} catch (IOException e1) {
			printError("dispose", e1);
		}
		doc.audioTracks.removeListener(this);
		doc.timeline.removeTimelineListener(this);
		transport.removeTransportListener(this);
	}

	private void disposeServerStuff()
			throws IOException {
		if (ct != null) {
			ct.dispose();
			ct = null;
		}
	}

	public Bus getInputBus() {
		if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

		if (ct != null) {
			return ct.busInternal;
		} else {
			return null;
		}
	}
	
	public GroupSync getInputSync()
	{
		return syncInput;
	}

	public GroupSync getOutputSync()
	{
		return syncOutput;
	}

//	public Bus getMeterSource()
//	{
//		synchronized( this ) {
//			if( ct != null ) {
//				return ct.busMeter;
//			} else {
//				return null;
//			}
//		}
//	}
//	
//	public void setMeterSource( Bus meterInput )
//	{
//		final int		numCh;
//		final OSCBundle	bndl;
//			
//		synchronized( this ) {
//			if( ct != null ) {
//				numCh		= Math.min( ct.numInputChannels, meterInput.getNumChannels() );
//				bndl		= new OSCBundle();
//				ct.busMeter	= meterInput == null ? ct.busInternal : meterInput;
//				for( int ch = 0; ch < numCh; ch++ ) {
//					bndl.addPacket( ct.synthsMeter[ ch ].setMsg( "i_aInBus", ct.busMeter.getIndex() + ch ));
//				}
//				server.sendBundle( bndl );
//			}
//		}
//	}
	
	// sync : attempts shared on DOOR_TRACKS
//	public void setOutputConfig( RoutingConfig oCfg, float volume )
	public void setOutputConfig( RoutingConfig oCfg )
	{
		this.oCfg	= oCfg;
//		this.volume	= volume;

		if( server.isRunning() ) {
			final boolean	wasRunning	= transport.isRunning();
			final double	rate		= transport.getRateScale();
			if( wasRunning ) {
				transport.stop();
			}
//			if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) {
//				return;
//			}
//			try {
//				System.out.println( "setOutputConfig : rebuildSynths" );

				rebuildSynths();
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TRACKS );
//			}
			if( wasRunning ) {
				transport.play( rate );
			}
		}
	}
	
//	public void addMeterListener( MeterListener ml )
//	{
//		synchronized( collMeterListeners )
//		{
//			if( !meterListening && keepMetering ) {
//				try {
//					meterListening = true;
//					grpMeter.run( true );
//				}
//				catch( IOException e1 ) {
//					printError( "Run Meter Group", e1 );
//				}
//			}
//			collMeterListeners.add( ml );
//		}
//	}
//	
//	public void removeMeterListener( MeterListener ml )
//	{
//		synchronized( collMeterListeners )
//		{
//			collMeterListeners.remove( ml );
//			if( meterListening && collMeterListeners.isEmpty() ) {
//				try {
//					grpMeter.run( false );
//					meterListening = false;
//				}
//				catch( IOException e1 ) {
//					printError( "Run Meter Group", e1 );
//				}
//			}
//		}
//	}

	public void setActiveInput( boolean onOff )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		if( onOff != activeInput ) {
			activeInput = onOff;
			if( transport.isRunning() ) return;
			
			if( activeInput ) {
				syncInput.activate( null );
			} else {
				syncInput.deactivate( null );
			}
		}
	}

	public void setActiveOutput( boolean onOff )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		if( onOff != activeOutput ) {
			activeOutput = onOff;
			if( transport.isRunning() ) return;
			
			final OSCBundle bndl = new OSCBundle();
			bndl.addPacket( grpOutput.runMsg( activeOutput ));
			if( activeOutput ) {
				syncOutput.activate( bndl );
			} else {
				syncOutput.deactivate( bndl );
			}
			try {
				server.sendBundle( bndl );
			}
			catch( IOException e1 ) {
				printError( "Run Output Group", e1 );
			}
		}
	}
	
//	public void setMetering( boolean onOff )
//	{
//		keepMetering = onOff;
//
//		try {
//			synchronized( collMeterListeners ) {
//				if( onOff ) {	
//					if( !meterListening && !collMeterListeners.isEmpty() ) {
//						meterListening = true;
//						grpMeter.run( true );
//					}
//				} else {
//					if( meterListening && collMeterListeners.isEmpty() ) {
//						grpMeter.run( false );
//						meterListening = false;
//					}
//				}
//			}
//		}
//		catch( IOException e1 ) {
//			printError( "Run Meter Group", e1 );
//		}
//	}
//	
//	/**
//	 *	@synchronization	must be called in event thread
//	 */
//	public void meterBang()
//	{
//		if( ct != null ) {
//			try {
////				if( meterListening ) server.sendMsg( ct.meterBangMsg );
//				if( meterListening ) server.sendBundle( ct.meterBangBndl );
//			}
//			catch( IOException e1 ) {} // don't print coz the frequency might be high
//		}
//	}
	
	protected static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}
	
	// note: includes call to addChannelMuteMessages
	// sync: must be called in EventThread
	private void rebuildSynths()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

//new Exception().printStackTrace();
		
//		synchronized( sync ) {
			try {
				server.sync( TIMEOUT ); // an n_free on a pausing node can crash scsynth otherwise (19-nov-07)
				grpRoot.deepFree();
				disposeServerStuff();
				
				if( oCfg == null ) return;
				
				ct = new Context( channelMaps, numInputChannels, oCfg.numChannels );

				final float			orient	= -oCfg.startAngle/360 * oCfg.numChannels;
				final SynthDef[]	defs;
				OSCBundle			bndl;

				defs = createOutputDefs( oCfg.numChannels );
				if( defs != null ) {
					bndl	= new OSCBundle();
					for (SynthDef def : defs) {
						bndl.addPacket(def.recvMsg());
					}
					if( !server.sync( bndl, TIMEOUT )) {
						printTimeOutMsg( "defs" );
					}
				}
				
				bndl	= new OSCBundle();
				for( int i = 0; i < ct.numFiles; i++ ) {
					bndl.addPacket( ct.bufsDisk[ i ].allocMsg() );
				}

				for( int ch = 0; ch < ct.numInChans; ch++ ) {
					bndl.addPacket( ct.synthsPan[ ch ].newMsg( grpOutput, new String[] {
						"i_aInBs",					   "i_aOtBs",		     "orient" }, new float[] {
						ct.busInternal.getIndex() + ch, ct.busPan.getIndex(), orient  }, kAddToTail ));
					nw.register( ct.synthsPan[ ch ]);
				}
				for( int ch = 0; ch < oCfg.numChannels; ch++ ) {
					if( oCfg.mapping[ ch ] < server.getOptions().getNumOutputBusChannels() ) {
						bndl.addPacket( ct.synthsRoute[ ch ].newMsg( grpOutput, new String[] {
							"i_aInBs",				   "i_aOtBs"       }, new float[] {
							ct.busPan.getIndex() + ch, oCfg.mapping[ ch ]}, kAddToTail ));
						nw.register( ct.synthsRoute[ ch ]);
					}
				}

				addChannelPanMessages( bndl );
				addChannelMuteMessages( bndl );
				if( !server.sync( bndl, TIMEOUT )) {
					printTimeOutMsg( "alloc" );
				}
				ct.bufsAllocated = true;
			}
			catch( IOException e1 ) {
				printError( "rebuildSynths", e1 );
			}
//		}
	}
	
//	private void addBufferReadMessages( OSCBundle bndl, Span span, int numFrames, int offset )
//	{
//		SampledChunk	ts;
//		int				len;
//	
//		
//	
//		for( int i = 0; i <  tl.size(); i++ ) {
//			ts			= tl.get( i );
//			len			= (int) Math.min( numFrames, ts.getLength() );
//			if( len > 0 ) {
//// MMM
////				bndl.addPacket( ct.bufDisk.readMsg( ts.f.getFile().getAbsolutePath(), ts.offset, len, offset ));
//				offset     += len;
//				numFrames  -= len;
//			}
//		}
//		if( numFrames > 0 ) {	// zero the rest
//			bndl.addPacket( ct.bufDisk.fillMsg(
//				offset * ct.bufDisk.getNumChannels(), numFrames * ct.bufDisk.getNumChannels(), 0.0f ));
//		}
//	}

	// sync : attempts shared on DOOR_TRACKS
	private void addChannelMuteMessages( OSCBundle bndl )
	{
//		Object	o;
		boolean	audible;

		if( oCfg == null ) return;
	
//		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
//		try {
			if( doc.audioTracks.size() != ct.numInChans ) {
				Server.getPrintStream().println( "Input channel mismatch!" );
				return;
			}
			for( int ch = 0; ch < ct.numInChans; ch++ ) {
//				o = doc.audioTracks.get( ch ).getMap().getValue( SessionObject.MAP_KEY_FLAGS );
//				if( (o != null) && (o instanceof Number) ) {
//					muted = (((Number) o).intValue() & (SessionObject.FLAGS_MUTE | SessionObject.FLAGS_VIRTUALMUTE)) != 0;
//				} else {
//					muted = false;
//				}
				audible = ((AudioTrack) doc.audioTracks.get( ch )).isAudible();
				bndl.addPacket( ct.synthsPan[ ch ].runMsg( audible ));
			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRACKS );
//		}
	}

	// sync : attempts shared on DOOR_TRACKS
	private void addChannelPanMessages( OSCBundle bndl )
	{
		Object		o;
		MapManager	map;
		float		pos, width;
		
		if( oCfg == null ) return;
	
//		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
//		try {
			if( doc.audioTracks.size() != ct.numInChans ) {
				Server.getPrintStream().println( "Input channel mismatch!" );
				return;
			}
			for( int ch = 0; ch < ct.numInChans; ch++ ) {
				map	= doc.audioTracks.get( ch ).getMap();
				o	= map.getValue( AudioTrack.MAP_KEY_PANAZIMUTH );
				if( (o != null) && (o instanceof Number) ) {
					pos	= ((Number) o).floatValue() / 180;
					pos	= pos < 0.0f ? 2.0f - ((-pos) % 2.0f) : pos % 2.0f;
				} else {
					pos	= 0.0f;
				}
				o	= map.getValue( AudioTrack.MAP_KEY_PANSPREAD );
				if( (o != null) && (o instanceof Number) ) {
					width		= ((Number) o).floatValue();
//System.out.println( "width in : " + width );
					if( width <= 0.0f ) {
						width	= Math.max( 1.0f, width + 2.0f );
					} else {
						width	= Math.min( 1.0f, width ) * (oCfg.numChannels - 2) + 2.0f;
					}
				} else {
					width	= 2.0f;
				}
//System.out.println( "width out : " + width );
				
				bndl.addPacket( ct.synthsPan[ ch ].setMsg(
					new String[] { "pos", "width" }, new float[] { pos, width }));
			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRACKS );
//		}
	}

	protected void printTimeOutMsg( String loc )
	{
		Server.getPrintStream().println( getResourceString( "errOSCTimeOut" ) + " : " + loc );
	}

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( "errOSCTimeOut" );
	}

	private void updateSRC()
	{
//		srcFactor	= sourceRate / serverRate;
		doc.getFrame().setSRCEnabled( srcFactor != 1.0 );
	}

	// ------------ OSCRouter interface ------------

	public String oscGetPathComponent()
	{
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

	public Object oscQuery_inputGroup() {
		return grpInput.getNodeID();
	}

	public Object oscQuery_outputGroup() {
		return grpOutput.getNodeID();
	}

	public Object oscQuery_diskBusIndex() {
		return ((ct == null) ? null : ct.busInternal.getIndex());
	}

	public Object oscQuery_diskBusNumChannels() {
		return ((ct == null) ? null : ct.busInternal.getNumChannels());
	}

	public Object oscQuery_panBusIndex() {
		return ((ct == null) ? null : ct.busPan.getIndex());
	}

	public Object oscQuery_panBusNumChannels() {
		return ((ct == null) ? null : ct.busPan.getNumChannels());
	}

	// "createNRTFile", (String) fileName, (int) audioBusOffset, (int) controlBusOffset, (int) bufferOffset, (float) serverRate
	// ; audio is written to <diskBusNumChannels> channels, beginning at <audioBusOffset>
	public void oscCmd_createNRTFile(RoutedOSCMessage rom) {
		final SynthDef[]		defs;
		final Buffer[]			bufsDisk;
		final Synth[]			synthsBufRd;
		final Bus				busInternal, busPh;
		final Span				span				= doc.timeline.getSelectionSpan();
		final AudioTrail		at					= doc.getAudioTrail();
		final long				nrtPlayOffset		= span.start;
		final Span[]			bufSpans			= new Span[ 1 ];
		final Group				nrtGrpRoot, nrtGrpInput;
		final Synth				synthPhasor;
		final float				realRate;
		final float				interpolation;
		final double			nrtServerRate;
		final float				rate				= 1.0f;
		Server					nrtServer			= null;
		NRTFile					f					= null;
		int						argIdx				= 1;
		int						audioBusOffset, bufferOffset; //, controlBusOffset;
		OSCBundle				bndl;
		double					time				= 0.0;
		boolean					even;
		int						nrtClock;
		long					pos					= nrtPlayOffset;

		if (ct == null) {
			try {
				rom.replyFailed(1);
			} catch (IOException e11) {
				OSCRoot.failed(rom, e11);
			}
		}
	
		try {
			f				= NRTFile.openAsWrite( new File( rom.msg.getArg( argIdx ).toString() ));
			argIdx++;
			audioBusOffset	= ((Number) rom.msg.getArg( argIdx )).intValue();
			argIdx++;
			argIdx++;
			bufferOffset	= ((Number) rom.msg.getArg( argIdx )).intValue();
			argIdx++;
			nrtServerRate		= ((Number) rom.msg.getArg( argIdx )).doubleValue();
			
			nrtServer			= new Server( "nrt" );
			
			f.write( SuperColliderClient.getInstance().loadDefsMsg() );
			
			defs			= createInputDefs( ct.chanMaps ); // ct.numInputChannels
			if( defs != null ) {
				for (SynthDef def : defs) {
					f.write(def.recvMsg());
				}
			}

			srcFactor			= sourceRate / nrtServerRate;
			realRate			= (float) (rate * srcFactor);
			interpolation		= realRate == 1.0f ? 1f : 3f;
		
			nrtGrpRoot				= Group.basicNew( nrtServer );
			f.write( nrtGrpRoot.addToHeadMsg( nrtServer.getDefaultGroup() ));
			nrtGrpInput			= Group.basicNew( nrtServer );
			f.write( nrtGrpInput.addToTailMsg( nrtGrpRoot ));

			synthsBufRd				= new Synth[ ct.numFiles ];
			busInternal				= new Bus( nrtServer, kAudioRate, audioBusOffset, ct.numInChans );
			audioBusOffset		   += busInternal.getNumChannels();
			busPh				= new Bus( nrtServer, kAudioRate, audioBusOffset );
			audioBusOffset		   += busPh.getNumChannels();
			bufsDisk				= new Buffer[ ct.numFiles ];
			for( int i = 0; i < ct.numFiles; i++ ) {
				bufsDisk[ i ]		= new Buffer( nrtServer, DISKBUF_SIZE, ct.chanMaps[ i ].length, bufferOffset++ );
				f.write( bufsDisk[ i ].allocMsg() );
			}

			for( int i = 0; i < ct.numFiles; i++ ) {
				synthsBufRd[ i ]	= Synth.basicNew( "eisk-input" + ct.chanMaps[ i ].length, nrtServer );
			}
			synthPhasor	= Synth.basicNew( "eisk-phasor", nrtServer );

			for( nrtClock = 0, even = true;; nrtClock++, even = !even ) {
				if( even ) {
					pos = nrtClock * DISKBUF_SIZE_HM - DISKBUF_PAD + nrtPlayOffset;
				} else {
					pos = nrtClock * DISKBUF_SIZE_HM + nrtPlayOffset;
				}
				if( pos >= span.stop ) break;
				f.setTime( time );
				bndl				= new OSCBundle( time );
//				if( pos >= DISKBUF_PAD ) {
				if( pos < 0 ) {
					for (Buffer aBufsDisk : bufsDisk) {
						bndl.addPacket(aBufsDisk.fillMsg(0, DISKBUF_PAD * aBufsDisk.getNumChannels(), 0.0f));
					}
					pos += DISKBUF_PAD;
				}
//					bufSpans[ 0 ] = new Span( pos - DISKBUF_PAD, pos - DISKBUF_PAD + DISKBUF_SIZE_H );
				bufSpans[ 0 ] = new Span( pos, pos + DISKBUF_SIZE_H );
				at.addBufferReadMessages( bndl, bufSpans, bufsDisk, even ? 0 : DISKBUF_SIZE_H );
				f.write( bndl );
				
				if( nrtClock == 0 ) {
					for( int i = 0, off = 0; i < ct.numFiles; i++ ) {
						f.write( synthsBufRd[ i ].newMsg( nrtGrpInput, new String[] {
							"i_aInBf",	               "i_aOtBs",                    "i_aPhBs",            "i_intrp" }, new float[] {
							bufsDisk[ i ].getBufNum(), busInternal.getIndex() + off, busPh.getIndex(), interpolation }
						));
						off += ct.chanMaps[ i ].length;
					}

					if( ct.numFiles > 0 ) {
						f.write( synthPhasor.newMsg( nrtGrpInput, new String[] {
							"i_aInBf",				   "rate",   "i_aPhBs"          }, new float[] {
							bufsDisk[ 0 ].getBufNum(), realRate, busPh.getIndex() }));
					}
					
				} else {
					time = (nrtClock * DISKBUF_SIZE_HM / sourceRate) + 0.1;	// a bit beyond that spot to avoid rounding errors
				}
			}
			
			time = span.getLength() / sourceRate;
			f.setTime( time );
			f.write( nrtGrpRoot.freeMsg() );
			for (Buffer aBufsDisk : bufsDisk) {
				f.write(aBufsDisk.freeMsg());
			}
			
			f.close();
			f = null;

			try {
				rom.replyDone( 1, new Object[0] );
			}
			catch( IOException e11 ) {
				OSCRoot.failed(rom, e11);
			}
		} catch (ClassCastException e1) {
			OSCRoot.failedArgType(rom, argIdx);
		} catch (IndexOutOfBoundsException e1) {
			OSCRoot.failedArgCount(rom);
		} catch (IOException e1) {
			e1.printStackTrace();
			try {
				rom.replyFailed(1);
			} catch (IOException e11) {
				OSCRoot.failed(rom, e11);
			}
		} finally {
			if (nrtServer != null) nrtServer.dispose();
			if (f != null) f.dispose();
		}
	}

// ------------- TimelineListener interface -------------

	public void timelineChanged(TimelineEvent e) {
		final double newSrcFactor;

		sourceRate = doc.timeline.getRate();
		newSrcFactor = sourceRate / serverRate;
		if (newSrcFactor != srcFactor) {
			srcFactor = newSrcFactor;
			updateSRC();
		}
	}

	public void timelineSelected(TimelineEvent e) { /* ignored */ }

	public void timelinePositioned(TimelineEvent e) { /* ignored */ }

	public void timelineScrolled(TimelineEvent e) { /* ignored */ }

// ------------- TransportListener interface -------------

	public void transportStop(Transport t, long pos) {
		trigNodeID = -1;
		if (!server.isRunning() || (ct == null)) return;

		try {
			trigResp.remove();
			final OSCBundle bndl = new OSCBundle();
			bndl.addPacket(grpInput.freeAllMsg());
			if (!activeOutput) {
				bndl.addPacket(grpOutput.runMsg(false));
				syncOutput.deactivate(bndl);
			}
			if (!activeInput) {
				syncInput.deactivate(bndl);
			}
			server.sendBundle(bndl);
		} catch (IOException e1) {
			printError("transportStop", e1);
		}
	}

	// XXX sync
	public void transportPosition(Transport t, long pos, double rate) {
		transportStop(t, pos);
		transportPlay(t, pos, rate);
	}
	
	// irgendwie noch nicht so 100% fertig, manchmal scheinen buffer updates
	// nicht korrekt (aktuell spielende buffer haelfte -> anschliessend alles ok)
	public void transportReadjust( Transport t, long readjusted, double rate )
	{
		final OSCBundle	bndl;
		Span[]			bufSpans;
		long			pos, start;
		int				even, nextClock, fill, bufOff, numCh;

//		pos = nextClock * DISKBUF_SIZE_HM - ((1 - even) * DISKBUF_PAD) + playOffset;
		
		playOffset = readjusted;
		// now refresh dem buffers to make sure they reflect the new loop!
			bndl		= new OSCBundle();
			for( int i = 0; i < 2; i++ ) {
				nextClock = clock + i;
				even	= nextClock & 1;
//				pos 	= (clock + even) * DISKBUF_SIZE_HM - ((1 - even) * DISKBUF_PAD) + playOffset;
				pos		= nextClock * DISKBUF_SIZE_HM - ((1 - even) * DISKBUF_PAD) + playOffset;
				start	= Math.max( 0, pos );
				fill	= (int) (start - pos);
				bufOff	= even * DISKBUF_SIZE_H;
				bufSpans = t.foldSpans( new Span( start, pos + DISKBUF_SIZE_H ), MIN_LOOP_LEN );
checkSpans:		if( bufSpans.length == lastBufSpans[ even ].length ) {
					for( int j = 0; j < bufSpans.length; j++ ) {
						if( !bufSpans[ j ].equals( lastBufSpans[ even ][ j ])) break checkSpans;
					}
					continue;
				}
				if( fill > 0 ) {
					for( int j = 0; j < ct.bufsDisk.length; j++ ) {
						numCh = ct.bufsDisk[ j ].getNumChannels();
						bndl.addPacket( ct.bufsDisk[ j ].fillMsg( bufOff * numCh, fill * numCh, 0.0f ));
					}
				}
				doc.getAudioTrail().addBufferReadMessages( bndl, bufSpans, ct.bufsDisk, bufOff + fill );
			
if( DEBUG_FOLD ) {
	System.out.println( "------A " + nextClock + ", " + even + ", " + playOffset + ", " + pos );
	for( int k = 0, m = bufOff + fill; k < bufSpans.length; k++ ) {
		System.out.println( "i = " + k + "; " + bufSpans[ k ] + " -> " + m );
		m += bufSpans[ k ].getLength();
	}
}
				lastBufSpans[ even ] = bufSpans;
			}
			if( bndl.getPacketCount() > 0 ) {
//System.out.println();
				try {
					if( !server.sync( bndl, TIMEOUT )) {
						printTimeOutMsg( "readjust" );
					}
				}
				catch( IOException e1 ) {
					printError( "transportPlay", e1 );
			}
		}
	}
	
	// sync : shared on MTE
	public void transportPlay( Transport t, long pos, double rate )
	{
		final float			realRate;
		final float			interpolation;
		final Span[]		bufSpans;
		final long			start;
		final int			fill;
		OSCBundle			bndl;

		realRate			= (float) (rate * srcFactor);
		interpolation		= realRate == 1.0f ? 1f : 3f;

//		synchronized( sync ) {
			if( !server.isRunning() ) return;
			if( ct == null ) {	// as of oct '05 may be null if lockmanager timeout in setOutputConfig occurs
//				if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) {
//System.err.println( "OH NO!" );
//					return;
//				}
//				try {
				System.out.println( "transportPlay : rebuildSynths" );
					rebuildSynths();
					if( ct == null ) return;
//				}
//				finally {
//					doc.bird.releaseShared( Session.DOOR_TRACKS );
//				}
			}
			
//			if( !doc.bird.attemptShared( Session.DOOR_MTE, 500 )) return;
			try {
				bndl	= new OSCBundle();
				start	= Math.max( 0, pos - DISKBUF_PAD );
				fill	= (int) (start + DISKBUF_PAD - pos);
				if( fill > 0 ) {
					for( int i = 0; i < ct.bufsDisk.length; i++ ) {
						bndl.addPacket( ct.bufsDisk[ i ].fillMsg( 0, fill * ct.bufsDisk[ i ].getNumChannels(), 0.0f ));
					}
				}
				bufSpans = t.foldSpans( new Span( start, pos - DISKBUF_PAD + DISKBUF_SIZE ), MIN_LOOP_LEN );
				doc.getAudioTrail().addBufferReadMessages( bndl, bufSpans, ct.bufsDisk, fill );

if( DEBUG_FOLD ) {
	System.out.println( "------P "+ clock + ", X, " + playOffset + ", " + pos );
	for( int k = 0, m = fill; k < bufSpans.length; k++ ) {
		System.out.println( "i = " + k + "; " + bufSpans[ k ] + " -> " + m );
		m += bufSpans[ k ].getLength();
	}
	System.out.println();
}
				lastBufSpans[ 0 ] = emptySpans;
				lastBufSpans[ 1 ] = emptySpans;
				if( !server.sync( bndl, TIMEOUT )) {
					printTimeOutMsg( "play" );
					return;
				}
			}
			catch( IOException e1 ) {
				printError( "transportPlay", e1 );
			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_MTE );
//			}
			
			bndl		= new OSCBundle();
			bndl.addPacket( grpInput.freeAllMsg() );
			ct.newInputSynths();	// re-create synthsBufRd and synthPhasor
			for( int i = 0, off = 0; i < ct.numFiles; i++ ) {
				bndl.addPacket( ct.synthsBufRd[ i ].newMsg( grpInput, new String[] {
					"i_aInBf",	                  "i_aOtBs",                       "i_aPhBs",            "i_intrp" }, new float[] {
					ct.bufsDisk[ i ].getBufNum(), ct.busInternal.getIndex() + off, busPhasor.getIndex(), interpolation }
				));
				nw.register( ct.synthsBufRd[ i ]);
				off += ct.chanMaps[ i ].length;
			}
			if( ct.numFiles > 0 ) {
				bndl.addPacket( ct.synthPhasor.newMsg( grpInput, new String[] {
					"i_aInBf",					  "rate",   "i_aPhBs"          }, new float[] {
					ct.bufsDisk[ 0 ].getBufNum(), realRate, busPhasor.getIndex() }));
				nw.register( ct.synthPhasor );
			}
			bndl.addPacket( grpOutput.runMsg( true ));

			playOffset	= pos;
			clock		= 0;
			try {
				trigResp.add();
				if( !activeInput ) syncInput.activate( bndl );
				if( !activeOutput ) syncOutput.activate( bndl );
				trigNodeID = ct.synthPhasor.getNodeID();
				server.sendBundle( bndl );
			}
			catch( IOException e1 ) {
				printError( "transportPlay", e1 );
			}
//		} // synchronized( sync )
	}
	
	public void transportQuit( Transport t )
	{
		trigNodeID	= -1;
	}

// -------------- SessionCollection.Listener classes --------------

	public void sessionObjectMapChanged(SessionCollection.Event e) {
		if (server.isRunning() && (ct != null)) {
			OSCBundle bndl = null;

			if (e.setContains(AudioTrack.MAP_KEY_PANAZIMUTH) ||
					e.setContains(AudioTrack.MAP_KEY_PANSPREAD)) {

				bndl = new OSCBundle();
				addChannelPanMessages(bndl);

			} else if (e.setContains(SessionObject.MAP_KEY_FLAGS)) {
				bndl = new OSCBundle();
				addChannelMuteMessages(bndl);
			}
			if ((bndl != null) && (bndl.getPacketCount() > 0)) {
				try {
					server.sendBundle(bndl);
				} catch (IOException e1) {
					printError("Set Channel Status", e1);
				}
			}
		}
	}

	public void sessionCollectionChanged(SessionCollection.Event e) { /* XXX should react (well realtime host does) */ }

	public void sessionObjectChanged(SessionCollection.Event e) { /* ignored */ }

// -------------- internal classes --------------

	private class Context
	{
		protected final Synth[]		synthsBufRd;	// buffer readers for all parallel files
		protected final Synth[]		synthsPan;		// for each input channel a pan synth with numOutputs output channels
		protected final Synth[]		synthsRoute;	// for each pan output one route to the audio interface channel
		protected Synth				synthPhasor;
		protected final Buffer[]	bufsDisk;
		protected final Bus			busInternal;	// the buffer-reader writes to this bus (numInputChannels)
		protected final Bus			busPan;

		protected final int			numFiles;
		protected final int			numInChans;		// sum over all files
		protected final int[][]		chanMaps;		// re audio input files

		protected boolean			bufsAllocated	= false;
		private boolean				disposed		= false;

		/*
		 *	@throws	IOException	if the server ran out of busses
		 *						or buffers
		 */
		protected Context(int[][] channelMaps, int numInputChannels, int numConfigOutputs)
				throws IOException {

			this.chanMaps = channelMaps;
			numFiles = channelMaps.length;
			this.numInChans = numInputChannels;
		
			synthsBufRd				= new Synth[ numFiles ];
			synthsPan				= new Synth[ numInputChannels ];
			for( int i = 0; i < numInputChannels; i++ ) {
				synthsPan[ i ]		= Synth.basicNew( "eisk-pan" + numConfigOutputs, server );
			}
			synthsRoute				= new Synth[ numConfigOutputs ];
			for( int i = 0; i < numConfigOutputs; i++ ) {
				synthsRoute[ i ]	= Synth.basicNew( "eisk-route", server );
			}

			busInternal				= Bus.audio( server, numInputChannels );
			busPan					= Bus.audio( server, numConfigOutputs );

			if( (busInternal == null) || (busPan == null) ) {
				if( busInternal != null ) busInternal.free();
				if( busPan != null ) busPan.free();
				throw new IOException( getResourceString("scErrNoBuses"));
			}

			bufsDisk				= new Buffer[ numFiles ];
			for( int i = 0; i < numFiles; i++ ) {
				bufsDisk[ i ]		= new Buffer( server, DISKBUF_SIZE, channelMaps[ i ].length );
				if( bufsDisk[ i ] == null ) {
					for( int j = 0; j < i; j++ ) bufsDisk[ j ].freeMsg(); // cleans up allocator!
					throw new IOException( getResourceString( "scErrNoBuffers" ));
				}
			}
		}
		
		protected void newInputSynths()
		{
			for( int i = 0; i < numFiles; i++ ) {
				synthsBufRd[ i ]	= Synth.basicNew( "eisk-input" + chanMaps[ i ].length, server );
			}
			synthPhasor	= Synth.basicNew( "eisk-phasor", server );
		}

		protected void dispose()
				throws IOException {

			if (disposed) throw new IllegalStateException("Double disposal");
			disposed = true;

			busInternal.free();
			busPan.free();

			final OSCBundle bndl = new OSCBundle();
			for (Buffer aBufsDisk : bufsDisk) bndl.addPacket(aBufsDisk.freeMsg());
			if (bufsAllocated) {
				bufsAllocated = false;
				if ((bndl.getPacketCount() > 0) && server.isRunning() && !server.sync(bndl, TIMEOUT)) {
					printTimeOutMsg("dispose");
				}
			}
		}
	}
}
