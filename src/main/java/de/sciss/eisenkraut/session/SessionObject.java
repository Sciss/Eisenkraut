/*
 *  SessionObject.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.session;

import de.sciss.eisenkraut.util.MapManager;
import de.sciss.util.Disposable;

public interface SessionObject extends Disposable {
	/**
	 *	Code for <code>MapManager.Event.getOwnerModType()</code>:
	 *	the object has been renamed
	 *
	 *	@see	de.sciss.eisenkraut.util.MapManager.Event#getOwnerModType()
	 */
	public static final int OWNER_RENAMED		=	0x1000;

	/**
	 *	Code for <code>MapManager.Event.getOwnerModType()</code>:
	 *	the object has been visually changed
	 *
	 *	@see	de.sciss.eisenkraut.util.MapManager.Event#getOwnerModType()
	 */
	public static final int OWNER_VISUAL		=	MapManager.OWNER_VISUAL;

	public static final String	MAP_KEY_FLAGS	= "flags";
	
	public static final int FLAGS_SOLO			= 0x01;
	public static final int FLAGS_MUTE			= 0x02;
	public static final int FLAGS_SOLOSAFE		= 0x04;
	public static final int FLAGS_VIRTUALMUTE	= 0x08;

	/**
	 *  Retrieves the property map manager of the session
	 *	object. This manager may be used to read and
	 *	write properties and register listeners.
	 *
	 *	@return	the property map manager that stores
	 *			all the properties of this session object
	 */
	public MapManager getMap();

	/**
	 *  Changes the object's logical name.
	 *  This name is used for displaying on the GUI.
	 *
	 * 	Warning: callers should check that the session's
	 *				collection doesn't contain objects
	 *				with duplicate logical names because
	 *				they might deduce file names from
	 *				their logical names when saving their
	 *				data model!
	 *
	 *  @param  newName		new object's name.
	 *
	 *  @see	SessionCollection#findByName( String )
	 */
	public void setName( String newName );

	/**
	 *  Queries the object's logical name.
	 *  This name is used for displaying on the GUI.
	 *
	 *  @return		current object's name.
	 */
	public String getName();

	/**
	 *  Gets the default editor for this
	 *  kind of object.
	 *
	 *	@return	class depending on the session object, i.e. ReceiverEditor
	 *			for a receiver
	 *
	 *  @see	java.lang.Class#newInstance()
	 */
	public Class<?> getDefaultEditor();
}