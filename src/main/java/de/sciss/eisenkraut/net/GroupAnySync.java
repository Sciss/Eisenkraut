//
//  GroupAnySync.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 21.09.06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package de.sciss.eisenkraut.net;

import java.util.HashSet;
import java.util.Set;

import de.sciss.net.OSCBundle;

public class GroupAnySync
extends GroupSync
implements GroupSync.Slave
{
	private final Set	syncs		= new HashSet();
	private int			numActive	= 0;

	public GroupAnySync()
	{
		super();
	}
	
	public void addSync( GroupSync s )
	{
		if( !syncs.add( s )) throw new IllegalArgumentException( "Sync already added" );
		s.addSlave( this );
		if( s.isActive() && (++numActive == 1) ) {
			activate( null );
		}
	}
	
	public void removeSync( GroupSync s )
	{
		if( !syncs.remove( s ))  throw new IllegalArgumentException( "Sync not found" );
		s.removeSlave( this );
//System.err.println( "removeSync. isActive = "+s.isActive()+"; numActive = "+numActive );		
		if( s.isActive() && (--numActive == 0) ) {
			deactivate( null );
		}
	}
	
	public void clearSyncs()
	{
		syncs.clear();
		numActive	= 0;
		active		= false;
	}

	// -------------- Disposable interface --------------

	public void dispose()
	{
		clearSyncs();
		super.dispose();
	}

	// ------------------ GroupSync.Slave interface ------------------

	public void groupActivate( OSCBundle bndl )
	{
		if( ++numActive == 1 ) {
			activate( bndl );
		}
	}
	
	public void groupDeactivate( OSCBundle bndl )
	{
		if( --numActive == 0 ) {
			deactivate( bndl );
		}
	}
}
