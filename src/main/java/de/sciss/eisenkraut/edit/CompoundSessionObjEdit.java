/*
 *  SyncCompoundSessionObjEdit.java
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
 *
 *
 *  Changelog:
 *		15-Jul-05	copied from de.sciss.meloncillo.edit.SyncCompoundSessionObjEdit,
 *					added representation name, uses LockManager instead of Session
 */

package de.sciss.eisenkraut.edit;

import java.util.ArrayList;
import java.util.List;

import de.sciss.eisenkraut.session.SessionObject;

import de.sciss.app.AbstractCompoundEdit;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is used
 *  to synchronize an Undo or Redo operation of compound
 *  transmitter (or trajectory) modifying edits.
 *  The synchronization is provided by waiting exclusively
 *  for a given door.
 *
 *  @author			Hanns Holger Rutz
 *  @version		0.57, 28-Sep-07
 *  @see			UndoManager
 *  @see			de.sciss.util.LockManager
 */
public class CompoundSessionObjEdit
extends AbstractCompoundEdit
{
	private Object					source;
	private final List				collSessionObjects;
	private final int				ownerModType;
	private final Object			ownerModParam, ownerUndoParam;

	/**
	 *  Creates a <code>CompoundEdit</code> objekt, whose Undo/Redo
	 *  actions are synchronized. When the edit gets finished
	 *  by calling the <code>end</code> method, the
	 *  <code>transmitterCollection.modifiedAll</code> method is called,
	 *  thus dispatching a <code>transmitterCollectionEvent</code>.
	 *
	 *  @param  source				Event-Source for <code>doc.transmitterCollection.modified</code>.
	 *								Gets discarded upon undo / redo invocation.
	 *  @param  collSessionObjects	list of transmitters to be edited.
	 *	@param	ownerModType		XXX
	 *	@param	ownerModParam		XXX
	 *	@param	ownerUndoParam		XXX
	 *  @param  doors				doors to use for locking, usually
	 *								Session.DOOR_TRNS or Session.DOOR_TIMETRNSMTE
	 *
	 *  @see	de.sciss.util.LockManager
	 *  @see	de.sciss.eisenkraut.session.SessionCollection
	 *  @see	de.sciss.eisenkraut.session.SessionCollection.Event
	 *
	 *  @synchronization			waitExclusive on the given doors
	 */
	public CompoundSessionObjEdit( Object source, List collSessionObjects,
								   int ownerModType, Object ownerModParam,
								   Object ownerUndoParam, String representationName )
	{
		super( representationName );
		
		this.source				= source;
		this.collSessionObjects = new ArrayList( collSessionObjects );
		this.ownerModType		= ownerModType;
		this.ownerModParam		= ownerModParam;
		this.ownerUndoParam		= ownerUndoParam;
	}
	
	/**
	 *  calls <code>doc.transmitterCollection.modifiedAll</code>.
	 *  The original edit source is discarded.
	 */
	protected void undoDone()
	{
		int i;
		
		for( i = 0; i < collSessionObjects.size(); i++ ) {
			((SessionObject) collSessionObjects.get( i )).getMap().dispatchOwnerModification(
				source, ownerModType, ownerUndoParam );
		}
	}

	/**
	 *  calls <code>doc.transmitterCollection.modifiedAll</code>.
	 *  The original edit source is discarded.
	 */
	protected void redoDone()
	{
		int i;
		
		for( i = 0; i < collSessionObjects.size(); i++ ) {
			((SessionObject) collSessionObjects.get( i )).getMap().dispatchOwnerModification(
				source, ownerModType, ownerModParam );
		}
	}

	protected void cancelDone() { /* empty */ }
	
	/**
	 *  Finishes the compound edit and calls
	 *  <code>doc.transmitterCollection.modifiedAll</code>
	 *  using the source provided in the constructor.
	 *
	 *  @synchronization	the caller must
	 *						block all <code>addEdit</code>
	 *						and the <code>end</code> call
	 *						into a sync block itself!
	 */
	public void end()
	{
		super.end();
		redoDone();
		source  = this;
	}
}