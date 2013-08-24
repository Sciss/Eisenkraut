/*
 *  XMLRepresentation.java
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
 *		13-May-05	created from de.sciss.meloncillo.io.XMLRepresentation
 */

package de.sciss.eisenkraut.io;

import java.io.IOException;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *  Classes implementing this interface
 *  support import and export of their
 *  structure from/to XML
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 *
 *  @todo   this could be much more elaborate
 */
public interface XMLRepresentation
{
	/**
	 *	This key can be used for the options map
	 *	to specify the base directory of the xml file.
	 *	The value should be of class <code>File</code>
	 */
	public static final String KEY_BASEPATH	= "path";

	/**
	 *	This key can be used for the options map
	 *	by the implementing class to tell the invoking
	 *	method to display a warning to the user.
	 *	The value should be of class <code>String</code>
	 */
	public static final String KEY_WARNING = "warn";

	/**
	 *  Requests the object to attach a XML
	 *  representation of its serialized fields
	 *  to the provided node.
	 *
	 *  @param  domDoc  XML document used to create new elements
	 *  @param  node	the (parent) node to which this object
	 *					should attach its own elements and sub elements
	 *  @throws IOException when an error occurs. XML specific exceptions
	 *			must be mapped to IOExceptions
	 */
	public void toXML( Document domDoc, Element node, Map options ) throws IOException;

	/**
	 *  Requests the object to restore its serialized fields from
	 *  the provided XML node.
	 *
	 *  @param  domDoc  XML document containing the node
	 *  @param  node	the (parent) node from which this object
	 *					should read relevant data to restore its fields
	 *  @throws IOException when an error occurs. XML specific exceptions
	 *			must be mapped to IOExceptions
	 */
	public void fromXML( Document domDoc, Element node, Map options ) throws IOException;
}
