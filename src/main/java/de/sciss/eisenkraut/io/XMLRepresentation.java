/*
 *  XMLRepresentation.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
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
    public void toXML(Document domDoc, Element node, Map<Object, Object> options) throws IOException;

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
    public void fromXML(Document domDoc, Element node, Map<Object, Object> options) throws IOException;
}
