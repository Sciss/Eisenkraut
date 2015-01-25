/*
 *  MapManager.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		13-May-05	created from de.sciss.meloncillo.util.MapManager
 */

package de.sciss.eisenkraut.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.eisenkraut.io.XMLRepresentation;

import de.sciss.io.IOUtil;

public class MapManager
		implements EventManager.Processor, XMLRepresentation {
	/**
	 *	Code for <code>Event.getOwnerModType()</code>:
	 *	the object has been changed in a way that affects its graphical
	 *	representation.
	 *
	 *	@see	MapManager.Event#getOwnerModType()
	 */
	public static final int OWNER_VISUAL		=	0x10000;


	// ------- private -------
	
	private final Map<String, Object> backingMap;
	protected final Object		owner;
	private final Map<String, Context> contextMap;
	private final EventManager elm = new EventManager(this);

	/**
	 */
	public MapManager(Object owner, Map<String, Object> backingMap) {
		this.owner			= owner;
		this.backingMap		= backingMap;
		contextMap			= new HashMap<String, Context>();
	}

	public MapManager(Object owner) {
		this(owner, new HashMap<String, Object>());
	}

	public void addListener(MapManager.Listener listener) {
		elm.addListener(listener);
	}

	public void removeListener(MapManager.Listener listener) {
		elm.removeListener(listener);
	}

	public void cloneMap(MapManager orig) {
		for (String key : orig.backingMap.keySet()) {
			this.backingMap.put(key, orig.backingMap.get(key));    // all supported types are immutable
		}

		for (String key : orig.contextMap.keySet()) {
			this.contextMap.put(key, new Context(orig.contextMap.get(key)));
		}
	}
	
	// the returned set is allowed to be modified
	public Set<String> keySet(int inclusionFlags, int exclusionFlags) {
		Set<String> allKeys = backingMap.keySet();
		HashSet<String> fltKeys = new HashSet<String>(allKeys);

		if ((inclusionFlags == Context.ALL_INCLUSIVE) &&
				(exclusionFlags == Context.NONE_EXCLUSIVE)) return fltKeys;
	
		Iterator<String> iter = fltKeys.iterator();
		Context c;

		while (iter.hasNext()) {
			c = contextMap.get(iter.next());
			if ((c == null) || ((c.flags & inclusionFlags) == 0) || ((c.flags & exclusionFlags) != 0)) {
				iter.remove();
			}
		}
	
		return fltKeys;
	}

	public boolean containsKey(String key) {
		return backingMap.containsKey(key);
	}

	public void putContext(Object source, String key, MapManager.Context context) {
		contextMap.put(key, context);
		Object o = getValue( key );
		if( (context.defaultValue != null) ) {
			if( o == null ) {
				putValue( source, key, context.defaultValue );
			} else {
				if( !o.getClass().equals( context.defaultValue.getClass() )) {
					// Long and Integer should be exchangeable due to NumberField ( XXX )
					if( !(o instanceof Number) || !(context.defaultValue instanceof Number) ) {
						System.err.println( "Warning! Map context and value classes inconsistent - "+
											"for key '"+key+"' context wants '"+context.defaultValue.getClass()+
											"' but finds '"+o.getClass()+"'. Replacing by default value!" );
						putValue( source, key, context.defaultValue );
					}
				}
			}
		}
	}

	public MapManager.Context getContext(String key) {
		return contextMap.get(key);
	}

	public Object putValue(Object source, String key, Object value) {
		Object oldVal = backingMap.put(key, value);
		if( (source != null) &&
			(((oldVal == null) && (value != null)) ||
			 ((oldVal != null) && (value == null)) ||
			 ((oldVal != null) && (value != null) && !(oldVal.equals( value ))))) {

			Set<String> keySet = new HashSet<String>( 1 );
			keySet.add( key );

			elm.dispatchEvent( new MapManager.Event( this, source, keySet ));
			checkOwnerModification( source, keySet );
		}
		return( oldVal );
	}

	public Object removeValue(Object source, String key) {
		final Object result = backingMap.remove(key);
		if ((result != null) && (source != null)) {
			final Set<String> keySet = new HashSet<String>(1);
			keySet.add(key);

			elm.dispatchEvent(new MapManager.Event(this, source, keySet));
			checkOwnerModification(source, keySet);
		}
		return result;
	}

	public void putAllValues(Object source, Map<String, Object> map) {
		backingMap.putAll(map);
		if ((source != null)) {
			Set<String> keySet = new HashSet<String>(map.keySet());
			elm.dispatchEvent(new MapManager.Event(this, source, keySet));
			checkOwnerModification(source, keySet);
		}
	}

	public Object getValue(String key) {
		return backingMap.get(key);
	}

	public void clearValues(Object source) {
		Set<String> keySet = new HashSet<String>(backingMap.keySet());
		boolean dispatch = !keySet.isEmpty() && (source != null);

		backingMap.clear();
		if (dispatch) {
			elm.dispatchEvent(new MapManager.Event(this, source, keySet));
			checkOwnerModification(source, keySet);
		}
	}

	/**
	 *	Copies all contexts from this map to
	 *	another map, which satisfy the given flags.
	 *
	 *	@param	inclusionFlags	only copy contexts whose flags
	 *							contain all of these flags
	 *	@param	exclusionFlags	only copy contexts whose flags
	 *							do not contain any of these flags
	 *	@param	targetMap		to map to which the contexts should be copied
	 */
	public void copyContexts(Object source, int inclusionFlags, int exclusionFlags, MapManager targetMap) {
		Iterator<String> iter = this.keySet( inclusionFlags, exclusionFlags ).iterator();
		String		key;
		Context		c;

		while (iter.hasNext()) {
			key = iter.next();
			c = this.contextMap.get(key);
			targetMap.putContext(source, key, c);
		}
	}

	public void dispatchOwnerModification(Object source, int ownerModType, Object ownerModParam) {
		elm.dispatchEvent(new MapManager.Event(this, source, ownerModType, ownerModParam));
	}

	private void checkOwnerModification(Object source, Set<String> keySet) {
		Iterator<String> iter	= keySet.iterator();
		String		key;
		Context		c;

		while (iter.hasNext()) {
			key = iter.next();
			c = getContext(key);
			if ((c != null) && ((c.flags & Context.FLAG_VISUAL) != 0)) {
				dispatchOwnerModification(source, OWNER_VISUAL, null);
				return;
			}
		}
	}

	public void debugDump() {
		System.err.println("..... map : hash = " + hashCode() + ". owner = " + owner.getClass().getName() + " . contains : ");
		Iterator<String> iter = backingMap.keySet().iterator();
		String key, val;
		while (iter.hasNext()) {
			key = iter.next();
			val = backingMap.get(key).toString();
			System.err.println(" key = " + key + " ; val = " + val);
		}
	}

// --------------------- EventManager.Processor interface ---------------------
	
	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed. This
	 *  will invoke the listener's <code>propertyChanged</code> method.
	 */
	public void processEvent( BasicEvent e )
	{
		MapManager.Listener listener;
		int i;
		MapManager.Event mme = (MapManager.Event) e;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (MapManager.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case MapManager.Event.MAP_CHANGED:
				listener.mapChanged( mme );
				break;
			case MapManager.Event.OWNER_MODIFIED:
				listener.mapOwnerModified( mme );
				break;
			default:
				assert false : e.getID();
				break;
			}
		} // for( i = 0; i < this.countListeners(); i++ )
	}

// --------------------- XMLRepresentation interface ---------------------

	private static final String XML_ELEM_ENTRY		= "entry";
//	private static final String XML_ELEM_FLAG		= "flag";
	private static final String XML_ATTR_KEY		= "key";
	private static final String XML_ATTR_VALUE		= "value";
	private static final String XML_ATTR_TYPE		= "type";
//	private static final String XML_ATTR_ID			= "id";
//	private static final String XML_VALUE_OBSERVER	= "observer";
//	private static final String XML_VALUE_EDITOR	= "editor";
//	private static final String XML_VALUE_LIST		= "list";
//	private static final String XML_VALUE_CONTEXT	= "context";

	private static final String[] XML_VALUE_TYPES	= {
		"int", "long", "float", "double", "boolean", "string", "file"
	};

	public void toXML(Document domDoc, Element node, Map options)
			throws IOException {
		Iterator<String> keys = backingMap.keySet().iterator();

		try {
			while( keys.hasNext() ) {
				String key = keys.next();
				Context c = contextMap.get( key );
				if( (c == null) || ((c.flags & Context.FLAG_NO_STORAGE) != 0) ) continue;
				Object value = backingMap.get( key );
				Element child = domDoc.createElement( XML_ELEM_ENTRY );
				child.setAttribute( XML_ATTR_KEY, key);
				child.setAttribute( XML_ATTR_VALUE, value.toString() );
				child.setAttribute( XML_ATTR_TYPE, XML_VALUE_TYPES[ c.type ]);
//				if( (c.flags & Context.FLAG_CONTEXT_STORAGE) != 0 ) {
//					if( (c.flags & Context.FLAG_OBSERVER_DISPLAY) != 0 ) {
//						createFlag( domDoc, child, XML_VALUE_OBSERVER );
//					}
//					if( (c.flags & Context.FLAG_EDITOR_DISPLAY) != 0 ) {
//						createFlag( domDoc, child, XML_VALUE_EDITOR );
//					}
//					if( (c.flags & Context.FLAG_LIST_DISPLAY) != 0 ) {
//						createFlag( domDoc, child, XML_VALUE_LIST );
//					}
//					if( (c.flags & Context.FLAG_CONTEXT_STORAGE) != 0 ) {
//						createFlag( domDoc, child, XML_VALUE_CONTEXT );
//					}
//				}
				node.appendChild( child );
			}
		}
		catch( DOMException e1 ) {
			throw IOUtil.map( e1 );  // rethrow exception
		}
	}

//	private void createFlag( Document domDoc, Element node, String id )
//	{
//		Element child = domDoc.createElement( XML_ELEM_FLAG );
//		child.setAttribute( XML_ATTR_ID, id );
//		node.appendChild( child );
//	}

	public void fromXML(Document domDoc, Element node, Map options)
			throws IOException {

		final NodeList	nl		= node.getChildNodes();
		final Set<String> keySet	= new HashSet<String>( backingMap.keySet() );
		int				type;
		Element			xmlChild;
		String			key, value, typeStr;
		Context			c;
		Object			o;
		
		backingMap.clear();

		for (int i = 0; i < nl.getLength(); i++) {
			if (!(nl.item(i) instanceof Element)) continue;
			xmlChild = (Element) nl.item(i);
			if (xmlChild.getTagName().equals(XML_ELEM_ENTRY)) {
				key = xmlChild.getAttribute(XML_ATTR_KEY);
				value = xmlChild.getAttribute(XML_ATTR_VALUE);
				typeStr = xmlChild.getAttribute(XML_ATTR_TYPE);
				if (typeStr == null) {    // #IMPLIED = String
					type = Context.TYPE_STRING;
				} else {
					for (type = 0; type < XML_VALUE_TYPES.length; type++) {
						if (XML_VALUE_TYPES[type].equals(typeStr)) break;
					}
					if (type == XML_VALUE_TYPES.length) {
						System.err.println("skipping map entry of unknown type : key = " + key + "; type = " + typeStr);
						continue;
					}
				}
				c = contextMap.get(key);
				if (c != null && c.type != type) {
					System.err.println("skipping map entry of deviant type : key = " + key + "; type = " + typeStr +
							"; expected = " + XML_VALUE_TYPES[c.type]);
					continue;
				} else if (c == null) {
					c = new Context(0, type, null, null, null, null);    // LLL XXX should read flags, constraints and label
					contextMap.put(key, c);
				}
				try {
					switch (type) {
						case Context.TYPE_INTEGER:
							o = new Integer(value);
							break;
						case Context.TYPE_LONG:
							o = new Long(value);
							break;
						case Context.TYPE_FLOAT:
							o = new Float(value);
							break;
						case Context.TYPE_DOUBLE:
							o = new Double(value);
							break;
						case Context.TYPE_BOOLEAN:
							o = Boolean.valueOf(value);
							break;
						case Context.TYPE_STRING:
							o = value;
							break;
						case Context.TYPE_FILE:
							o = new File(value);
							break;
						default:
							assert false : type;
							o = null;
					}
					backingMap.put(key, o);
					keySet.add(key);
				} catch (NumberFormatException e1) {
					System.err.println("couldn't parse map entry : key " + key + "; type = " + typeStr + "; value = " + value);
				}
			}
		}
		elm.dispatchEvent( new MapManager.Event( this, this, keySet ));
	}
	
// --------------------- inner classes ---------------------
	
	public static class Context
	{
		public static final int ALL_INCLUSIVE			= -1;
		public static final int NONE_EXCLUSIVE			= 0;

		public static final int FLAG_OBSERVER_DISPLAY	= 0x01;	// item should be visible in an observer
		public static final int FLAG_EDITOR_DISPLAY		= 0x02;	// item should be visible in an editor
		public static final int FLAG_LIST_DISPLAY		= 0x04;	// item should be visible in a list view
		public static final int FLAG_NO_STORAGE			= 0x08;	// item should not be stored in the session file
//		public static final int FLAG_CONTEXT_STORAGE	= 0x10;	// context flags and constratins should be saved
		public static final int FLAG_VISUAL				= 0x20;	// item affects visual representation
		public static final int FLAG_DYNAMIC			= 0x40;	// item should be effective only if a plug-in is active

		public static final int TYPE_INTEGER			= 0x00;
		public static final int TYPE_LONG				= 0x01;
		public static final int TYPE_FLOAT				= 0x02;
		public static final int TYPE_DOUBLE				= 0x03;
		public static final int TYPE_BOOLEAN			= 0x04;
		public static final int TYPE_STRING				= 0x05;
		public static final int TYPE_FILE				= 0x06;

		public final int	flags;
		public final int	type;
		public final Object	typeConstraints;	// e.g. NumberSpace
//		public int		gui;
		public final String	label;
		public final String	dynamic;
		public final Object	defaultValue;

		/**
		 *	typeConstraints must be an immutable object!
		 */
		public Context( int flags, int type, Object typeConstraints, String label, String dynamic,
						Object defaultValue )
		{
			this.flags				= flags;
			this.type				= type;
			this.typeConstraints	= typeConstraints;
			this.label				= label;
			this.dynamic			= dynamic;
			this.defaultValue		= defaultValue;
		}
		
		protected Context( Context orig )
		{
			this.flags				= orig.flags;
			this.type				= orig.type;
			this.typeConstraints	= orig.typeConstraints; // == null ? null : orig.typeConstraints.clone();
			this.label				= orig.label;
			this.dynamic			= orig.dynamic;
			this.defaultValue		= orig.defaultValue;
		}
	}
	
	/**
	 *  A simple interface describing
	 *  the method that gets called from
	 *  the event dispatching thread when
	 *  new objects have been queued.
	 */
	public interface Listener {
		public void mapChanged(MapManager.Event e);

		public void mapOwnerModified(MapManager.Event e);
	}

	@SuppressWarnings("serial")
	public static class Event
			extends BasicEvent {
		public static final int MAP_CHANGED		= 0;
		public static final int OWNER_MODIFIED	= 1;
		
		private final MapManager map;
		private final Set<String> propertyNames;
		private final Object param;
		private final int ownerModType;

		public Event(MapManager map, Object source, Set<String> propertyNames) {
			super(source, MAP_CHANGED, System.currentTimeMillis());

			this.map = map;
			this.propertyNames = propertyNames;
			this.param = null;
			this.ownerModType = -1;
		}

		public Event(MapManager map, Object source, int ownerModType, Object ownerModParam) {
			super(source, OWNER_MODIFIED, System.currentTimeMillis());

			this.map			= map;
			this.param			= ownerModParam;
			this.propertyNames	= null;
			this.ownerModType	= ownerModType;
		}

		public MapManager getManager() {
			return map;
		}

		public Set<String> getPropertyNames() {
			return propertyNames;
		}

		public Object getOwner() {
			return map.owner;
		}

		public int getOwnerModType() {
			return ownerModType;
		}

		public Object getOwnerModParam() {
			return param;
		}

		/**
		 * Returns false always at the moment
		 */
		public boolean incorporate(BasicEvent oldEvent) {
			return false;
		}
	}
}