/*
 *  TimelineEvent.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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
 *		28-Jan-05	created from de.sciss.meloncillo.timeline.TimelineEvent
 */

package de.sciss.eisenkraut.timeline;

import de.sciss.app.BasicEvent;

/**
 *  This kind of event is fired
 *  from a <code>Timeline</code> when
 *  the user or an application object modified the timeline.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.56, 05-May-06
 *
 *  @see		Timeline#addTimelineListener( TimelineListener )
 *  @see		TimelineListener
 */
public class TimelineEvent
extends BasicEvent
{
// --- ID values ---
	/**
	 *  returned by getID() : a portion of the timeline
	 *  has been selected or deselected
	 */
	public static final int SELECTED	= 0;
	/**
	 *  returned by getID() : the basic properties of
	 *  the timeline, rate or length, have been modified.
     *  <code>actionObj</code> is a (potentially empty)
     *  <code>Span</code> object
	 */
	public static final int CHANGED		= 1;
	/**
	 *  returned by getID() : the 'playback head' of
	 *  the timelime has been moved
	 */
	public static final int POSITIONED	= 2;
	/**
	 *  returned by getID() : the visible portion of
	 *  the timelime has been changed.
     *  <code>actionObj</code> is a (potentially empty)
     *  <code>Span</code> object
	 */
	public static final int SCROLLED	= 3;

	private final int		actionID;   // currently not in use
	private final Object	actionObj;  // used depending on the event ID

	/**
	 *  Constructs a new <code>TimelineEvent</code>
	 *
	 *  @param  source		who originated the action
	 *  @param  ID			one of <code>CHANGED</code>, <code>SELECTED</code>,
	 *						<code>POSITIONED</code> and <code>SCROLLED</code>
	 *  @param  when		system time when the event occured
	 *  @param  actionID	currently unused - thus use zero
	 *  @param  actionObj   for <code>SELECTED</code> and <code>SCROLLED</code>
	 *						this is a <code>Span</code> describing the new
	 *						visible or selected span.
	 */
	public TimelineEvent( Object source, int ID, long when, int actionID, Object actionObj )
	{
		super( source, ID, when );
	
		this.actionID   = actionID;
		this.actionObj  = actionObj;
	}
	
	/**
	 *  Currently unused
	 */
	public int getActionID()
	{
		return actionID;
	}

	/**
	 *  Depends on ID. See constructor for details.
	 *
	 *  @return		an event ID dependent object
	 */
	public Object getActionObject()
	{
		return actionObj;
	}

	public boolean incorporate( BasicEvent oldEvent )
	{
		if( oldEvent instanceof TimelineEvent &&
			this.getSource() == oldEvent.getSource() &&
			this.getID() == oldEvent.getID() ) {
			
			// XXX beware, when the actionID and actionObj
			// are used, we have to deal with them here
			
			return true;

		} else return false;
	}
}