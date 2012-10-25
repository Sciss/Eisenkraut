/*
 *  EditPutMapValue.java
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
 *		17-Sep-05	copied from de.sciss.inertia.edit.EditPutMapValue
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.util.MapManager;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of a map.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 *
 *  @see		UndoManager
 *	@see		de.sciss.eisenkraut.util.MapManager
 */
public class EditPutMapValue
extends BasicUndoableEdit
{
	private Object				source;
	private final MapManager	map;
	private final String		key;
	private final Object		oldValue, newValue;
	private final String		name;

	/**
	 *  Create and perform this edit. This
	 *  invokes the map's <code>putValue</code> method,
	 *  thus dispatching a <code>MapManager.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  lm				the <code>LockManager</code> to use for synchronization
	 *							or <code>null</code>
	 *	@param	doors			the doors to sync on using <code>waitExclusive</code>
	 *  @param  map				the map to change (e.g. a session object's map)
	 *	@param	key				the map entry to change
	 *  @param  value			the new property value
	 *
	 *  @see	de.sciss.eisenkraut.util.MapManager#putValue( Object, String, Object )
	 *  @see	de.sciss.eisenkraut.util.MapManager.Event
	 *
	 *  @synchronization		<code>lm.waitExclusive()</code> on <code>doors</code>
	 */
	public EditPutMapValue( Object source,
							MapManager map, String key, Object value, String name )
	{
		super();
		this.source			= source;
		this.map			= map;
		this.key			= key;
		newValue			= value;
		oldValue			= map.getValue( key );
		this.name			= name;
	}

	public EditPutMapValue( Object source,
							MapManager map, String key, Object value )
	{
		this( source, map, key, value, null );
	}

	public PerformableEdit perform()
	{
		map.putValue( source, key, newValue );
		source			= this;
		return this;
	}

	/**
	 *  Undo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		map.putValue( source, key, oldValue );
	}
	
	/**
	 *  Redo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *  The original event source is discarded.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return name == null ? getResourceString( "editPutMapValue" ) : name;
	}
}