/*
 *  TimelineListener.java
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

package de.sciss.eisenkraut.timeline;

import java.util.EventListener;

/**
 *  Interface for listening
 *  to changes of the session's timeline
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