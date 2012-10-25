/*
 *  GroupSync.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.sciss.net.OSCBundle;
import de.sciss.util.Disposable;

public class GroupSync
implements Disposable
{
	private final List	collSlaves	= new ArrayList();
	protected boolean	active		= false;

	public GroupSync()
	{
		 /* empty */ 
	}

	public void addSlave( Slave s )
	{
		collSlaves.add( s );
	}
	
	public void removeSlave( Slave s )
	{
		collSlaves.remove( s );
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public void activate( OSCBundle bndl )
	{
		active = true;
		for( Iterator iter = collSlaves.iterator(); iter.hasNext(); ) {
			((Slave) iter.next()).groupActivate( bndl );
		}
	}
	
	public void deactivate( OSCBundle bndl )
	{
		active = false;
		for( Iterator iter = collSlaves.iterator(); iter.hasNext(); ) {
			((Slave) iter.next()).groupDeactivate( bndl );
		}
	}
	
	// -------------- Disposable interface --------------

	public void dispose()
	{
		collSlaves.clear();
		active = false;
	}

	// -------------- internal interfaces --------------
	
	public static interface Slave
	{
		public void groupActivate( OSCBundle bndl );
		public void groupDeactivate( OSCBundle bndl );
	}
}
