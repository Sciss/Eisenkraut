//
//  LevelMeterManager.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 21.09.06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package de.sciss.eisenkraut.gui;

import java.awt.EventQueue;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.jcollider.Bus;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.Server;
import de.sciss.net.OSCBundle;
import de.sciss.util.Disposable;

import de.sciss.eisenkraut.net.GroupSync;
import de.sciss.eisenkraut.net.GroupAnySync;
import de.sciss.eisenkraut.net.MeterListener;
import de.sciss.eisenkraut.net.MeterManager;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 17-Oct-06
 *
 *	@synchronization	all methods are to be called in the event thread
 */
public class LevelMeterManager
implements MeterListener, Disposable, DynamicListening, GroupSync.Slave	// , NodeListener
{
	private final MeterManager				mm;
	private final DynamicAncestorAdapter	daa;

	private volatile LevelMeter[]			meters			= new LevelMeter[ 0 ];
	private boolean							added;						// registered with the MeterManager and NodeWatcher
	private Server							s				= null;
	private Group							g				= null;
	private int[]							channels		= null;
	private volatile boolean				task			= false;	// whether currently metering
//	private int								numActive		= 0;		// number of tasking nodes playing
	private boolean							showing			= false;	// whether level meters are displayed
//	private NodeWatcher						nw				= null;
	private final GroupAnySync				anySync;
	private final Runnable					runStopTasking;
	
	private Object							sync			= new Object();

	public LevelMeterManager( MeterManager mm )
	{
		this.mm	= mm;
		daa		= new DynamicAncestorAdapter( this );
		anySync	= new GroupAnySync();
		anySync.addSlave( this );
		
		runStopTasking = new Runnable() {
			public void run()
			{
				checkStopTasking();
			}
		};
	}
	
	public void setSync( Object sync ) {
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		this.sync	= sync;
		for( int i = 0; i < meters.length; i++ ) meters[ i ].setSync( sync );
	}
	
	private void checkStopTasking()
	{
		if( added && !task ) {
//System.err.println( "MOTO!" );
			mm.setListenerTask( this, false, null );
		}
	}
	
	public void setDynamicComponent( JComponent c )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		if( daa.getComponent() != null ) {
			daa.remove();
		}
		daa.addTo( c );
		setSync( RepaintManager.currentManager( c ));
	}
	
	public void setGroup( Group g )
	{
		this.g	= g;
	}
	
	public void clearInputs()
	{
		setInputs( null, null );
	}
	
	public void setInputs( Bus b )
	{
		final int[] channels = new int[ b.getNumChannels() ];
	
		for( int i = 0, j = b.getIndex(); i < channels.length; ) {
			channels[ i++ ] = j++;
		}
		
		setInputs( b.getServer(), channels );
	}

	public void setInputs( Server s, int[] channels )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		if( added ) {
			mm.removeListener( this );
//			nw.removeListener( this );
			added	= false;
//			nw		= null;
		}
		
//		clearTaskSyncs();
		
		this.s			= s;
		this.channels	= channels;
		checkAdded();
	}
	
	private void checkAdded()
	{
		if( added ) {
			if( (s == null) || !showing ) {
				mm.removeListener( this );
				added = false;
			}
		} else {
			if( (s != null) && showing ) {
//				mm.addListener( this, b, null, task );
				mm.addListener( this, s, channels, g, task );
				added = true;
			}
		}
	}
	
	public void setMeters( LevelMeter[] meters )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		this.meters	= meters;
//		final LevelMeter[] newMeters	= new LevelMeter[ meters.length + 1 ];
//		System.arraycopy( meters, 0, newMeters, 0, meters.length );
//		newMeters[ meters.length ] = m;
//		meters = newMeters;
		for( int i = 0; i < meters.length; i++ ) meters[ i ].setSync( sync );
	}
	
//	public void removeMeter( LevelMeter m )
//	{
//		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
//	
//		int i;
//		for( i = 0; i < meters.length; i++ ) {
//			if( meters[ i ] == m ) break;
//		}
//		if( i >= meters.length ) throw new IllegalArgumentException( "Meter not found" );
//	
//		final LevelMeter[] newMeters	= new LevelMeter[ meters.length - 1 ];
//		System.arraycopy( meters, 0, newMeters, 0, i );
//		System.arraycopy( meters, i + 1, newMeters, i, newMeters.length - i );
//		meters = newMeters;
//	}
	
	public void addTaskSync( GroupSync n )
	{
//		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
//	
		anySync.addSync( n );
	}
	
	public void removeTaskSync( GroupSync n )
	{
//		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
//	
		anySync.removeSync( n );
	}
	
	public void clearTaskSyncs()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		anySync.clearSyncs();
		updateTask( null );
	}
	
	private void updateTask( OSCBundle bndl )
	{
//		final boolean newTask = showing && anySync.isActive();
		final boolean newTask = anySync.isActive();
		if( newTask != task ) {
			task = newTask;
			if( added ) {
				if( task ) {
					mm.setListenerTask( this, true, bndl );
				}
			}
		}
	}
	
	// -------------- GroupSync.Slave interface --------------
	
	public void groupActivate( OSCBundle bndl )
	{
//System.err.println( "groupActivate" );
		updateTask( bndl );
	}
	
	public void groupDeactivate( OSCBundle bndl )
	{
//System.err.println( "groupDeactivate" );
		updateTask( bndl );
	}

	// -------------- Disposable interface --------------
	
	public void dispose()
	{
		meters		= new LevelMeter[ 0 ];
		anySync.dispose();
//		numActive	= 0;
		showing		= false;
		if( daa.getComponent() != null ) daa.remove();
		if( added ) {
			mm.removeListener( this );
			added = false;
		}
		s			= null;
		g			= null;
		channels	= null;
	}

	// -------------- DynamicListening interface --------------
	
	public void startListening()
	{
		showing	= true;
		updateTask( null );
		checkAdded();
//System.err.println( "startListening(). task = "+task+"; added =" +added );
	}

	public void stopListening()
	{
		showing	= false;
		checkAdded();
		updateTask( null );
//System.err.println( "stopListening(). task = "+task+"; added =" +added );
	}

//	// -------------- NodeListener interface --------------
//
//	public void nodeAction( NodeEvent e )
//	{
//		final Node n = e.getNode();
//		
//		if( taskSyncs.contains( n )) {
//			switch( e.getID() ) {
//			case NodeEvent.GO:
//			case NodeEvent.ON:
//				if( ++numActive == 1 ) {
//					updateTask();
//				}
//				break;
//				
//			case NodeEvent.OFF:
//				if( --numActive == 0 ) {
//					updateTask();
//				}
//				break;
//
//			case NodeEvent.END:
//				removeTaskSync( n );
//				break;
//			}
//		}
//	}
	
	// -------------- MeterListener interface --------------

	public void meterUpdate( float[] peakRMSPairs )
	{
		final LevelMeter[]	meters		= this.meters;	// = easy synchronization
		final int			numMeters	= Math.min( meters.length, peakRMSPairs.length >> 1 );
		final long			now			= System.currentTimeMillis();
		int					dirty		= 0;

//		if( now > timeMetersPause ) {
//			mm.setListenerTask( this, false );
//		}

		synchronized( sync ) {
			for( int i = 0, j = 0; i < numMeters; i++ ) {
				if( meters[ i ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ], now )) dirty++;
			}
		}
		
		if( !task && (dirty == 0) ) {
//System.err.println( "queue stop" );
			EventQueue.invokeLater( runStopTasking );
		}
//if( (dirty == 0) && task ) System.err.println( "STILL TASKING" );
	}
}