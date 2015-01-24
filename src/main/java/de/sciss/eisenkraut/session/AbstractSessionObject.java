/*
 *  AbstractSessionObject.java
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

package de.sciss.eisenkraut.session;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.sciss.eisenkraut.io.XMLRepresentation;
import de.sciss.eisenkraut.util.MapManager;
import de.sciss.io.IOUtil;

public abstract class AbstractSessionObject
		implements SessionObject, XMLRepresentation, MapManager.Listener {

	private	String		name;
	private MapManager map = new MapManager(this, new HashMap());

	protected static final String XML_ATTR_NAME			= "name";
	protected static final String XML_ATTR_CLASS		= "class";
	protected static final String XML_ELEM_OBJECT		= "object";
	protected static final String XML_ELEM_COLL			= "coll";
	protected static final String XML_ELEM_MAP			= "map";

	/**
	 */
	protected AbstractSessionObject()
	{
		init();
	}
	
	protected AbstractSessionObject(AbstractSessionObject orig) {
		init();
		map.cloneMap(orig.map);
		this.setName(orig.getName());
	}

	protected void init() {
		map.addListener(this);
		map.putContext(null, MAP_KEY_FLAGS, new MapManager.Context(MapManager.Context.FLAG_LIST_DISPLAY,
				MapManager.Context.TYPE_INTEGER, null, null,
				null, 0));
	}

	public void dispose() {
		/* empty */
	}

// ---------------- SessionObject interface ---------------- 

	/**
	 *  Retrieves the property map manager of the session
	 *	object. This manager may be used to read and
	 *	write properties and register listeners.
	 *
	 *	@return	the property map manager that stores
	 *			all the properties of this session object
	 */
	public MapManager getMap()
	{
		return map;
	}

	public void setName( String newName )
	{
		name = newName;
	}
	
	public String getName()
	{
		return name;
	}

// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged(MapManager.Event e) {
		/* empty */
	}

	public void mapOwnerModified( MapManager.Event e )
	{
		/* empty */ 
	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		try {
			node.setAttribute( XML_ATTR_CLASS, getClass().getName() );
			node.setAttribute( XML_ATTR_NAME, getName() );
			getMap().toXML( domDoc, (Element) node.appendChild( domDoc.createElement( XML_ELEM_MAP )), options );
		}
		catch( DOMException e1 ) {
			throw IOUtil.map( e1 );  // rethrow exception
		}
	}

	/**
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		NodeList	nl	= node.getChildNodes();
		int			i;
		Element		xmlChild;
		
		setName( node.getAttribute( XML_ATTR_NAME ));
		for( i = 0; i < nl.getLength(); i++ ) {
			if( !(nl.item( i ) instanceof Element )) continue;
			xmlChild = (Element) nl.item( i );
			if( xmlChild.getTagName().equals( XML_ELEM_MAP )) {
				getMap().fromXML( domDoc, xmlChild, options );
//System.err.println( "found map" );
//Set keySet = getMap().keySet( MapManager.Context.ALL_INCLUSIVE, MapManager.Context.NONE_EXCLUSIVE );
//Iterator iter = keySet.iterator();
//MapManager.Context c;
//String key;
//while( iter.hasNext() ) {
//	key = iter.next().toString();
//	c = getMap().getContext( key );
//	System.err.println( "    key = "+key+ " flags = "+c.flags );
//}
				break;	// only one 'map' allowed
			}
		}
	}
}