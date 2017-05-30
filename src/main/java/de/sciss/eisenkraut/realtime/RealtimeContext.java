/*
 *  RealtimeContext.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.realtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.sciss.eisenkraut.session.SessionObject;
import de.sciss.io.Span;

/**
 *  Analogue to RenderContext
 *  for the realtime engine
 */
public class RealtimeContext {
	private final Span				time;
	private final double			sourceRate;
	private final List<SessionObject> tracks;
	private final Transport			transport;
	
	private final HashMap<Object, Object> options			= new HashMap<Object, Object>();
	private final HashSet<Object> modifiedOptions = new HashSet<Object>();

	/**
	 *  Constructs a new RealtimeContext.
	 *
	 *  @param  sourceRate			the source sense data rate
	 */
	public RealtimeContext( Transport transport, List<SessionObject> tracks, Span time, double sourceRate )
	{
		this.transport		= transport;
		this.time			= time;
		this.sourceRate		= sourceRate;
		this.tracks			= tracks;
	}

	public Transport getTransport() {
		return transport;
	}

	public List<SessionObject> getTracks() {
		return tracks;
	}

	/**
	 *  Replaces a value for an option
	 *  (or create a new option if no
	 *  value was previously set). The
	 *  option is added to the list of
	 *  modifications, see getModifiedOptions().
	 *
	 *	@param	key		key of the option such as KEY_PREFBLOCKSIZE
	 *	@param	value	corresponding value. Hosts and plug-ins
	 *					should "know" what kind of key required what
	 *					kind of value class
	 */
	public void setOption(Object key, Object value) {
		options.put(key, value);
		modifiedOptions.add(key);
	}

	/**
	 * Performs setOption() on a series
	 * of key/value pairs.
	 *
	 * @param    map        a map whose key/value pairs
	 * are copied to the context options and
	 * appear in the modified options list
	 */
	public void setOptions(Map<Object, Object> map) {
		options.putAll(map);
		modifiedOptions.addAll(map.keySet());
	}
	
	/**
	 *  Queries the value of an options.
	 *
	 *	@return		the value corresponding to the key
	 *				or null if the option wasn't set.
	 */
	public Object getOption( Object key )
	{
		return options.get( key );
	}
	
	/**
	 *  Returns a set of all options modified
	 *  since last calling this method. Calling
	 *  this method twice in succession will
	 *  result in an empty set. All options
	 *  set using setOption() after calling
	 *  getModifiedOptions() will be present
	 *  at the next invocation of this method.
	 *
	 *	@return	a set of keys which were modified
	 *			since the last invocation of this method
	 */
	public Set<Object> getModifiedOptions()
	{
		Set<Object> result = new HashSet<Object>( modifiedOptions );
		modifiedOptions.clear();
	
		return result;
	}

	/**
	 *  Returns the time span to render
	 *
	 *	@return	the rendering time span as passed to the constructor
	 */
	public Span getTimeSpan()
	{
		return time;
	}

	/**
	 *  Returns the source sense data rate
	 *
	 *	@return	the source rate (in hertz) as passed to the constructor
	 */
	public double getSourceRate()
	{
		return sourceRate;
	}
}