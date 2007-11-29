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
import de.sciss.gui.PeakMeter;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 29-Nov-07
 *
 *	@synchronization	all methods are to be called in the event thread
 */
public class PeakMeterManager
implements MeterListener, Disposable, DynamicListening, GroupSync.Slave
{
	private final MeterManager				mm;
	private final DynamicAncestorAdapter	daa;

	private volatile PeakMeter[]			meters			= new PeakMeter[ 0 ];
	private boolean							added;						// registered with the MeterManager and NodeWatcher
	private Server							s				= null;
	private Group							g				= null;
	private int[]							channels		= null;
	private volatile boolean				task			= false;	// whether currently metering
	private boolean							showing			= false;	// whether level meters are displayed
	private final GroupAnySync				anySync;
	private final Runnable					runStopTasking;
	
	private Object							sync			= new Object();

	public PeakMeterManager( MeterManager mm )
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
			added	= false;
		}
		
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
				clear();
			}
		} else {
			if( (s != null) && showing ) {
//				clear();
				mm.addListener( this, s, channels, g, task );
				added = true;
			}
		}
	}
	
	public void setMeters( PeakMeter[] meters )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		this.meters	= meters;
		for( int i = 0; i < meters.length; i++ ) meters[ i ].setSync( sync );
	}
	
	private void clear()
	{
//		System.out.println( "clear" );
		for( int i = 0; i < meters.length; i++ ) meters[ i ].clear();
	}
		
	public void addTaskSync( GroupSync n )
	{
		anySync.addSync( n );
	}
	
	public void removeTaskSync( GroupSync n )
	{
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
		updateTask( bndl );
	}
	
	public void groupDeactivate( OSCBundle bndl )
	{
		updateTask( bndl );
	}

	// -------------- Disposable interface --------------
	
	public void dispose()
	{
		meters		= new PeakMeter[ 0 ];
		anySync.dispose();
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
	}

	public void stopListening()
	{
		showing	= false;
		checkAdded();
		updateTask( null );
	}

	// -------------- MeterListener interface --------------

	public void meterUpdate( float[] peakRMSPairs )
	{
		final PeakMeter[]	meters		= this.meters;	// = easy synchronization
		final int			numMeters	= Math.min( meters.length, peakRMSPairs.length >> 1 );
		final long			now			= System.currentTimeMillis();
		int					dirty		= 0;

		synchronized( sync ) {
			for( int i = 0, j = 0; i < numMeters; i++ ) {
				if( meters[ i ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ], now )) dirty++;
			}
		}
		
		if( !task && (dirty == 0) ) {
			EventQueue.invokeLater( runStopTasking );
		}
	}
}