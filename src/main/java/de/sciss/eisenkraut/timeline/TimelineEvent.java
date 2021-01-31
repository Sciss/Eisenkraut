/*
 *  TimelineEvent.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.timeline;

import de.sciss.app.BasicEvent;

/**
 *  This kind of event is fired
 *  from a <code>Timeline</code> when
 *  the user or an application object modified the timeline.
 *
 *  @see		Timeline#addTimelineListener( TimelineListener )
 *  @see		TimelineListener
 */
public class TimelineEvent
        extends BasicEvent {
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
     *  the timeline has been moved
     */
    public static final int POSITIONED	= 2;
    /**
     *  returned by getID() : the visible portion of
     *  the timeline has been changed.
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
     *  @param  when		system time when the event occurred
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

    public boolean incorporate(BasicEvent oldEvent) {
        // XXX beware, when the actionID and actionObj
        // are used, we have to deal with them here
        return oldEvent instanceof TimelineEvent &&
                this.getSource() == oldEvent.getSource() &&
                this.getID() == oldEvent.getID();
    }
}