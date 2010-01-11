/*
 *  TimelineVisualEdit.java
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
 *		08-Sep-05	created
 *		27-Apr-06	deferred perform
 */

package de.sciss.eisenkraut.edit;

import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.session.Session;

import de.sciss.io.Span;

/**
 *  An <code>UndoableEdit</code> that describes the modification of the
 *  timeline visual properties (position, selection, visible span).
 *	This edit is always &quot;insignificant&quot;, placing it on the
 *	pending stack of the undo manager, and not appearing as separately
 *	undoable edits. By fusing all visual properties into one edit
 *	class, successive visual edits can be collapsed into one edit object
 *	without flooding the undo manager's list.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 27-Apr-06
 *  @see		UndoManager
 */
public class TimelineVisualEdit
extends BasicUndoableEdit
{
	private final Session   doc;
	private Object			source;
	private long			oldPos, newPos;
	private Span			oldVisi, newVisi, oldSel, newSel;

	private int				actionMask;
	
//	private boolean			dontMerge	= false;
	
	private static final int	ACTION_POSITION	= 0x01;
	private static final int	ACTION_SCROLL	= 0x02;
	private static final int	ACTION_SELECT	= 0x04;

	/*
	 *  Create and perform the edit. This method
	 *  invokes the <code>Timeline.setSelectionSpan</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setSelectionSpan</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						to be selected / deselected.
	 *  @param  span		the new timeline selection span.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	private TimelineVisualEdit( Object source, Session doc )
	{
		super();
		this.source		= source;
		this.doc		= doc;
		actionMask		= 0;
	}
	
//	/**
//	 *	Decides whether this visual edit can be
//	 *	merged with adjectant visual edits.
//	 *	By default, they may be merged.
//	 *
//	 *	@param	onOff	choose <code>true</code> to disable merging of visual edits
//	 */
//	public void setDontMerge( boolean onOff )
//	{
//		dontMerge = onOff;
//	}
	
	public static TimelineVisualEdit position( Object source, Session doc, long pos )
	{
		final TimelineVisualEdit tve = new TimelineVisualEdit( source, doc );
		tve.actionMask	= ACTION_POSITION;
		
//		try {
//			doc.bird.waitShared( Session.DOOR_TIME );
			tve.oldPos		= doc.timeline.getPosition();
			tve.newPos		= pos;
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
		return tve;
	}

	public static TimelineVisualEdit scroll( Object source, Session doc, Span newVisi )
	{
		final TimelineVisualEdit tve = new TimelineVisualEdit( source, doc );
		tve.actionMask	= ACTION_SCROLL;
		
//		try {
//			doc.bird.waitShared( Session.DOOR_TIME );
			tve.oldVisi		= doc.timeline.getVisibleSpan();
			tve.newVisi		= newVisi;
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
		return tve;
	}

	public static TimelineVisualEdit select( Object source, Session doc, Span newSel )
	{
		final TimelineVisualEdit tve = new TimelineVisualEdit( source, doc );
		tve.actionMask	= ACTION_SELECT;
		
//		try {
//			doc.bird.waitShared( Session.DOOR_TIME );
			tve.oldSel		= doc.timeline.getSelectionSpan();
			tve.newSel		= newSel;
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
		return tve;
	}
	
	public PerformableEdit perform()
	{
		if( (actionMask & ACTION_POSITION) != 0 ) {
			doc.timeline.setPosition( source, newPos );
		}
		if( (actionMask & ACTION_SCROLL) != 0 ) {
			doc.timeline.setVisibleSpan( source, newVisi );
		}
		if( (actionMask & ACTION_SELECT) != 0 ) {
			doc.timeline.setSelectionSpan( source, newSel );
		}
		source	= this;
		return this;
	}

	/**
	 *  @return		false to tell the UndoManager it should not feature
	 *				the edit as a single undoable step in the history.
	 *				which is especially important since <code>TimelineAxis</code>
	 *				will generate lots of edits when the user drags
	 *				the timeline selection.
	 */
	public boolean isSignificant()
	{
		return false;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setSelectionSpan</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		if( (actionMask & ACTION_POSITION) != 0 ) {
			doc.timeline.setPosition( source, oldPos );
//System.err.println( "undo. setting position "+oldPos+"; source = "+source.getClass().getName() );
		}
		if( (actionMask & ACTION_SCROLL) != 0 ) {
			doc.timeline.setVisibleSpan( source, oldVisi );
//System.err.println( "undo. setting scroll "+oldVisi+"; source = "+source.getClass().getName() );
		}
		if( (actionMask & ACTION_SELECT) != 0 ) {
			doc.timeline.setSelectionSpan( source, oldSel );
//System.err.println( "undo. setting select "+oldSel+"; source = "+source.getClass().getName() );
		}
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
	
	/**
	 *  Collapse multiple successive EditSetReceiverBounds edit
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
//		if( dontMerge ) return false;
	
		if( anEdit instanceof TimelineVisualEdit ) {
			final TimelineVisualEdit tve = (TimelineVisualEdit) anEdit;
			if( (tve.actionMask & ACTION_POSITION) != 0 ) {
				newPos		= tve.newPos;
//System.err.println( "addEdit. taking newPos "+newPos );
				if( (actionMask & ACTION_POSITION) == 0 ) {
					oldPos = tve.oldPos;
//System.err.println( "addEdit. taking oldPos "+oldPos );
				}
			}
			if( (tve.actionMask & ACTION_SCROLL) != 0 ) {
				newVisi	= tve.newVisi;
//System.err.println( "addEdit. taking newVisi "+newVisi );
				if( (actionMask & ACTION_SCROLL) == 0 ) {
					oldVisi = tve.oldVisi;
//System.err.println( "addEdit. taking oldVisi "+oldVisi );
				}
			}
			if( (tve.actionMask & ACTION_SELECT) != 0 ) {
				newSel		= tve.newSel;
//System.err.println( "addEdit. taking newSel "+newSel );
				if( (actionMask & ACTION_SELECT) == 0 ) {
					oldSel = tve.oldSel;
//System.err.println( "addEdit. taking oldSel "+oldSel );
				}
			}
			actionMask |= tve.actionMask;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	/**
	 *  Collapse multiple successive edits
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit( UndoableEdit anEdit )
	{
//		if( dontMerge ) return false;

		if( anEdit instanceof TimelineVisualEdit ) {
			final TimelineVisualEdit tve = (TimelineVisualEdit) anEdit;
			if( (tve.actionMask & ACTION_POSITION) != 0 ) {
				oldPos		= tve.oldPos;
				if( (actionMask & ACTION_POSITION) == 0 ) {
					newPos	= tve.newPos;
				}
			}
			if( (tve.actionMask & ACTION_SCROLL) != 0 ) {
				oldVisi	= tve.oldVisi;
				if( (actionMask & ACTION_SCROLL) == 0 ) {
					newVisi = tve.newVisi;
				}
			}
			if( (tve.actionMask & ACTION_SELECT) != 0 ) {
				oldSel		= tve.oldSel;
				if( (actionMask & ACTION_SELECT) == 0 ) {
					newSel = tve.newSel;
				}
			}
			actionMask |= tve.actionMask;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelineView" );
	}
}