/*
 *  MeterManager.java
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
 *		13-Jul-06	created
 */

package de.sciss.eisenkraut.net;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

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
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;

public class MeterManager
implements OSCListener, Constants, ServerListener, ActionListener
{
	private List					collAllClients		= new ArrayList();
	private List					collActiveClients	= new ArrayList();
	private Map						mapClients			= new HashMap();	// key = MeterListener, value = MeterClient
//	private boolean					meterListening		= false;
//	private boolean					keepMetering		= false;
//	private long					lastMeterInfo		= 0;	// used to stop listening
//	
//	private static final int		MAXFALLTIME			= 5000;

	private Server					server				= null;
	
	private Bus						bus					= null;
	private Group					grp					= null;
	
	private final Object			sync				= new Object();
	
	private int						numCtrlChans		= 0;
	
	private OSCBundle				meterBangBndl		= null;
	private OSCResponderNode		resp				= null;

	private final Timer				meterTimer;
	private final SuperColliderClient sc;
	
	private int						numTask				= 0;

	public MeterManager( SuperColliderClient sc )
	{
		this.sc		= sc;
		meterTimer	= new javax.swing.Timer( 33, this );
		sc.addServerListener( this );
	}
	
	public void dispose()
	{
		sc.removeServerListener( this );
		disposeServer();
	}

	private void meterBang()
	{
		synchronized( sync ) {
			if( (server != null) && (meterBangBndl != null) ) {
				try {
//					if( meterListening ) server.sendBundle( meterBangBndl );
					server.sendBundle( meterBangBndl );
				}
				catch( IOException e1 ) {} // don't print coz the frequency might be high
			}
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
		synchronized( sync ) {	
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
	}

	// ------------- OSCListener interface -------------
	
	public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
	{
		final int		busIndex	= ((Number) msg.getArg( 0 )).intValue();
		final int		numVals		= ((Number) msg.getArg( 1 )).intValue();
		MeterClient		mc;
		
		synchronized( sync ) {
			if( (bus == null) || (busIndex != bus.getIndex()) ) return;

			for( int i = 0; i < collActiveClients.size(); i++ ) {
				mc	= (MeterClient) collActiveClients.get( i );
				if( !mc.task ) continue;
//				for( int j = 0, off = mc.cOffset; (j < mc.cNum) && (off < numVals); j++, off++ ) {
//					mc.peakRMSPairs[ j ] = ((Number) msg.getArg( off + 2 )).floatValue();
//				}
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
				mc.ml.meterUpdate( mc.peakRMSPairs );
			}
		}
	}

	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	// @synchronization	must be called with sync on sync
	private void disposeServer()
	{
		MeterClient mc;

		meterTimer.stop();
		
		if( resp != null ) {
			try {
				resp.remove();
			}
			catch( IOException e1 ) {
				printError( "disposeServer", e1 );
			}
		}
	
		if( bus != null ) {
			bus.free();
			bus = null;
		}
		grp	= null;
		
		if( server == null ) return;
		
		for( int i = 0; i < collAllClients.size(); ) {
			mc = (MeterClient) collAllClients.get( i );
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
	
	// @synchronization	must be called with sync on sync
	private void setServer( Server s )
	{
		if( !Thread.holdsLock( sync )) throw new IllegalMonitorStateException();
	
		MeterClient mc;

		disposeServer();
	
		if( s == null ) return;

		server		= s;
				
		for( int i = 0; i < collAllClients.size(); i++ ) {
			mc = (MeterClient) collAllClients.get( i );
			if( mc.server == server ) {
				collActiveClients.add( mc );
			}
		}
				
		resp	= new OSCResponderNode( server, "/c_setn", this );
		resortClients();
	}
	
	public void setListenerTask( MeterListener ml, boolean task, OSCBundle bndl )
	{
		synchronized( sync ) {
			final MeterClient mc = (MeterClient) mapClients.get( ml );
			if( mc == null ) return;
			if( mc.task != task ) {
				mc.task	= task;
				if( mc.server == server ) {
					final boolean weCreated = bndl == null;
					if( weCreated ) bndl = new OSCBundle();
					for( int j = 0; j < mc.synths.length; j++ ) {
						if( mc.synths[ j ] != null ) {
							bndl.addPacket( mc.synths[ j ].runMsg( task ));
//System.err.println( mc.synths[ j ].toString() + " -> runMsg( "+task+" )" );
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
//System.err.println( "numPackets "+bndl.getPacketCount() + "; task = "+task );
					if( task ) {
						if( ++numTask == 1 ) {
//System.err.println( "numTask == 1" );
							meterTimer.restart();
						}
					} else {
						if( --numTask == 0 ) {
//System.err.println( "numTask == 0" );
							meterTimer.stop();
						}
					}
				}
			}
		}
	}

	public void addListener( MeterListener ml, Bus b, Group g, boolean task )
	{
		final int[] channels = new int[ b.getNumChannels() ];

//System.err.println( "addListener( "+ml+", "+b+", "+g );
		
		for( int i = 0, j = b.getIndex(); i < channels.length; ) {
			channels[ i++ ] = j++;
		}
		addListener( ml, b.getServer(), channels, g, task );
	}

	public void addListener( MeterListener ml, Server s, int[] channels, Group g, boolean task )
	{
		final MeterClient mc;

//Thread.dumpStack();
//System.err.println( "addListener( "+ml+", "+channels.length+", "+g );
		
		synchronized( sync ) {
			mc = new MeterClient( ml, s, channels, g, task );
//System.err.println( "add "+ml+"; bus "+b );
			if( mapClients.put( ml, mc ) != null ) throw new IllegalArgumentException( "MeterListener was already registered" );
			collAllClients.add( mc );
			if( mc.server == server ) {
				collActiveClients.add( mc );
				resortClients();
			}
		}
	}
	
	public void removeListener( MeterListener ml )
	{
		final MeterClient	mc;
		final OSCBundle		bndl;
		
		synchronized( sync ) {
			mc = (MeterClient) mapClients.remove( ml );
			if( mc == null ) return;
//			if( mc == null ) throw new IllegalArgumentException( "MeterListener was not registered" );
//System.err.println( "remove "+mc.ml+"; bus "+mc.b );
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
						server.sendBundle( bndl );
						resortClients();
					}
					catch( IOException e1 ) {
						printError( "removeMeterListener", e1 );
					}
				}
			}
		}
	}
	
	// @synchronization	must be called with sync on sync
	private void resortClients()
	{
		if( !Thread.holdsLock( sync )) throw new IllegalMonitorStateException();

		final NodeWatcher	nw;
		int					off	= 0;
		OSCBundle			bndl;
		Group				g;
		boolean				haveGrpTrig	= false;
		MeterClient			mc;
		int					srcChan;
	
		meterTimer.stop();

		if( resp != null ) {
			try {
				resp.remove();
			}
			catch( IOException e1 ) {
				printError( "resortClients", e1 );
			}
		}

		bndl			= new OSCBundle();
		meterBangBndl	= null;

		for( int i = 0; i < collActiveClients.size(); i++ ) {
			mc = (MeterClient) collActiveClients.get( i );
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
				server.sendBundle( bndl );
			}
			catch( IOException e1 ) {
				numCtrlChans	= 0;
				printError( "resortClients", e1 );
			}
		}
		
//		System.err.println( "numCtrlChans = "+numCtrlChans );
		
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
						mc	= (MeterClient) collActiveClients.get( i );
//System.err.println( "i = "+i+"; mc = "+mc );
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
//System.err.println( mc.synths[ j ].toString() + " -> runMsg( "+mc.task+" )" );
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
	
	private static class MeterClient
	{
		private final float[]		peakRMSPairs;
		private final int			cNum;
		private int					cOffset;
		private final MeterListener	ml;
		private final int[]			channels;
		private final Group			g;
		private final Synth[]		synths;
		private final Server		server;
		private boolean				task;
		
		private MeterClient( MeterListener ml, Server server, int[] channels, Group g, boolean task )
		{
			this.ml			= ml;
			this.server		= server;
			this.channels	= channels;
			this.g			= g;
			this.task		= task;

			cNum			= channels.length << 1;
			peakRMSPairs	= new float[ cNum ];
			synths			= new Synth[ channels.length ];
		}
		
		private void setOffset( int cOffset )
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
			return( "MeterClient( "+ml+", "+server+", " + sb.toString() + ", " + g + ", " + task + " )" );
		}
	}
}
