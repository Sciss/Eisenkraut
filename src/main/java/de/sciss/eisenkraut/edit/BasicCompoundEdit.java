/*
 *  BasicSyncCompoundEdit.java
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
 *
 *
 *  Changelog:
 *		07-Feb-05	created from de.sciss.meloncillo.edit.BasicSyncCompoundEdit
 *		02-Mar-06	added setSignificant()
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.AbstractCompoundEdit;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is 
 *  the most basic extension of the abstract class
 *  which simply puts empty bodies for the abstract methods.
 *
 *  @author			Hanns Holger Rutz
 *  @version		0.70, 01-May-06
 *  @see			de.sciss.util.LockManager
 */
public class BasicCompoundEdit
extends AbstractCompoundEdit
{
	private boolean	significant	= true;

	/**
	 *  Creates a <code>CompountEdit</code> object, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm		the <code>LockManager</code> to use in synchronization
	 *  @param  doors   the doors to lock exclusively using the provided <code>LockManager</code>
	 */
	public BasicCompoundEdit()
	{
		super();
	}

	/**
	 *  Creates a <code>CompountEdit</code> object with a given name, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm					the <code>LockManager</code> to use in synchronization
	 *  @param  doors				the doors to lock exclusively using the provided <code>LockManager</code>
	 *	@param	presentationName	text describing the compound edit
	 */
	public BasicCompoundEdit( String presentationName )
	{
		super( presentationName );
	}
	
	public boolean isSignificant()
	{
		if( significant ) return super.isSignificant();
		else return false;
	}

	public void setSignificant( boolean b )
	{
		significant = b;
	}
	
	/**
	 *  Does nothing
	 */
	protected void undoDone() { /* empty */ }
	/**
	 *  Does nothing
	 */
	protected void redoDone() { /* empty */ }
	/**
	 *  Does nothing
	 */
	protected void cancelDone() { /* empty */ }
}