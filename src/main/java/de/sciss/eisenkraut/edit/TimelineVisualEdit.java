/*
 *  TimelineVisualEdit.java
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

package de.sciss.eisenkraut.edit;

import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.eisenkraut.session.Session;

import de.sciss.io.Span;

/**
 * An <code>UndoableEdit</code> that describes the modification of the
 * timeline visual properties (position, selection, visible span).
 * This edit is always &quot;insignificant&quot;, placing it on the
 * pending stack of the undo manager, and not appearing as separately
 * undoable edits. By fusing all visual properties into one edit
 * class, successive visual edits can be collapsed into one edit object
 * without flooding the undo manager's list.
 *
 * @see        UndoManager
 */
public class TimelineVisualEdit extends BasicUndoableEdit {

	private final Session   doc;
	private Object			source;
	private long			oldPos, newPos;
	private Span oldVisible, newVisible, oldSel, newSel;

	private int				actionMask;

	private static final int	ACTION_POSITION	= 0x01;
	private static final int	ACTION_SCROLL	= 0x02;
	private static final int	ACTION_SELECT	= 0x04;

	/*
	 *  Creates and performs the edit. This method
	 *  invokes the <code>Timeline.setSelectionSpan</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setSelectionSpan</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						to be selected / deselected.
	 *  @param  span		the new timeline selection span.
	 */
	private TimelineVisualEdit(Object source, Session doc) {
		super();
		this.source = source;
		this.doc 	= doc;
		actionMask 	= 0;
	}

	public static TimelineVisualEdit position(Object source, Session doc, long pos) {
		final TimelineVisualEdit tve = new TimelineVisualEdit(source, doc);
		tve.actionMask = ACTION_POSITION;

		tve.oldPos = doc.timeline.getPosition();
		tve.newPos = pos;

		return tve;
	}

	public static TimelineVisualEdit scroll(Object source, Session doc, Span newVisible) {
		final TimelineVisualEdit tve = new TimelineVisualEdit(source, doc);
		tve.actionMask = ACTION_SCROLL;

		tve.oldVisible = doc.timeline.getVisibleSpan();
		tve.newVisible = newVisible;

		return tve;
	}

	public static TimelineVisualEdit select(Object source, Session doc, Span newSel) {
		final TimelineVisualEdit tve = new TimelineVisualEdit(source, doc);
		tve.actionMask = ACTION_SELECT;

		tve.oldSel = doc.timeline.getSelectionSpan();
		tve.newSel = newSel;

		return tve;
	}

	public PerformableEdit perform() {
		if ((actionMask & ACTION_POSITION) != 0) {
			doc.timeline.setPosition(source, newPos);
		}
		if ((actionMask & ACTION_SCROLL) != 0) {
			doc.timeline.setVisibleSpan(source, newVisible);
		}
		if ((actionMask & ACTION_SELECT) != 0) {
			doc.timeline.setSelectionSpan(source, newSel);
		}
		source = this;
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
	 * Undoes the edit
	 * by calling the <code>Timeline.setSelectionSpan</code>,
	 * method, thus dispatching a <code>TimelineEvent</code>.
	 */
	public void undo() {
		super.undo();
		if ((actionMask & ACTION_POSITION) != 0) {
			doc.timeline.setPosition(source, oldPos);
		}
		if ((actionMask & ACTION_SCROLL) != 0) {
			doc.timeline.setVisibleSpan(source, oldVisible);
		}
		if ((actionMask & ACTION_SELECT) != 0) {
			doc.timeline.setSelectionSpan(source, oldSel);
		}
	}
	
	/**
	 *  Redoes the edit. The original source is discarded
	 *  which means, that, since a new <code>TimelineEvent</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}
	
	/**
	 *  Collapses multiple successive EditSetReceiverBounds edit
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit(UndoableEdit anEdit) {
		if (anEdit instanceof TimelineVisualEdit) {
			final TimelineVisualEdit tve = (TimelineVisualEdit) anEdit;
			if ((tve.actionMask & ACTION_POSITION) != 0) {
				newPos = tve.newPos;
				if ((actionMask & ACTION_POSITION) == 0) {
					oldPos = tve.oldPos;
				}
			}
			if ((tve.actionMask & ACTION_SCROLL) != 0) {
				newVisible = tve.newVisible;
				if ((actionMask & ACTION_SCROLL) == 0) {
					oldVisible = tve.oldVisible;
				}
			}
			if ((tve.actionMask & ACTION_SELECT) != 0) {
				newSel = tve.newSel;
				if ((actionMask & ACTION_SELECT) == 0) {
					oldSel = tve.oldSel;
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
	 *  Collapses multiple successive edits
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit(UndoableEdit anEdit) {
		if (anEdit instanceof TimelineVisualEdit) {
			final TimelineVisualEdit tve = (TimelineVisualEdit) anEdit;
			if ((tve.actionMask & ACTION_POSITION) != 0) {
				oldPos = tve.oldPos;
				if ((actionMask & ACTION_POSITION) == 0) {
					newPos = tve.newPos;
				}
			}
			if ((tve.actionMask & ACTION_SCROLL) != 0) {
				oldVisible = tve.oldVisible;
				if ((actionMask & ACTION_SCROLL) == 0) {
					newVisible = tve.newVisible;
				}
			}
			if ((tve.actionMask & ACTION_SELECT) != 0) {
				oldSel = tve.oldSel;
				if ((actionMask & ACTION_SELECT) == 0) {
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

	public String getPresentationName() {
		return getResourceString("editSetTimelineView");
	}
}