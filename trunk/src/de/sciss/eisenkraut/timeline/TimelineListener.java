/*
 *  TimelineListener.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2007 Hanns Holger Rutz. All rights reserved.
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
 *		28-Jan-05	created from de.sciss.meloncillo.timeline.TimelineListener
 */

package de.sciss.eisenkraut.timeline;

import java.util.*;

/**
 *  Interface for listening
 *  to changes of the session's timeline
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.56, 15-Sep-05
 *
 *  @see		Timeline#addTimelineListener( TimelineListener )
 *  @see		TimelineEvent
 */
public interface TimelineListener
extends EventListener
{
	/**
	 *  Notifies the listener that
	 *  a portion of the timeline was selected or deselected.
	 *
	 *  @param  e   the event describing
	 *				the timeline selection
	 *				(<code>getActionObj</code> will
	 *				return the new selected span)
	 */
	public void timelineSelected( TimelineEvent e );
	/**
	 *  Notifies the listener that
	 *  the basic timeline properties were modified
	 *  (e.g. the length or rate changed).
	 *
	 *  @param  e   the event describing
	 *				the timeline modification
	 */
	public void timelineChanged( TimelineEvent e );
	/**
	 *  Notifies the listener that
	 *  the timeline's playback position was moved.
	 *  Note that during realtime playback, only the
	 *  realtime consumers get informed about transport
	 *  advances, since the frequency is too high for
	 *  using event dispatching. instead, when the transport
	 *  is stopped, the new position is fired using a
	 *  timeline event. hence, when you're not interested
	 *  in continuous realtime update of the timeline position,
	 *  a normal timeline listener is sufficient.
	 *
	 *  @param  e   the event describing
	 *				the timeline positioning
	 */
	public void timelinePositioned( TimelineEvent e );
	/**
	 *  Notifies the listener that
	 *  a the view of the timeline frame was scrolled
	 *  to a new position (or zoomed).
	 *
	 *  @param  e   the event describing
	 *				the timeline scrolling
	 *				(<code>getActionObj</code> will
	 *				return the new visible span)
	 */
	public void timelineScrolled( TimelineEvent e );
}