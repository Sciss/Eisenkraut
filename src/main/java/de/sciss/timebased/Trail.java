/*
 *  Trail.java
 *  de.sciss.timebased package
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 *		06-Jan-06	created
 *		25-Feb-06	extends TreeNode ; moved to double precision
 */

package de.sciss.timebased;

import java.io.IOException;
import java.util.EventListener;
import java.util.List;
import javax.swing.tree.TreeNode;

import de.sciss.app.BasicEvent;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.io.Span;
import de.sciss.util.Disposable;

public interface Trail
		extends Disposable, TreeNode {

	public static final int TOUCH_NONE		= 0;
	public static final int TOUCH_SPLIT		= 1;
	public static final int TOUCH_RESIZE	= 2;

	public void clear(Object source);

	public void dispose();

	public void insert(Object source, Span span);

	public void insert(Object source, Span span, int touchMode);

	public void editInsert(Object source, Span span, AbstractCompoundEdit ce);

	public void editInsert(Object source, Span span, int touchMode, AbstractCompoundEdit ce);

	public void remove(Object source, Span span);

	public void remove(Object source, Span span, int touchMode);

	public void editRemove(Object source, Span span, AbstractCompoundEdit ce);

	public void editRemove(Object source, Span span, int touchMode, AbstractCompoundEdit ce);

	public void clear(Object source, Span span);

	public void clear(Object source, Span span, int touchMode);

	public void editClear(Object source, Span span, AbstractCompoundEdit ce);

	public void editClear(Object source, Span span, int touchMode, AbstractCompoundEdit ce);

	public void add(Object source, Stake stake) throws IOException;

	public void editAdd(Object source, Stake stake, AbstractCompoundEdit ce) throws IOException;

	public void addAll(Object source, List<Stake> stakes) throws IOException;

	public void editAddAll(Object source, List<Stake> stakes, AbstractCompoundEdit ce) throws IOException;

	public void remove(Object source, Stake stake) throws IOException;

	public void editRemove(Object source, Stake stake, AbstractCompoundEdit ce) throws IOException;

	public void removeAll(Object source, List<Stake> stakes) throws IOException;

	public void editRemoveAll(Object source, List<Stake> stakes, AbstractCompoundEdit ce) throws IOException;

	public int getDefaultTouchMode();

	//	public void replace( Object source, Stake oldStake, Stake newStake, CompoundEdit ce );
	public List<Stake> getRange(Span span, boolean byStart);

	public List<Stake> getCutRange(Span span, boolean byStart, int touchMode, long shiftVirtual);

	public Trail getCutTrail(Span span, int touchMode, long shiftVirtual);

	//	public List getRange( int startIdx, int stopIdx, boolean byStart );
	public List<Stake> getAll(boolean byStart);

	public Stake get(int idx, boolean byStart);

	public int getNumStakes();

	public Span getSpan();

	public double getRate();

	public int indexOf(Stake stake, boolean byStart);

	public int indexOf(long pos, boolean byStart);

	public boolean contains(Stake stake);

	public boolean isEmpty();
//	public Stake getLeftMost( int idx, boolean byStart );
//	public Stake getRightMost( int idx, boolean byStart );
//	public int getLeftMostIndex( int idx, boolean byStart );
//	public int getRightMostIndex( int idx, boolean byStart );

	public void addListener(Trail.Listener listener);

	public void removeListener(Trail.Listener listener);

	public void editBegin(AbstractCompoundEdit ce);

	public void editEnd(AbstractCompoundEdit ce);

// -------------------------- inner Listener interface --------------------------

	public interface Listener
			extends EventListener {
		public void trailModified(Trail.Event e);
	}

// --------------------- internal classes ---------------------

	@SuppressWarnings("serial")
	public static class Event
			extends BasicEvent {
		public static final int MODIFIED = 0;

		private final Trail t;
		private Span span;

		public Event(Trail t, Object source, Span affectedSpan) {
			super(source, MODIFIED, System.currentTimeMillis());

			this.t = t;
			this.span = affectedSpan;
		}

		public Trail getTrail() {
			return t;
		}

		public Span getAffectedSpan() {
			return span;
		}

		public boolean incorporate(BasicEvent oldEvent) {
			if (oldEvent instanceof Event &&
					this.getSource() == oldEvent.getSource() &&
					this.getID() == oldEvent.getID()) {

				final Event e = (Event) oldEvent;
				if (e.getTrail() == this.getTrail()) {
					this.span = Span.union(this.getAffectedSpan(), e.getAffectedSpan());
					return true;
				}
			}
			return false;
		}
	}
}
