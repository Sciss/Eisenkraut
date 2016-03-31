/*
 *  BasicTrail.java
 *  de.sciss.timebased package
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.timebased;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.List;
import javax.swing.tree.TreeNode;
import javax.swing.undo.UndoableEdit;

import de.sciss.app.BasicEvent;
import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.EventManager;
import de.sciss.app.PerformableEdit;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.io.Span;
import de.sciss.util.ListEnum;

public abstract class BasicTrail
		implements Trail, EventManager.Processor {

	private static final boolean		DEBUG				= false;

	protected static final Comparator<Object> startComparator	= new StartComparator();
	protected static final Comparator<Object> stopComparator	= new StopComparator();
//	private static final List	collEmpty			= new ArrayList( 1 );
	
	private final List<Stake> collStakesByStart	= new ArrayList<Stake>();	// sorted using StartComparator
	private final List<Stake> collStakesByStop	= new ArrayList<Stake>();	// sorted using StopComparator

	private List<Stake>				collEditByStart		= null;
	private List<Stake>				collEditByStop		= null;
	private AbstractCompoundEdit	currentEdit			= null;

	private double						rate;
	
	private EventManager	 elm				= null;		// lazy creation
	private List<BasicTrail> dependants			= null;		// lazy creation

	// Element : Trail

	private final Object sync = new Object();
	
	public BasicTrail()
	{
		/* empty */ 
	}
	
	public double getRate()
	{
		return rate;
	}

	public void setRate(double rate) {
		this.rate = rate;
	}

	public void clear( Object source )
	{
		final boolean	wasEmpty	= isEmpty();
		final Span		span		= getSpan();

		clearIgnoreDependants();

		// ____ dep ____
		if (dependants != null) {
			synchronized (sync) {
				for (BasicTrail dependant : dependants) {
					dependant.clear(source);
				}
			}
		}

		if( (source != null) && !wasEmpty ) {
			dispatchModification( source, span );
		}
	}

	protected void clearIgnoreDependants() {
		Stake stake;

		while (!collStakesByStart.isEmpty()) {
			stake = collStakesByStart.remove(0);
//			stake.setTrail( null );
			stake.dispose();
		}
		collStakesByStop.clear();
	}

	public void dispose()
	{
//System.err.println( "BasicTrail.dispose()" );

		// ____ dep ____
		// crucial here that dependants are disposed _before_ this object
		// coz they might otherwise try be keep running stuff which is already disposed
		if( dependants != null ) {
			synchronized(sync) {
				final Object[] dep = dependants.toArray();
				for (Object aDep : dep) {
					((BasicTrail) aDep).dispose();
				}
			}
		}

		for (Stake aCollStakesByStart : collStakesByStart) aCollStakesByStart.dispose();
	
		collStakesByStart.clear();
		collStakesByStop .clear();
	}

	protected List<Stake> editGetCollByStart(AbstractCompoundEdit ce) {
		if ((ce == null) || (collEditByStart == null)) {
			return collStakesByStart;
		} else {
			return collEditByStart;
		}
	}

	protected List<Stake> editGetCollByStop(AbstractCompoundEdit ce) {
		if ((ce == null) || (collEditByStop == null)) {
			return collStakesByStop;
		} else {
			return collEditByStop;
		}
	}

	public Span getSpan() {
		return editGetSpan(null);
	}

	public Span editGetSpan(AbstractCompoundEdit ce) {
		return new Span(editGetStart(ce), editGetStop(ce));
	}

	private long editGetStart(AbstractCompoundEdit ce) {
		final List<Stake> coll = editGetCollByStart(ce);

		return (coll.isEmpty() ? 0 : coll.get(0).getSpan().start);
	}

	private long editGetStop(AbstractCompoundEdit ce) {
		final List<Stake> coll = editGetCollByStop(ce);

		return (coll.isEmpty() ? 0 : coll.get(coll.size() - 1).getSpan().stop);
	}

	public void editBegin(AbstractCompoundEdit ce) {
		if (currentEdit != null) {
			throw new ConcurrentModificationException("Concurrent editing");
		}
		currentEdit = ce;
		collEditByStart = null;        // dispose ? XXXX
		collEditByStop = null;        // dispose ? XXXX

		// ____ dep ____
		if (dependants != null) {
			synchronized (sync) {
				for (BasicTrail dependant : dependants) dependant.editBegin(ce);
			}
		}
	}

	public void editEnd( AbstractCompoundEdit ce )
	{
		checkEdit( ce );
		currentEdit		= null;
		collEditByStart	= null;		// dispose ? XXXX
		collEditByStop	= null;		// dispose ? XXXX

		// ____ dep ____
		if( dependants != null ) {
			synchronized(sync) {
				for (BasicTrail dependant : dependants) {
					dependant.editEnd(ce);
				}
			}
		}
	}

	private void checkEdit( AbstractCompoundEdit ce )
	{
		if( currentEdit == null ) {
			throw new IllegalStateException( "Missing editBegin" );
		}
		if( ce != currentEdit ) {
			throw new ConcurrentModificationException( "Concurrent editing" );
		}
	}
	
	private void ensureEditCopy()
	{
		if (collEditByStart == null) {
			collEditByStart = new ArrayList<Stake>(collStakesByStart);
			collEditByStop  = new ArrayList<Stake>(collStakesByStop );
		}
	}

	// returns stakes that intersect OR TOUCH the span
	public List<Stake> getRange(Span span, boolean byStart) {
		return editGetRange(span, byStart, null);
	}

	// returns stakes that intersect OR TOUCH the span
	public List<Stake> editGetRange(Span span, boolean byStart, AbstractCompoundEdit ce) {
		final List<Stake> collByStart, collByStop;
		List<Stake> collResult;
		int idx;

		if (ce == null) {
			collByStart = collStakesByStart;
			collByStop  = collStakesByStop;
		} else {
			checkEdit(ce);
			collByStart = collEditByStart == null ? collStakesByStart : collEditByStart;
			collByStop  = collEditByStop  == null ? collStakesByStop  : collEditByStop;
		}

		if (byStart) {
			idx = Collections.binarySearch(collByStop, span.start, stopComparator);
			if (idx < 0) {
				idx = -(idx + 1);
			} else {
				// "If the list contains multiple elements equal to the specified object,
				//  there is no guarantee which one will be found"
				idx = getLeftMostIndex(collByStop, idx, false);
			}
			collResult = new ArrayList<Stake>(collByStop.subList(idx, collByStop.size()));

			Collections.sort( collResult, startComparator );

			idx = Collections.binarySearch(collResult, span.stop, startComparator);
			if (idx < 0) {
				idx = -(idx + 1);
			} else {
				idx = getRightMostIndex(collResult, idx, true) + 1;
			}
			collResult = collResult.subList(0, idx);

		} else {
			idx = Collections.binarySearch(collByStart, span.stop, startComparator);
			if (idx < 0) {
				idx = -(idx + 1);
			} else {
				idx = getRightMostIndex(collByStart, idx, true) + 1;
			}
			collResult = new ArrayList<Stake>(collByStart.subList(0, idx));

			Collections.sort(collResult, stopComparator);

			idx = Collections.binarySearch(collResult, span.start, stopComparator);
			if (idx < 0) {
				idx = -(idx + 1);
			} else {
				idx = getLeftMostIndex(collResult, idx, false);
			}
			collResult = collResult.subList(idx, collResult.size());
		}

		return collResult;
	}

	public void insert( Object source, Span span )
	{
		editInsert( source, span, getDefaultTouchMode(), null );
	}
	
	public void insert( Object source, Span span, int touchMode )
	{
		editInsert( source, span, touchMode, null );
	}

	public void editInsert(Object source, Span span, AbstractCompoundEdit ce) {
		editInsert(source, span, getDefaultTouchMode(), ce);
	}
	
	public void editInsert( Object source, Span span, int touchMode, AbstractCompoundEdit ce )
	{
		final long	start			= span.start;
//		final long	stop			= span.stop;
		final long	totStop			= editGetStop( ce );
		final long	delta			= span.getLength();
		
		if( (delta == 0) || (start > totStop) ) return;
		
		final List<Stake> collRange		= editGetRange( new Span( start, totStop ), true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List<Stake>	collToAdd		= new ArrayList<Stake>();
		final List<Stake>	collToRemove	= new ArrayList<Stake>();
		final Span	modSpan;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			// XXX could use binarySearch ?
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.start >= start) {
					collToRemove.add(stake);
					collToAdd.add(stake.shiftVirtual(delta));
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop <= start) continue;

				collToRemove.add(stake);

				if (stakeSpan.start >= start) {            // not splitted
					collToAdd.add(stake.shiftVirtual(delta));
				} else {
					collToAdd.add(stake.replaceStop(start));
					stake = stake.replaceStart(start);
					collToAdd.add(stake.shiftVirtual(delta));
					stake.dispose();                        // delete temp product
				}
			}
			break;
			
		case TOUCH_RESIZE:
System.err.println( "BasicTrail.insert, touchmode resize : not tested" );
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop > start) {
					collToRemove.add(stake);

					if (stakeSpan.start > start) {
						collToAdd.add(stake.shiftVirtual(delta));
					} else {
						collToAdd.add(stake.replaceStop(stakeSpan.stop + delta));
					}
				}
			}
			break;

		default:
			throw new IllegalArgumentException( "TouchMode : " + touchMode );
		}

		modSpan		= Span.union( removeAllPr( collToRemove, ce ), addAllPr( collToAdd, ce ));

		// ____ dep ____
		if( dependants != null ) {
			synchronized(sync) {
				for (BasicTrail dependant : dependants) {
					dependant.editInsert(source, span, touchMode, ce);
				}
			}
		}

		if( (source != null) && (modSpan != null) ) {
			if( ce != null ) {
				ce.addPerform( new Edit( this, modSpan ));
			} else {
				dispatchModification( source, modSpan );
			}
		}
	}

	public void remove( Object source, Span span )
	{
		editRemove( source, span, getDefaultTouchMode(), null );
	}

	public void remove( Object source, Span span, int touchMode )
	{
		editRemove( source, span, touchMode, null );
	}
	 
	public void editRemove( Object source, Span span, AbstractCompoundEdit ce )
	{
		editRemove( source, span, getDefaultTouchMode(), ce );
	}

	/**
	 *	Removes a time span from the trail. Stakes that are included in the
	 *	span will be removed. Stakes that begin after the end of the removed span,
	 *	will be shifted to the left by <code>span.getLength()</code>. Stakes whose <code>stop</code> is
	 *	<code>&lt;=</code> the start of removed span, remain unaffected. Stakes that intersect the
	 *	removed span are traited according to the <code>touchMode</code> setting:
	 *	<ul>
	 *	<li><code>TOUCH_NONE</code> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start remain unaffected ; otherwise they are removed. This mode is usefull
	 *		for markers.</li>
	 *	<li><code>TOUCH_SPLIT</code> : the stake is cut at the removed span's start and stop ; a
	 *		middle part (if existing) is removed ; the left part (if existing) remains as is ; the right part
	 *		(if existing) is shifted by <code>-span.getLength()</code>. This mode is usefull for audio regions.</li>
	 *	<li><code>TOUCH_RESIZE</code> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start, will keep their start position ; if their stop position lies within the
	 *		removed span, it is truncated to the removed span's start. if their stop position exceeds the removed
	 *		span's stop, the stake's length is shortened by <code>-span.getLength()</code> . 
	 *		intersecting stakes whose <code>start</code> is greater or equal to the
	 *		the removed span's start, will by shortened by <code>(removed_span_stop - stake_start)</code> and
	 *		shifted by <code>-span.getLength()</code> . This mode is usefull for marker regions.</li>
	 *	</ul>
	 *
	 *	@param	source		source object for event dispatching (or <code>null</code> for no dispatching)
	 *	@param	span		the span to remove
	 *	@param	touchMode	the way intersecting staks are handled (see above)
	 *	@param	ce			provided to make the action undoable ; may be <code>null</code>. if a
	 *						<code>CompoundEdit</code> is provided, disposal of removed stakes is deferred
	 *						until the edit dies ; otherwise (<code>ce == null</code>) removed stakes are
	 *						immediately disposed.
	 */
	public void editRemove( Object source, Span span, int touchMode, AbstractCompoundEdit ce )
	{
		final long	start			= span.start;
		final long	stop			= span.stop;
		final long	totStop			= editGetStop( ce );
		final long	delta			= -span.getLength();
		
		if( (delta == 0) || (start > totStop) ) return;
		
		final List<Stake>	collRange		= editGetRange( new Span( start, totStop ), true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List<Stake>	collToAdd		= new ArrayList<Stake>();
		final List<Stake>	collToRemove	= new ArrayList<Stake>();
		final Span	modSpan;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			// XXX could use binarySearch ?
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.start < start) continue;

				collToRemove.add(stake);

				if (stakeSpan.start >= stop) {
					collToAdd.add(stake.shiftVirtual(delta));
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop > start) {

					collToRemove.add(stake);

					if (stakeSpan.start >= start) {            // start portion not splitted
						if (stakeSpan.start >= stop) {            // just shifted
							collToAdd.add(stake.shiftVirtual(delta));
						} else if (stakeSpan.stop > stop) {    // stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart(stop);
							collToAdd.add(stake.shiftVirtual(delta));
							stake.dispose();                    // delete temp product
						}
					} else {
						collToAdd.add(stake.replaceStop(start));    // start portion splitted
						if (stakeSpan.stop > stop) {            // stop portion splitted
							stake = stake.replaceStart(stop);
							collToAdd.add(stake.shiftVirtual(delta));
							stake.dispose();                    // delete temp product
						}
					}
				}
			}
			break;
			
		case TOUCH_RESIZE:
System.err.println( "BasicTrail.remove, touchmode resize : not tested" );
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop > start) {

					collToRemove.add(stake);

					if (stakeSpan.start >= start) {            // start portion not modified
						if (stakeSpan.start >= stop) {            // just shifted
							collToAdd.add(stake.shiftVirtual(delta));
						} else if (stakeSpan.stop > stop) {    // stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart(stop);
							collToAdd.add(stake.shiftVirtual(delta));
							stake.dispose();                    // delete temp product
						}
					} else {
						if (stakeSpan.stop <= stop) {
							collToAdd.add(stake.replaceStop(start));
						} else {
							collToAdd.add(stake.replaceStop(stakeSpan.stop + delta));
						}
					}
				}
			}
			break;
			
		default:
			throw new IllegalArgumentException( "TouchMode : " + touchMode );
		}

if( DEBUG ) {
	System.err.println( this.getClass().getName() + " : removing : " );
	for (Stake aCollToRemove : collToRemove) {
		System.err.println("  span " + aCollToRemove.getSpan());
	}
	System.err.println( " : adding : " );
	for (Object aCollToAdd : collToAdd) {
		System.err.println("  span " + ((Stake) aCollToAdd).getSpan());
	}
}
		modSpan		= Span.union( removeAllPr( collToRemove, ce ), addAllPr( collToAdd, ce ));

		// ____ dep ____
		if( dependants != null ) {
			synchronized(sync) {
				for (BasicTrail dependant : dependants) {
					dependant.editRemove(source, span, touchMode, ce);
				}
			}
		}

		if( (source != null) && !(collToRemove.isEmpty() && collToAdd.isEmpty()) ) {
			if( ce != null ) {
				ce.addPerform( new Edit( this, modSpan ));
			} else {
				dispatchModification( source, modSpan );
			}
		}
	}

	public void clear( Object source, Span span )
	{
		editClear( source, span, getDefaultTouchMode(), null );
	}

	public void clear( Object source, Span span, int touchMode )
	{
		editClear( source, span, touchMode, null );
	}

	public void editClear( Object source, Span span, AbstractCompoundEdit ce )
	{
		editClear( source, span, getDefaultTouchMode(), ce );
	}
	
	public void editClear( Object source, Span span, int touchMode, AbstractCompoundEdit ce )
	{
		final long	start			= span.start;
		final long	stop			= span.stop;
		final List<Stake>	collRange		= editGetRange( span, true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List<Stake> collToAdd		= new ArrayList<Stake>();
		final List<Stake> collToRemove	= new ArrayList<Stake>();
		final Span	modSpan;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.start >= start) {
					collToRemove.add(stake);
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop > start) {

					collToRemove.add(stake);

					if (stakeSpan.start >= start) {            // start portion not splitted
						if (stakeSpan.stop > stop) {            // stop portion splitted (otherwise completely removed!)
							collToAdd.add(stake.replaceStart(stop));
						}
					} else {
						collToAdd.add(stake.replaceStop(start));    // start portion splitted
						if (stakeSpan.stop > stop) {                // stop portion splitted
							collToAdd.add(stake.replaceStart(stop));
						}
					}
				}
			}
			break;
			
		case TOUCH_RESIZE:
			System.err.println( "BasicTrail.clear, touchmode resize : not tested" );
			for (Stake stake : collRange) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.stop > start) {

					collToRemove.add(stake);

					if (stakeSpan.start >= start) {        // start portion not modified
						if (stakeSpan.stop > stop) {        // stop portion splitted (otherwise completely removed!)
							collToAdd.add(stake.replaceStart(stop));
						}
					} else {
						if (stakeSpan.stop <= stop) {
							collToAdd.add(stake.replaceStop(start));
						} else {
							collToAdd.add(stake.replaceStop(stakeSpan.stop - span.getLength()));
						}
					}
				}
			}
			break;
			
		default:
			throw new IllegalArgumentException( "TouchMode : " + touchMode );
		}

if( DEBUG ) {
	System.err.println( this.getClass().getName() + " : removing : " );
	for (Stake aCollToRemove : collToRemove) {
		System.err.println("  span " + aCollToRemove.getSpan());
	}
	System.err.println( " : adding : " );
	for (Stake aCollToAdd : collToAdd) {
		System.err.println("  span " + aCollToAdd.getSpan());
	}
}
		modSpan		= Span.union( removeAllPr( collToRemove, ce ), addAllPr( collToAdd, ce ));

		// ____ dep ____
		if( dependants != null ) {
			synchronized(sync) {
				for (BasicTrail dependant : dependants) {
					dependant.editClear(source, span, touchMode, ce);
				}
			}
		}

		if( (source != null) && (modSpan != null) ) {
			if( ce != null ) {
				ce.addPerform( new Edit( this, modSpan ));
			} else {
				dispatchModification( source, modSpan );
			}
		}
	}

	public Trail getCutTrail(Span span, int touchMode, long shiftVirtual)
	{
		final BasicTrail		trail	= createEmptyCopy();
		final List<Stake>		stakes	= getCutRange(span, true, touchMode, shiftVirtual);
		
//		trail.setRate( this.getRate() );

//		Collections.sort( stakes, startComparator );
		trail.collStakesByStart.addAll( stakes );
		Collections.sort(stakes, stopComparator);
		trail.collStakesByStop.addAll( stakes );
	
		return trail;
	}
	
	protected abstract BasicTrail createEmptyCopy();

	public static List<Stake> getCuttedRange(List<Stake> stakes, Span span, boolean byStart,
											 int touchMode, long shiftVirtual) {
		if (stakes.isEmpty()) return stakes;
		
		final List<Stake> collResult		= new ArrayList<Stake>();
		final long		start			= span.start;
		final long		stop			= span.stop;
		final boolean	shift			= shiftVirtual != 0;
		Span			stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			for (Stake stake : stakes) {
				stakeSpan = stake.getSpan();
				if (stakeSpan.start >= start) {
					if (shift) {
						collResult.add(stake.shiftVirtual(shiftVirtual));
					} else {
						collResult.add(stake.duplicate());
					}
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for (Stake stake : stakes) {
				stakeSpan = stake.getSpan();

				if (stakeSpan.start >= start) {            // start portion not splitted
					if (stakeSpan.stop <= stop) {            // completely included, just make a copy
						if (shift) {
							collResult.add(stake.shiftVirtual(shiftVirtual));
						} else {
							collResult.add(stake.duplicate());
						}
					} else {                                // adjust stop
						stake = stake.replaceStop(stop);
						if (shift) {
							final Stake stake2 = stake;
							stake = stake.shiftVirtual(shiftVirtual);
							stake2.dispose();    // delete temp product
						}
						collResult.add(stake);
					}
				} else {
					if (stakeSpan.stop <= stop) {            // stop included, just adjust start
						stake = stake.replaceStart(start);
						if (shift) {
							final Stake stake2 = stake;
							stake = stake.shiftVirtual(shiftVirtual);
							stake2.dispose();    // delete temp product
						}
						collResult.add(stake);
					} else {                                // adjust both start and stop
						final Stake stake2 = stake.replaceStart(start);
						stake = stake2.replaceStop(stop);
						stake2.dispose();    // delete temp product
						if (shift) {
							final Stake stake3 = stake;
							stake = stake.shiftVirtual(shiftVirtual);
							stake3.dispose();    // delete temp product
						}
						collResult.add(stake);
					}
				}
			}
			break;
			
		default:
			throw new IllegalArgumentException( "TouchMode : " + touchMode );
		}
		
		return collResult;
	}

	public List<Stake> getCutRange(Span span, boolean byStart, int touchMode, long shiftVirtual) {
		return BasicTrail.getCuttedRange(getRange(span, byStart), span, byStart, touchMode, shiftVirtual);
	}

	public Stake get(int idx, boolean byStart) {
		final List<Stake> coll = byStart ? collStakesByStart : collStakesByStop;
		return coll.get(idx);
	}
	
	public int getNumStakes()
	{
		return collStakesByStart.size();
	}
	
	public boolean isEmpty()
	{
		return collStakesByStart.isEmpty();
	}
	
	public boolean contains( Stake stake )
	{
		return indexOf( stake, true ) >= 0;
	}

	public int indexOf( Stake stake, boolean byStart )
	{
		return editIndexOf( stake, byStart, null );
	}

	public int editIndexOf( Stake stake, boolean byStart, AbstractCompoundEdit ce )
	{
		final	List<Stake> coll;
		final	Comparator<Object> comp	= byStart ? startComparator : stopComparator;
		final	int			idx;
		
		if( ce == null ) {
			coll = byStart ? collStakesByStart : collStakesByStop;
		} else {
			checkEdit( ce );
			coll = byStart ? (collEditByStart == null ? collStakesByStart : collEditByStart) :
							 (collEditByStop == null ? collStakesByStop : collEditByStop);
		}
	
		// "If the list contains multiple elements equal to the specified object,
		//  there is no guarantee which one will be found"
		idx = Collections.binarySearch(coll, stake, comp);
		if (idx >= 0) {
			Stake stake2 = coll.get(idx);
			if (stake2.equals(stake)) return idx;
			for (int idx2 = idx - 1; idx2 >= 0; idx2--) {
				stake2 = coll.get(idx2);
				if (stake2.equals(stake)) return idx2;
			}
			for (int idx2 = idx + 1; idx2 < coll.size(); idx2++) {
				stake2 = coll.get(idx2);
				if (stake2.equals(stake)) return idx2;
			}
		}
		return idx;
	}

	public int indexOf( long pos, boolean byStart )
	{
		return editIndexOf( pos, byStart, null );
	}

	public int editIndexOf(long pos, boolean byStart, AbstractCompoundEdit ce) {
		if (byStart) {
			return Collections.binarySearch(editGetCollByStart(ce), pos, startComparator);
		} else {
			return Collections.binarySearch(editGetCollByStop(ce), pos, stopComparator);
		}
	}

	public Stake editGetLeftMost(int idx, boolean byStart, AbstractCompoundEdit ce) {
		if (idx < 0) {
			idx = -(idx + 2);
			if (idx < 0) return null;
		}

		final List<Stake> coll = byStart ? editGetCollByStart(ce) : editGetCollByStop(ce);
		Stake lastStake = coll.get(idx);
		final long pos = byStart ? lastStake.getSpan().start : lastStake.getSpan().stop;
		Stake nextStake;

		while (idx > 0) {
			nextStake = coll.get(--idx);
			if ((byStart ? nextStake.getSpan().start : nextStake.getSpan().stop) != pos) break;
			lastStake = nextStake;
		}

		return lastStake;
	}

	public Stake editGetRightMost(int idx, boolean byStart, AbstractCompoundEdit ce) {
		final List<Stake> coll = byStart ? editGetCollByStart(ce) : editGetCollByStop(ce);
		final int sizeM1 = coll.size() - 1;

		if (idx < 0) {
			idx = -(idx + 1);
			if (idx > sizeM1) return null;
		}

		Stake lastStake = coll.get(idx);
		final long pos = byStart ? lastStake.getSpan().start : lastStake.getSpan().stop;
		Stake nextStake;

		while (idx < sizeM1) {
			nextStake = coll.get(++idx);
			if ((byStart ? nextStake.getSpan().start : nextStake.getSpan().stop) != pos) break;
			lastStake = nextStake;
		}

		return lastStake;
	}

	public int editGetLeftMostIndex(int idx, boolean byStart, AbstractCompoundEdit ce) {
		final List<Stake> coll = byStart ? editGetCollByStart(ce) : editGetCollByStop(ce);
		return getLeftMostIndex(coll, idx, byStart);
	}

	private int getLeftMostIndex(List<Stake> coll, int idx, boolean byStart) {
		if (idx < 0) {
			idx = -(idx + 2);
			if (idx < 0) return -1;
		}

		Stake stake = coll.get(idx);
		final long pos = byStart ? stake.getSpan().start : stake.getSpan().stop;

		while (idx > 0) {
			stake = coll.get(idx - 1);
			if ((byStart ? stake.getSpan().start : stake.getSpan().stop) != pos) break;
			idx--;
		}

		return idx;
	}

	public int editGetRightMostIndex_(int idx, boolean byStart, AbstractCompoundEdit ce) {
		final List<Stake> coll = byStart ? editGetCollByStart(ce) : editGetCollByStop(ce);
		return getRightMostIndex(coll, idx, byStart);
	}

	private int getRightMostIndex(List<Stake> coll, int idx, boolean byStart) {
		final int sizeM1 = coll.size() - 1;

		if (idx < 0) {
			idx = -(idx + 1);
			if (idx > sizeM1) return -1;
		}

		Stake stake = coll.get(idx);
		final long pos = byStart ? stake.getSpan().start : stake.getSpan().stop;

		while (idx < sizeM1) {
			stake = coll.get(idx + 1);
			if ((byStart ? stake.getSpan().start : stake.getSpan().stop) != pos) break;
			idx++;
		}

		return idx;
	}

	public List<Stake> getAll(boolean byStart) {
		final List<Stake> coll = byStart ? collStakesByStart : collStakesByStop;
		return new ArrayList<Stake>(coll);
	}

	public List<Stake> getAll(int startIdx, int stopIdx, boolean byStart) {
		final List<Stake> coll = byStart ? collStakesByStart : collStakesByStop;
		return new ArrayList<Stake>(coll.subList(startIdx, stopIdx));
	}

	public void add(Object source, Stake stake)
			throws IOException {
		editAddAll(source, Collections.singletonList(stake), null);    // ____ dep ____ handled there
	}

	public void editAdd(Object source, Stake stake, AbstractCompoundEdit ce)
			throws IOException {
		editAddAll(source, Collections.singletonList(stake), ce);    // ____ dep ____ handled there
	}

	public void addAll(Object source, List<Stake> stakes)
			throws IOException {
		editAddAll(source, stakes, null);
	}

	public void editAddAll(Object source, List<Stake> stakes, AbstractCompoundEdit ce)
			throws IOException {
		if (DEBUG) System.err.println("editAddAll " + stakes.size());
		if (stakes.isEmpty()) return;

		if (ce != null) {
			checkEdit(ce);
		}

		final Span span = addAllPr(stakes, ce);

		// ____ dep ____
		if ((dependants != null) && (span != null)) {
			synchronized (sync) {
				for (BasicTrail dependant : dependants) dependant.addAllDep(source, stakes, ce, span);
			}
		}

		if ((source != null) && (span != null)) {
			if (ce != null) {
				ce.addPerform(new Edit(this, span));
			} else {
				dispatchModification(source, span);
			}
		}
	}

	/**
	 * To be overwritten by dependants.
	 */
	protected void addAllDep(Object source, List<Stake> stakes, AbstractCompoundEdit ce, Span span)
			throws IOException {
		/* empty */
	}

	private Span addAllPr(List<Stake> stakes, AbstractCompoundEdit ce) {
		if (stakes.isEmpty()) return null;

		long		start	= Long.MAX_VALUE;
		long		stop	= Long.MIN_VALUE;
		final Span	span;

		for (Stake stake : stakes) {
			sortAddStake(stake, ce);
			start = Math.min(start, stake.getSpan().start);
			stop  = Math.max(stop , stake.getSpan().stop );
		}
		span = new Span(start, stop);
		if (ce != null) ce.addPerform(new Edit(this, stakes, span, EDIT_ADD));

		return span;
	}

	public void remove(Object source, Stake stake)
			throws IOException {
		editRemoveAll(source, Collections.singletonList(stake), null);
	}

	public void editRemove(Object source, Stake stake, AbstractCompoundEdit ce)
			throws IOException {
		editRemoveAll(source, Collections.singletonList(stake), ce);    // ____ dep ____ handled there
	}

	public void removeAll(Object source, List<Stake> stakes)
			throws IOException {
		editRemoveAll(source, stakes, null);
	}

	public void editRemoveAll(Object source, List<Stake> stakes, AbstractCompoundEdit ce)
			throws IOException {
		if (stakes.isEmpty()) return;

		if( ce != null ) {
			checkEdit( ce );
		}

		final Span span = removeAllPr( stakes, ce );

		// ____ dep ____
		if( (dependants != null) && (span != null) ) {
			synchronized(sync) {
				for (BasicTrail dependant : dependants) {
					dependant.removeAllDep(source, stakes, ce, span);
				}
			}
		}

		if ((source != null) && (span != null)) {
			if (ce != null) {
				ce.addPerform(new Edit(this, span));
			} else {
				dispatchModification(source, span);
			}
		}
	}

	/**
	 * To be overwritten by dependants.
	 */
	protected void removeAllDep(Object source, List<Stake> stakes, AbstractCompoundEdit ce, Span span)
			throws IOException {
		/* empty */
	}

	private Span removeAllPr(List<Stake> stakes, AbstractCompoundEdit ce) {
		if (stakes.isEmpty()) return null;
	
		long		start	= Long.MAX_VALUE;
		long		stop	= Long.MIN_VALUE;
		Stake		stake;
		final Span	span;

		for (Object stake1 : stakes) {
			stake = (Stake) stake1;
			sortRemoveStake(stake, ce);
			start = Math.min(start, stake.getSpan().start);
			stop = Math.max(stop, stake.getSpan().stop);
			if (ce == null) stake.dispose();
		}
		span		= new Span( start, stop );
		if( ce != null ) ce.addPerform( new Edit( this, stakes, span, EDIT_REMOVE ));

		return span;
	}

	public void debugDump() {
		/* empty */
	}

	public void debugVerifyContiguity() {
		Stake stake;
		final Span totalSpan = this.getSpan();
		long lastStop = totalSpan.start;
		Span stakeSpan;
		boolean ok = true;

		System.err.println("total Span = " + totalSpan);
		for (int i = 0; i < collStakesByStart.size(); i++) {
			stake = collStakesByStart.get(i);
			stakeSpan = stake.getSpan();
			if (stakeSpan.start != lastStop) {
				System.err.println("! broken contiguity for stake #" + i + " (" + stake + ") : "
						+ stakeSpan + " should have start of " + lastStop);
				ok = false;
			}
			if (stakeSpan.getLength() == 0) {
				System.err.println("! warning : stake #" + i + " (" + stake + ") has zero length");
			} else if (stakeSpan.getLength() < 0) {
				System.err.println("! illegal span length for stake #" + i + " (" + stake + ") : " + stakeSpan);
				ok = false;
			}
			lastStop = stakeSpan.stop;
		}
		System.err.println("--- result: " + (ok ? "OK." : "ERRORNEOUS!"));
	}

	protected void addIgnoreDependants(Stake stake) {
		sortAddStake(stake, null);
	}

	protected void sortAddStake(Stake stake, AbstractCompoundEdit ce) {
		final List<Stake> collByStart, collByStop;
		int idx;

		if (ce == null) {
			collByStart = collStakesByStart;
			collByStop  = collStakesByStop;
		} else {
			ensureEditCopy();
			collByStart = collEditByStart;
			collByStop  = collEditByStop;
		}

		idx = editIndexOf(stake.getSpan().start, true, ce);    // look for position only!
		if (idx < 0) idx = -(idx + 1);
		collByStart.add(idx, stake);
		idx = editIndexOf(stake.getSpan().stop, false, ce);
		if (idx < 0) idx = -(idx + 1);
		collByStop.add(idx, stake);

		stake.setTrail(this);    // ???
	}

	protected void sortRemoveStake(Stake stake, AbstractCompoundEdit ce) {
		final List<Stake> collByStart, collByStop;
		int idx;

		if (ce == null) {
			collByStart = collStakesByStart;
			collByStop  = collStakesByStop;
		} else {
			ensureEditCopy();
			collByStart = collEditByStart;
			collByStop  = collEditByStop;
		}
		idx = editIndexOf(stake, true, ce);
		if (idx >= 0) collByStart.remove(idx);        // look for object equality!
		idx = editIndexOf(stake, false, ce);
		if (idx >= 0) collByStop.remove(idx);
	}

	public void addListener(Trail.Listener listener) {
		if (elm == null) {
			elm = new EventManager(this);
		}
		elm.addListener(listener);
	}

	public void removeListener(Trail.Listener listener) {
		elm.removeListener(listener);
	}

	public void addDependant(BasicTrail sub) {
		if (dependants == null) {
			dependants = new ArrayList<BasicTrail>();
		}
		synchronized (sync) {
			if (dependants.contains(sub)) {
				System.err.println("BasicTrail.addDependant : WARNING : duplicate add");
			}
			dependants.add(sub);
		}
	}

	public void removeDependant(BasicTrail sub) {
		synchronized (sync) {
			if (!dependants.remove(sub)) {
				System.err.println("BasicTrail.removeDependant : WARNING : was not in list");
			}
		}
	}

	public int getNumDependants() {
		if (dependants == null) {
			return 0;
		} else {
			synchronized (sync) {
				return dependants.size();
			}
		}
	}

	public BasicTrail getDependant(int i) {
		synchronized (sync) {
			return dependants.get(i);
		}
	}

	protected void dispatchModification(Object source, Span span) {
		if (elm != null) {
			elm.dispatchEvent(new Trail.Event(this, source, span));
		}
	}

// ---------------- TreeNode interface ---------------- 

	public TreeNode getChildAt( int childIndex )
	{
		return get( childIndex, true );
	}
	
	public int getChildCount()
	{
		return getNumStakes();
	}
	
	public TreeNode getParent()
	{
		return null;
	}
	
	public int getIndex( TreeNode node )
	{
		if( node instanceof Stake ) {
			return indexOf( (Stake) node, true );
		} else {
			return -1;
		}
	}
	
	public boolean getAllowsChildren()
	{
		return true;
	}
	
	public boolean isLeaf()
	{
		return false;
	}

	public Enumeration<?> children() {
		return new ListEnum(getAll(true));
	}

// --------------------- EventManager.Processor interface ---------------------

	/**
	 * This is called by the EventManager
	 * if new events are to be processed. This
	 * will invoke the listener's <code>propertyChanged</code> method.
	 */
	public void processEvent(BasicEvent e) {
		Trail.Listener listener;
		int i;
		Trail.Event te = (Trail.Event) e;

		for (i = 0; i < elm.countListeners(); i++) {
			listener = (Trail.Listener) elm.getListener(i);
			switch (e.getID()) {
				case Trail.Event.MODIFIED:
					listener.trailModified(te);
					break;
				default:
					assert false : e.getID();
					break;
			}
		} // for( i = 0; i < this.countListeners(); i++ )
	}

// --------------------- internal classes ---------------------
	
	// undoable edits
		
	private static final int EDIT_ADD		= 0;
	private static final int EDIT_REMOVE	= 1;
	private static final int EDIT_DISPATCH	= 2;

	protected static final String[] EDIT_NAMES = { "Add", "Remove", "Dispatch" };

	// @todo	disposal is wrong (leaks?) when edit is not performed (e.g. EDIT_ADD not performed)
	// @todo	dispatch should not be a separate edit but one that is sucked and collapsed through multiple EDIT_ADD / EDIT_REMOVE stages
	@SuppressWarnings("serial")
	private static class Edit
			extends BasicUndoableEdit {
		private final int				cmd;
		private final List<Stake>		stakes;
		private final String			key;
		private final BasicTrail		trail;
//		private boolean					removed;
		private boolean					disposeWhenDying;
		private Span					span;

		protected Edit(BasicTrail t, Span span) {
			this(t, null, span, EDIT_DISPATCH, "editChangeTrail");
		}

		protected Edit(BasicTrail t, List<Stake> stakes, Span span, int cmd) {
			this(t, stakes, span, cmd, "editChangeTrail");
		}

		private Edit(BasicTrail t, List<Stake> stakes, Span span, int cmd, String key) {
			this.stakes = stakes;
			this.cmd	= cmd;
			this.key	= key;
			this.span	= span;
			this.trail	= t;
//			removed		= false;
			disposeWhenDying = stakes != null;
		}

		private void addAll() {
			for (Stake stake : stakes) {
				trail.sortAddStake(stake, null);
			}
			disposeWhenDying = false;
		}

		private void removeAll() {
			for (Stake stake : stakes) {
				trail.sortRemoveStake(stake, null);
			}
			disposeWhenDying = true;
		}

		private void disposeAll() {
			for (Stake stake : stakes) {
				stake.dispose();
			}
		}

		public void undo() {
			super.undo();

			switch (cmd) {
				case EDIT_ADD:
					removeAll();
					break;
				case EDIT_REMOVE:
					addAll();
					break;
				case EDIT_DISPATCH:
					trail.dispatchModification(trail, span);
					break;
				default:
					assert false : cmd;
			}
		}

		public void redo() {
			super.redo();
			perform();
		}

		public PerformableEdit perform() {
			switch (cmd) {
				case EDIT_ADD:
					addAll();
					break;
				case EDIT_REMOVE:
					removeAll();
					break;
				case EDIT_DISPATCH:
					trail.dispatchModification(trail, span);
					break;
				default:
					assert false : cmd;
			}
			return this;
		}

		public void die() {
			super.die();
			if (disposeWhenDying) {
				disposeAll();
			}
		}

		public String getPresentationName() {
			return getResourceString(key);
		}

		public String toString() {
			return (trail.getClass().getName() + "$Edit:" + EDIT_NAMES[cmd] + "; span = " + span +
					"; canUndo = " + canUndo() + "; canRedo = " + canRedo() + "; isSignificant = " + isSignificant());
		}

		/**
		 *  Collapses multiple successive edits
		 *  into one single edit. The new edit is sucked off by
		 *  the old one.
		 */
		public boolean addEdit(UndoableEdit anEdit) {
			if (!(anEdit instanceof Edit)) return false;

			final Edit old = (Edit) anEdit;

			if ((old.trail == this.trail) && (old.cmd == this.cmd)) {
				switch (cmd) {
					case EDIT_ADD:
					case EDIT_REMOVE:
						this.stakes.addAll(old.stakes);
						this.span = this.span.union(old.span);
						break;
					case EDIT_DISPATCH:
						this.span = this.span.union(old.span);
						break;
					default:
						assert false : cmd;
				}
				old.die();
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
			return addEdit(anEdit);    // same behaviour in this case
		}
	}

	// ---------------- comparators ----------------

	// XXX TODO - this is really horrible, allowing both `Stake` and `Long`

	private static class StartComparator
			implements Comparator<Object> {
		protected StartComparator() { /* empty */ }

		public int compare(Object o1, Object o2) {
			long l1 = (o1 instanceof Stake) ? ((Stake) o1).getSpan().start : (Long) o1;
			long l2 = (o2 instanceof Stake) ? ((Stake) o2).getSpan().start : (Long) o2;
			return l1 < l2 ? -1 : (l1 > l2 ? 1 : 0);
		}

		public boolean equals(Object o) {
			return ((o != null) && (o instanceof StartComparator));
		}
	}

	private static class StopComparator
			implements Comparator<Object> {
		protected StopComparator() { /* empty */ }

		public int compare(Object o1, Object o2) {
			long l1 = (o1 instanceof Stake) ? ((Stake) o1).getSpan().stop : (Long) o1;
			long l2 = (o2 instanceof Stake) ? ((Stake) o2).getSpan().stop : (Long) o2;
			return l1 < l2 ? -1 : (l1 > l2 ? 1 : 0);
		}

		public boolean equals(Object o) {
			return ((o != null) && (o instanceof StopComparator));
		}
	}
}
