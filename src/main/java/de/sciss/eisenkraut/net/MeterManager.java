/*
 *  MeterManager.java
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.gui.PeakMeterView;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Constants;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.OSCResponderNode;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;
import de.sciss.jcollider.Synth;
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCMessage;

public class MeterManager
		implements OSCResponderNode.Action, Constants, ServerListener, ActionListener,
		EventManager.Processor {

	private List					collAllClients		= new ArrayList();
	private List					collActiveClients	= new ArrayList();
	private Map						mapClients			= new HashMap();	// key = MeterListener, value = MeterClient

	private Server					server				= null;
	
	private Bus						bus					= null;
	private Group					grp					= null;
	
	private int						numCtrlChans		= 0;
	
	private OSCBundle				meterBangBndl		= null;
	private OSCResponderNode		resp				= null;

	private final Timer				meterTimer;
	private final SuperColliderClient sc;
	
	private int						numTask				= 0;
	
	private final EventManager		elm;

	public MeterManager( SuperColliderClient sc )
	{
		this.sc		= sc;
		meterTimer	= new javax.swing.Timer( 33, this );
		elm			= new EventManager( this );
		sc.addServerListener( this );
	}
	
	public void dispose()
	{
		sc.removeServerListener( this );
		disposeServer();
	}

	private void meterBang()
	{
		if( (server != null) && (meterBangBndl != null) ) {
			try {
				server.sendBundle( meterBangBndl );
			}
			catch( IOException e1 ) { /* don't print coz the frequency might be high */ }
		}
	}

	// ------------- ActionListener interface -------------

	public void actionPerformed( ActionEvent e )
	{
		meterBang();
	}

	// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		switch( e.getID() ) {
		case ServerEvent.STOPPED:
			setServer( null );
			break;
			
		case ServerEvent.RUNNING:
			setServer( e.getServer() );
			break;
			
		default:
			break;
		}
	}

	// ----------------- OSCResponderNode.Action interface -----------------
	
	public void respond( OSCResponderNode r, OSCMessage msg, long time )
	{
		elm.dispatchEvent( new Event( r, msg, time ));
	}

	// ----------------- EventManager.Processor interface -----------------

	public void processEvent( BasicEvent be )
	{
		final Event			e			= (Event) be;
		final OSCMessage	msg			= e.msg;
		final int			busIndex	= ((Number) msg.getArg( 0 )).intValue();
		final int			numVals		= ((Number) msg.getArg( 1 )).intValue();
// getWhen doesn't provide a valid value i think
//		final long			time		= e.getWhen(); 
		final long			time		= System.currentTimeMillis(); 
		Client				mc;	
	
		if( (bus == null) || (busIndex != bus.getIndex()) ) return;

		for( int i = 0; i < collActiveClients.size(); i++ ) {
			mc	= (Client) collActiveClients.get( i );
			if( !mc.task ) continue;
			for( int j = 0, k = 0, m = numVals + 2, off = mc.cOffset + 2; (k < mc.cNum) && (off < m); j++ ) {
				if( mc.channels[ j ] >= 0 ) {
					mc.peakRMSPairs[ k++ ] = ((Number) msg.getArg( off++ )).floatValue();
					mc.peakRMSPairs[ k++ ] = ((Number) msg.getArg( off++ )).floatValue();
				} else {
					mc.peakRMSPairs[ k++ ] = 0f;
					mc.peakRMSPairs[ k++ ] = 0f;
					off += 2;
				}
			}
			mc.view.meterUpdate( mc.peakRMSPairs, 0, time );
		}
	}

	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	private void disposeServer()
	{
		Client mc;

		meterTimer.stop();
		
		if( resp != null ) resp.remove();
	
		if( bus != null ) {
			bus.free();
			bus = null;
		}
		grp	= null;
		
		if( server == null ) return;
		
		for( int i = 0; i < collAllClients.size(); ) {
			mc = (Client) collAllClients.get( i );
			if( mc.server == server ) {
				collAllClients.remove( i );
			} else {
				i++;
			}
		}

		collActiveClients.clear();
		server			= null;
		meterBangBndl	= null;
	}
	
	private void setServer( Server s )
	{
		Client mc;

		disposeServer();
	
		if( s == null ) return;

		server		= s;
				
		for( int i = 0; i < collAllClients.size(); i++ ) {
			mc = (Client) collAllClients.get( i );
			if( mc.server == server ) {
				collActiveClients.add( mc );
			}
		}
				
		resp	= new OSCResponderNode( server, "/c_setn", this );
		resortClients();
	}
	
	public void setListenerTask( PeakMeterView view, boolean task, OSCBundle bndl )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		final Client mc = (Client) mapClients.get( view );
		if( mc == null ) return;
		if( mc.task != task ) {
			mc.task	= task;
			if( mc.server == server ) {
				final boolean weCreated = bndl == null;
				if( weCreated ) bndl = new OSCBundle();
				for( int j = 0; j < mc.synths.length; j++ ) {
					if( mc.synths[ j ] != null ) {
						bndl.addPacket( mc.synths[ j ].runMsg( task ));
					}
				}
				if( weCreated && (bndl.getPacketCount() > 0) ) {
					try {
						server.sendBundle( bndl );
					}
					catch( IOException e1 ) {
						printError( "setListenerTask", e1 );
					}
				}
				if( task ) {
					if( ++numTask == 1 ) {
						meterTimer.restart();
					}
				} else {
					if( --numTask == 0 ) {
						meterTimer.stop();
					}
				}
			}
		}
	}

	public void addListener( PeakMeterView ml, Bus b, Group g, boolean task )
	{
		final int[] channels = new int[ b.getNumChannels() ];

		for( int i = 0, j = b.getIndex(); i < channels.length; ) {
			channels[ i++ ] = j++;
		}
		addListener( ml, b.getServer(), channels, g, task );
	}

	public void addListener( PeakMeterView ml, Server s, int[] channels, Group g, boolean task )
	{
		final Client mc;

		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		mc = new Client( ml, s, channels, g, task );
		if( mapClients.put( ml, mc ) != null ) throw new IllegalArgumentException( "MeterListener was already registered" );
		collAllClients.add( mc );
		if( mc.server == server ) {
			collActiveClients.add( mc );
			resortClients();
		}
	}
	
	public void removeListener( PeakMeterView view )
	{
		final Client	mc;
		final OSCBundle		bndl;
		
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		mc = (Client) mapClients.remove( view );
		if( mc == null ) return;
		collAllClients.remove( mc );
		if( collActiveClients.remove( mc )) {
			bndl = new OSCBundle();
			for( int i = 0; i < mc.synths.length; i++ ) {
				if( mc.synths[ i ] != null ) {
					bndl.addPacket( mc.synths[ i ].freeMsg() );
					mc.synths[ i ] = null;
				}
			}
			if( bndl.getPacketCount() > 0 ) {
				try {
					if( server.isRunning() ) server.sendBundle( bndl );
					resortClients();
				}
				catch( IOException e1 ) {
					printError( "removeMeterListener", e1 );
				}
			}
		}
	}
	
	private void resortClients()
	{
		final NodeWatcher	nw;
		int					off	= 0;
		OSCBundle			bndl;
		Group				g;
		boolean				haveGrpTrig	= false;
		Client				mc;
		int					srcChan;
	
		meterTimer.stop();

		if( resp != null ) resp.remove();

		bndl			= new OSCBundle();
		meterBangBndl	= null;

		for( int i = 0; i < collActiveClients.size(); i++ ) {
			mc = (Client) collActiveClients.get( i );
			for( int j = 0; j < mc.synths.length; j++ ) {
				if( mc.synths[ j ] != null ) {
					bndl.addPacket( mc.synths[ j ].freeMsg() );
					mc.synths[ j ] = null;
				}
			}
			mc.setOffset( off );
			off += mc.cNum;
		}
		
		if( bus != null ) {
			bus.free();
			bus = null;
		}
		
		numCtrlChans	= off;
		if( bndl.getPacketCount() > 0 ) {
			try {
				if( server.isRunning() ) server.sendBundle( bndl );
			}
			catch( IOException e1 ) {
				numCtrlChans	= 0;
				printError( "resortClients", e1 );
			}
		}
		
		numTask = 0;
		
		try {
			if( (server != null) && (numCtrlChans > 0) ) {
				nw		= NodeWatcher.newFrom( server );
				bndl	= new OSCBundle();
				bus		= Bus.control( server, numCtrlChans );
				if( bus != null ) {
					meterBangBndl = new OSCBundle();
					meterBangBndl.addPacket( new OSCMessage( "/c_getn", new Object[] {
						new Integer( bus.getIndex() ), new Integer( bus.getNumChannels() )}));

					for( int i = 0; i < collActiveClients.size(); i++ ) {
						mc	= (Client) collActiveClients.get( i );
						if( mc.task ) numTask++;
						if( mc.g != null ) {
							g	= mc.g;
							meterBangBndl.addPacket( new OSCMessage( "/n_set", new Object[] {
								new Integer( g.getNodeID() ), "t_trig", new Integer( 1 )}));
						} else {
							if( grp == null ) {
								grp = Group.basicNew( server );
								grp.setName( "MeterManager" );
								bndl.addPacket( grp.newMsg( server.getDefaultGroup(), kAddToTail ));
								nw.register( grp );
							}
							g	= grp;
							if( !haveGrpTrig ) {
								meterBangBndl.addPacket( new OSCMessage( "/n_set", new Object[] {
									new Integer( g.getNodeID() ), "t_trig", new Integer( 1 )}));
								haveGrpTrig = true;
							}
						}
						for( int j = 0, m = bus.getIndex() + mc.cOffset; j < mc.synths.length; j++, m += 2 ) {
							srcChan			= mc.channels[ j ];
							if( srcChan >= 0 ) {
								mc.synths[ j ] = Synth.basicNew( "eisk-meter", server );
								bndl.addPacket( mc.synths[ j ].newMsg( g, new String[] {
									"i_aInBs",  "i_kOtBs" }, new float[] {
									srcChan,    m }, kAddToTail ));
								if( !mc.task ) {
									bndl.addPacket( mc.synths[ j ].runMsg( false ));
								}
								nw.register( mc.synths[ j ]);
							}
						}
					}
					if( bndl.getPacketCount() > 0 ) {
						try {
							server.sendBundle( bndl );
							if( resp != null ) resp.add();
							if( numTask > 0 ) meterTimer.restart();
						}
						catch( IOException e1 ) {
							printError( "resortClients", e1 );
						}
					}
				} else {
					System.err.println( "MeterManager : ran out of control busses!" );
				}
			}
		}
		catch( IOException e1 ) {
			e1.printStackTrace();
		}
	}

	// ------------- internal classes -------------
	
	private static class Event
	extends BasicEvent
	{
		protected OSCMessage msg;
		
		protected Event( Object src, OSCMessage msg, long time )
		{
			super( src, 0, time );
			this.msg = msg;
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			if( (oldEvent instanceof Event) && (oldEvent.getSource() == getSource()) ) {
				final OSCMessage omsg = ((Event) oldEvent).msg;
				if( omsg.getName().equals( msg.getName() ) &&
					(omsg.getArgCount() == msg.getArgCount()) &&
					omsg.getArg( 0 ).equals( msg.getArg( 0 )) &&	// busIndex
					omsg.getArg( 1 ).equals( msg.getArg( 1 ))) {	// numVals
					
					final Object[] fuseArgs = new Object[ msg.getArgCount() ];
					fuseArgs[ 0 ] = msg.getArg( 0 );
					fuseArgs[ 1 ] = msg.getArg( 1 );
					for( int i = 2; i < fuseArgs.length; i++ ) {
						fuseArgs[ i ] = new Float( Math.max(
						    ((Number)  msg.getArg( i )).floatValue(),
						    ((Number) omsg.getArg( i )).floatValue() ));
					}
					msg = new OSCMessage( msg.getName(), fuseArgs );
					return true;
				}
			}
			return false;
		}
	}
	
	private static class Client
	{
		protected final float[]			peakRMSPairs;
		protected final int				cNum;
		protected int					cOffset;
		protected final PeakMeterView	view;
		protected final int[]			channels;
		protected final Group			g;
		protected final Synth[]			synths;
		protected final Server			server;
		protected boolean				task;
		
		protected Client( PeakMeterView view, Server server, int[] channels, Group g, boolean task )
		{
			this.view		= view;
			this.server		= server;
			this.channels	= channels;
			this.g			= g;
			this.task		= task;

			cNum			= channels.length << 1;
			peakRMSPairs	= new float[ cNum ];
			synths			= new Synth[ channels.length ];
		}
		
		protected void setOffset( int cOffset )
		{
			this.cOffset	= cOffset;
		}
		
		public String toString()
		{
			final StringBuffer sb = new StringBuffer();
			sb.append( "[ " );
			for( int i = 0; i < channels.length; i++ ) {
				if( i > 0 ) sb.append( ", " );
				sb.append( channels[ i ]);
			}
			sb.append( " ]" );
			return( "MeterClient( "+view+", "+server+", " + sb.toString() + ", " + g + ", " + task + " )" );
		}
	}
}
