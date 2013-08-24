/*
 *  GroupAnySync.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2013 Hanns Holger Rutz. All rights reserved.
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
 */

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
