/*
 *  SessionObject.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		13-May-05	created from de.sciss.meloncillo.session.SessionObject
 */

package de.sciss.eisenkraut.session;

import de.sciss.eisenkraut.util.MapManager;
import de.sciss.util.Disposable;

/**
 *	@author		Hanns Holger Rutz
 *	@version	0.70, 07-Dec-07
 */
public interface SessionObject
extends Disposable
{
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
	 *  @param  newName		new object's name.
	 *
	 *  @see	SessionCollection#findByName( String )
	 *  @see	SessionCollection#createUniqueName( MessageFormat, Object[], List )
	 *
	 *  @warning	callers should check that the session's
	 *				collection doesn't contain objects
	 *				with duplicate logical names because
	 *				they might deduce file names from
	 *				their logical names when saving their
	 *				data model!
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
	public Class getDefaultEditor();
}