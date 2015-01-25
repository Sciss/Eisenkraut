/*
 *  EditPutMapValue.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.util.MapManager;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of a map.
 *
 *  @see		UndoManager
 *	@see		de.sciss.eisenkraut.util.MapManager
 */
@SuppressWarnings("serial")
public class EditPutMapValue
		extends BasicUndoableEdit {

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
	 *  @param  map				the map to change (e.g. a session object's map)
	 *	@param	key				the map entry to change
	 *  @param  value			the new property value
	 *
	 *  @see	de.sciss.eisenkraut.util.MapManager#putValue( Object, String, Object )
	 *  @see	de.sciss.eisenkraut.util.MapManager.Event
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