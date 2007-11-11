/*
 *  EditSetTimelineLength.java
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
		this.newLength		= length;
		this.oldLength		= doc.timeline.getLength();
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
		this.source		= this;
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