/*
 *  EditSetTimelineLength.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		07-Feb-05	created from de.sciss.meloncillo.edit.EditSetTimelineLength
 *		27-Apr-06	deferred perform
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.session.Session;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the truncation or increment of
 *  to session's <code>Timeline</code> duration.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 27-Apr-06
 *  @see		UndoManager
 */
public class EditSetTimelineLength
extends BasicUndoableEdit
{
	private Object			source;
	private final Session   doc;
	private final long 		oldLength, newLength;

	/**
	 *  Create and perform the edit. This is usually
	 *  not invoked directly by the application but as
	 *  part of the compound edits <code>EditInsertTimeSpan</code>
	 *  and <code>EditRemoveTimeSpan</code>. This method
	 *  doesn't take care of the timeline's selection or
	 *  visible span. It invokes the <code>Timeline.setLength</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setLength</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						affected.
	 *  @param  length		the new timeline length in sample frames.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditSetTimelineLength( Object source, Session doc, long length )
	{
		super();
		this.source			= source;
		this.doc			= doc;
		newLength			= length;
		oldLength			= doc.timeline.getLength();
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setLength</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		doc.timeline.setLength( source, oldLength );
	}
	
	public PerformableEdit perform()
	{
		doc.timeline.setLength( source, newLength );
		source		= this;
		return this;
	}
	
	/**
	 *  Redo the edit. The original source is discarded
	 *  which means, that, since a new <code>TimelineEvent</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelineLength" );
	}
}