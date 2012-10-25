/*
 *  EditSetSessionObjects.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *		13-Aug-05	copied from de.sciss.meloncillo.edit.EditSetSessionObjects;
 *		14-Jan-06	copied from de.sciss.inertia.edit.EditSetSessionObjects;
 */

package de.sciss.eisenkraut.edit;

import java.util.ArrayList;
import java.util.List;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.session.SessionCollection;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the selection or deselection
 *  of sessionObjects from the <code>SessionObjectCollection</code>
 *  of the session.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 *  @see		UndoManager
 */
public class EditSetSessionObjects
extends BasicUndoableEdit
{
	private Object					source;
	private final SessionCollection	quoi;
	private final List				oldSelection, newSelection;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.selectionSet</code> method,
	 *  thus dispatching a <code>SessionObjectCollectionEvent</code>.
	 *
	 *  @param  source				who initiated the action
	 *	@param	quoi				XXX
	 *  @param  collNewSelection	the new collection of sessionObjects
	 *								which form the new selection. the
	 *								previous selection is discarded.
	 *	@param	doors				XXX
	 *
	 *  @see	de.sciss.eisenkraut.session.SessionCollection
	 *  @see	de.sciss.eisenkraut.session.SessionCollection.Event
	 *
	 *  @synchronization			waitExclusive on doors
	 */
	public EditSetSessionObjects( Object source, SessionCollection quoi,
								  List collNewSelection )
	{
		super();
		this.source			= source;
		this.quoi			= quoi;
		oldSelection   		= quoi.getAll();
		newSelection   		= new ArrayList( collNewSelection );
	}

	/**
	 *  @return		false to tell the UndoManager it should not feature
	 *				the edit as a single undoable step in the history.
	 */
	public boolean isSignificant()
	{
		return false;
	}

	public PerformableEdit perform()
	{
		quoi.clear( source );
		quoi.addAll( source, newSelection );
		source			= this;
		return this;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>SessionObjectCollection.selectionSet</code>,
	 *  method. thus dispatching a <code>SessionObjectCollectionEvent</code>.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		quoi.clear( source );
		quoi.addAll( source, oldSelection );
	}
	
	/**
	 *  Redo the selection edit.
	 *  The original source is discarded
	 *  which means, that, since a new <code>SessionObjectCollectionEvent</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thorougly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	/**
	 *  Collapse multiple successive edits
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetSessionObjects ) {
			newSelection.clear();
			newSelection.addAll( ((EditSetSessionObjects) anEdit).newSelection );
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	/**
	 *  Collapse multiple successive edits
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetSessionObjects ) {
			oldSelection.clear();
			oldSelection.addAll( ((EditSetSessionObjects) anEdit).oldSelection );
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetSessionObjects" );
	}
}