/*
 *  BasicTrail.java
 *  de.sciss.timebased package
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		06-Jan-06	created
 *		25-Feb-06	implements TreeNode ; moved to double precision
 *		01-May-06	clearly distinugishes addPerform edits
 *		15-Oct-06	added getAll( int, int, boolean )
 *		19-Nov-07	removed retainAll calls which are extremely slow
 *		11-Feb-08	fixed editGetRange (calls getRightMostIndex instead of editGetRightMostIndex)
 *		18-Mar-08	editRemove dispatches even when modSpan is empty
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

/**
 *	@version	0.21, 28-Jun-08
 *	@author		Hanns Holger Rutz
 *
 *	@todo		addPerform( new Edit-Dispatch ) ueberpruefen (evtl. redundant)
 */
public abstract class BasicTrail
implements Trail, EventManager.Processor
{
	private static final boolean		DEBUG				= false;

	protected static final Comparator	startComparator		= new StartComparator();
	protected static final Comparator	stopComparator		= new StopComparator();
//	private static final List	collEmpty			= new ArrayList( 1 );
	
	private final List					collStakesByStart	= new ArrayList();	// sorted using StartComparator
	private final List					collStakesByStop	= new ArrayList();	// sorted using StopComparator

	private List						collEditByStart		= null;
	private List						collEditByStop		= null;
	private AbstractCompoundEdit		currentEdit			= null;

	private double						rate;
	
	private EventManager				elm					= null;		// lazy creation
	private List						dependants			= null;		// lazy creation

	// Element : Trail
	
	public BasicTrail()
	{
		/* empty */ 
	}
	
	public double getRate()
	{
		return rate;
	}
	
	public void setRate( double rate )
	{
		this.rate	= rate;
		
//		// ____ dep ____
//		if( dependants != null ) {
//			synchronized( dependants ) {
//				for( int i = 0; i < dependants.size(); i++ ) {
//					((Trail) dependants.get( i )).setRate( rate );
//				}
//			}
//		}
	}

	public void clear( Object source )
	{
		final boolean	wasEmpty	= isEmpty();
		final Span		span		= getSpan();

		clearIgnoreDependants();

		// ____ dep ____
		if( dependants != null ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).clear( source );
				}
			}
		}

		if( (source != null) && !wasEmpty ) {
			dispatchModification( source, span );
		}
	}
	
	protected void clearIgnoreDependants()
	{
		Stake stake;
	
		while( !collStakesByStart.isEmpty() ) {
			stake = (Stake) collStakesByStart.remove( 0 );
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
			synchronized( dependants ) {
				final Object[] dep = dependants.toArray();
				for( int i = 0; i < dep.length; i++ ) {
					((BasicTrail) dep[ i ]).dispose();
				}
			}
		}

		for( int i = 0; i < collStakesByStart.size(); i++ ) {
			((Stake) collStakesByStart.get( i )).dispose();
		}
	
		collStakesByStart.clear();
		collStakesByStop.clear();
	}

	protected List editGetCollByStart( AbstractCompoundEdit ce )
	{
		if( (ce == null) || (collEditByStart == null) ) {
			return collStakesByStart;
		} else {
			return collEditByStart;
		}
	}

	protected List editGetCollByStop( AbstractCompoundEdit ce )
	{
		if( (ce == null) || (collEditByStop == null) ) {
			return collStakesByStop;
		} else {
			return collEditByStop;
		}
	}

	public Span getSpan()
	{
		return editGetSpan( null );
	}
	
	public Span editGetSpan( AbstractCompoundEdit ce )
	{
		return new Span( editGetStart( ce ), editGetStop( ce ));
	}
	
	private long editGetStart( AbstractCompoundEdit ce )
	{
		final List coll = editGetCollByStart( ce );
	
		return( coll.isEmpty() ? 0 : ((Stake) coll.get( 0 )).getSpan().start );
	}
	
	private long editGetStop( AbstractCompoundEdit ce )
	{
		final List coll = editGetCollByStop( ce );

		return( coll.isEmpty() ? 0 : ((Stake) coll.get( coll.size() - 1 )).getSpan().stop );
	}

	public void editBegin( AbstractCompoundEdit ce )
	{
		if( currentEdit != null ) {
			throw new ConcurrentModificationException( "Concurrent editing" );
		}
		currentEdit		= ce;
		collEditByStart	= null;		// dispose ? XXXX
		collEditByStop	= null;		// dispose ? XXXX

		// ____ dep ____
		if( dependants != null ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).editBegin( ce );
				}
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
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).editEnd( ce );
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
		if( collEditByStart == null ) {
			collEditByStart = new ArrayList( collStakesByStart );
			collEditByStop	= new ArrayList( collStakesByStop );
		}
	}

	// returns stakes that intersect OR TOUCH the span
	public List getRange( Span span, boolean byStart )
	{
		return editGetRange( span, byStart, null );
	}
	
	// returns stakes that intersect OR TOUCH the span
	public List editGetRange( Span span, boolean byStart, AbstractCompoundEdit ce )
	{
		final List	collByStart, collByStop;
		List		collResult;
		int			idx;
		
		if( ce == null ) {
			collByStart = collStakesByStart;
			collByStop	= collStakesByStop;
		} else {
			checkEdit( ce );
			collByStart	= collEditByStart == null ? collStakesByStart : collEditByStart;
			collByStop	= collEditByStop == null ? collStakesByStop : collEditByStop;
		}
	
//long t1 = System.currentTimeMillis();
		if( byStart ) {
			idx			= Collections.binarySearch( collByStop, new Long( span.start ), stopComparator );
//long t2 = System.currentTimeMillis();
			if( idx < 0 ) {
				idx		= -(idx + 1);
			} else {
				// "If the list contains multiple elements equal to the specified object,
				//  there is no guarantee which one will be found"
				idx		= getLeftMostIndex( collByStop, idx, false );
			}
//	long t3 = System.currentTimeMillis();
			collResult	= new ArrayList( collByStop.subList( idx, collByStop.size() ));
			
			Collections.sort( collResult, startComparator );
			
//	long t4 = System.currentTimeMillis();
			idx			= Collections.binarySearch( collResult, new Long( span.stop ), startComparator );
//	long t5 = System.currentTimeMillis();
			if( idx < 0 ) {
				idx		= -(idx + 1);
			} else {
				idx		= getRightMostIndex( collResult, idx, true ) + 1;
			}
//	long t6 = System.currentTimeMillis();
			collResult	= collResult.subList( 0, idx );
//	long t7 = System.currentTimeMillis();
//	System.out.println( "editGetRange " + (t2-t1) + " / " + (t3-t2) + " / " + (t4-t3) + " / " + (t5-t4) + " / " + (t6-t5) + " / " + (t7-t6) );
	
		} else {
			idx			= Collections.binarySearch( collByStart, new Long( span.stop ), startComparator );
//long t2 = System.currentTimeMillis();
			if( idx < 0 ) {
				idx		= -(idx + 1);
			} else {
				idx		= getRightMostIndex( collByStart, idx, true ) + 1;
			}
//long t3 = System.currentTimeMillis();
			collResult	= new ArrayList( collByStart.subList( 0, idx ));
		
			Collections.sort( collResult, stopComparator );
		
//long t4 = System.currentTimeMillis();
			idx		= Collections.binarySearch( collResult, new Long( span.start ), stopComparator );
//long t5 = System.currentTimeMillis();
			if( idx < 0 ) {
				idx		= -(idx + 1);
			} else {
				idx		= getLeftMostIndex( collResult, idx, false );
			}
//long t6 = System.currentTimeMillis();
			collResult	= collResult.subList( idx, collResult.size() );
//long t7 = System.currentTimeMillis();
//System.out.println( "editGetRange " + (t2-t1) + " / " + (t3-t2) + " / " + (t4-t3) + " / " + (t5-t4) + " / " + (t6-t5) + " / " + (t7-t6) );
		}

		return collResult;
	}

/*
	// returns stakes that intersect OR TOUCH the span
	public List editGetRangeGAGA( Span span, boolean byStart, AbstractCompoundEdit ce )
	{
		final List	collByStart, collByStop, collUntil, collFrom, collResult;
		int						idx;
		
		if( ce == null ) {
			collByStart = collStakesByStart;
			collByStop	= collStakesByStop;
		} else {
			checkEdit( ce );
			collByStart	= collEditByStart == null ? collStakesByStart : collEditByStart;
			collByStop	= collEditByStop == null ? collStakesByStop : collEditByStop;
		}
	
long t1 = System.currentTimeMillis();
		// "If the list contains multiple elements equal to the specified object,
		//  there is no guarantee which one will be found"
		idx			= Collections.binarySearch( collByStart, new Long( span.stop ), startComparator );
long t2 = System.currentTimeMillis();
		if( idx < 0 ) {
			idx		= -(idx + 1);
		} else {
			idx		= editGetRightMostIndex( idx, true, ce ) + 1;
		}
long t3 = System.currentTimeMillis();
		collUntil	= collByStart.subList( 0, idx );
long t4 = System.currentTimeMillis();
		idx			= Collections.binarySearch( collByStop, new Long( span.start ), stopComparator );
long t5 = System.currentTimeMillis();
		if( idx < 0 ) {
			idx		= -(idx + 1);
		} else {
			idx		= editGetLeftMostIndex( idx, false, ce );
		}
long t6 = System.currentTimeMillis();
		collFrom	= collByStop.subList( idx, collByStop.size() );
long t7 = System.currentTimeMillis();

		// XXX retainAll is slow? see de.sciss.inertia.session.Track for alternative
		// algorithm (which doesn't ensure the result is sorted by start however!)
		
		if( byStart ) {
			collResult	= new ArrayList( collUntil );
			collResult.retainAll( collFrom ); // XXX EXTREMELY SLOW!!!
		} else {
			collResult	= new ArrayList( collFrom );
			collResult.retainAll( collUntil ); // XXX EXTREMELY SLOW!!!
		}
long t8 = System.currentTimeMillis();
System.out.println( "editGetRange " + (t2-t1) + " / " + (t3-t2) + " / " + (t4-t3) + " / " + (t5-t4) + " / " + (t6-t5) + " / " + (t7-t6) + " / " + (t8-t7) );
		return collResult;
	}
*/
	
	public void insert( Object source, Span span )
	{
		editInsert( source, span, getDefaultTouchMode(), null );
	}
	
	public void insert( Object source, Span span, int touchMode )
	{
		editInsert( source, span, touchMode, null );
	}
	
	public void editInsert( Object source, Span span, AbstractCompoundEdit ce )
	{
		editInsert( source, span, getDefaultTouchMode(), ce );
	}
	
	public void editInsert( Object source, Span span, int touchMode, AbstractCompoundEdit ce )
	{
		final long	start			= span.start;
//		final long	stop			= span.stop;
		final long	totStop			= editGetStop( ce );
		final long	delta			= span.getLength();
		
		if( (delta == 0) || (start > totStop) ) return;
		
		final List	collRange		= editGetRange( new Span( start, totStop ), true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List	collToAdd		= new ArrayList();
		final List	collToRemove	= new ArrayList();
		final Span	modSpan;
		Stake		stake;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			// XXX could use binarySearch ?
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.start >= start ) {
					collToRemove.add( stake );
					collToAdd.add( stake.shiftVirtual( delta ));
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop <= start ) continue;
				
				collToRemove.add( stake );

				if( stakeSpan.start >= start ) {			// not splitted
					collToAdd.add( stake.shiftVirtual( delta ));
				} else {
					collToAdd.add( stake.replaceStop( start ));
					stake = stake.replaceStart( start );
					collToAdd.add( stake.shiftVirtual( delta ));
					stake.dispose();						// delete temp product
				}
			}
			break;
			
		case TOUCH_RESIZE:
System.err.println( "BasicTrail.insert, touchmode resize : not tested" );
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop > start ) {
					collToRemove.add( stake );

					if( stakeSpan.start > start ) {
						collToAdd.add( stake.shiftVirtual( delta ));
					} else {
						collToAdd.add( stake.replaceStop( stakeSpan.stop + delta ));
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
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).editInsert( source, span, touchMode, ce  );
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
	 *	<li><code>TOUCH_NONE</code></li> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start remain unaffected ; otherwise they are removed. This mode is usefull
	 *		for markers.
	 *	<li><code>TOUCH_SPLIT</code></li> : the stake is cut at the removed span's start and stop ; a
	 *		middle part (if existing) is removed ; the left part (if existing) remains as is ; the right part
	 *		(if existing) is shifted by <code>-span.getLength()</code>. This mode is usefull for audio regions.
	 *	<li><code>TOUCH_RESIZE</code></li> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start, will keep their start position ; if their stop position lies within the
	 *		removed span, it is truncated to the removed span's start. if their stop position exceeds the removed
	 *		span's stop, the stake's length is shortened by <code>-span.getLength()</code> . 
	 *		intersecting stakes whose <code>start</code> is greater or equal to the
	 *		the removed span's start, will by shortened by <code>(removed_span_stop - stake_start)</code> and
	 *		shifted by <code>-span.getLength()</code> . This mode is usefull for marker regions.
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
		
		final List	collRange		= editGetRange( new Span( start, totStop ), true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List	collToAdd		= new ArrayList();
		final List	collToRemove	= new ArrayList();
		final Span	modSpan;
		Stake		stake;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			// XXX could use binarySearch ?
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.start < start ) continue;

				collToRemove.add( stake );

				if( stakeSpan.start >= stop ) {
					collToAdd.add( stake.shiftVirtual( delta ));
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop > start ) {
				
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start ) {			// start portion not splitted
						if( stakeSpan.start >= stop ) {			// just shifted
							collToAdd.add( stake.shiftVirtual( delta ));
						} else if( stakeSpan.stop > stop ) {	// stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose();					// delete temp product
						}
					} else {
						collToAdd.add( stake.replaceStop( start ));	// start portion splitted
						if( stakeSpan.stop > stop ) {			// stop portion splitted
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose();					// delete temp product
						}
					}
				}
			}
			break;
			
		case TOUCH_RESIZE:
System.err.println( "BasicTrail.remove, touchmode resize : not tested" );
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop > start ) {
				
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start ) {			// start portion not modified
						if( stakeSpan.start >= stop ) {			// just shifted
							collToAdd.add( stake.shiftVirtual( delta ));
						} else if( stakeSpan.stop > stop ) {	// stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose();					// delete temp product
						}
					} else {
						if( stakeSpan.stop <= stop ) {
							collToAdd.add( stake.replaceStop( start ));
						} else {
							collToAdd.add( stake.replaceStop( stakeSpan.stop + delta ));
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
	for( int i = 0; i < collToRemove.size(); i++ ) {
		System.err.println( "  span "+((Stake) collToRemove.get( i )).getSpan() );
	}
	System.err.println( " : adding : " );
	for( int i = 0; i < collToAdd.size(); i++ ) {
		System.err.println( "  span "+((Stake) collToAdd.get( i )).getSpan() );
	}
}
		modSpan		= Span.union( removeAllPr( collToRemove, ce ), addAllPr( collToAdd, ce ));

		// ____ dep ____
		if( dependants != null ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).editRemove( source, span, touchMode, ce  );
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
		final List	collRange		= editGetRange( span, true, ce );
		
		if( collRange.isEmpty() ) return;
		
		final List	collToAdd		= new ArrayList();
		final List	collToRemove	= new ArrayList();
		final Span	modSpan;
		Stake		stake;
		Span		stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.start >= start ) {
					collToRemove.add( stake );
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop > start ) {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start ) {			// start portion not splitted
						if( stakeSpan.stop > stop ) {			// stop portion splitted (otherwise completely removed!)
							collToAdd.add( stake.replaceStart( stop ));
						}
					} else {
						collToAdd.add( stake.replaceStop( start ));	// start portion splitted
						if( stakeSpan.stop > stop ) {				// stop portion splitted
							collToAdd.add( stake.replaceStart( stop ));
						}
					}
				}
			}
			break;
			
		case TOUCH_RESIZE:
System.err.println( "BasicTrail.clear, touchmode resize : not tested" );
			for( int i = 0; i < collRange.size(); i++ ) {
				stake		= (Stake) collRange.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.stop > start ) {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start ) {		// start portion not modified
						if( stakeSpan.stop > stop ) {		// stop portion splitted (otherwise completely removed!)
							collToAdd.add( stake.replaceStart( stop ));
						}
					} else {
						if( stakeSpan.stop <= stop ) {
							collToAdd.add( stake.replaceStop( start ));
						} else {
							collToAdd.add( stake.replaceStop( stakeSpan.stop - span.getLength() ));
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
	for( int i = 0; i < collToRemove.size(); i++ ) {
		System.err.println( "  span "+((Stake) collToRemove.get( i )).getSpan() );
	}
	System.err.println( " : adding : " );
	for( int i = 0; i < collToAdd.size(); i++ ) {
		System.err.println( "  span "+((Stake) collToAdd.get( i )).getSpan() );
	}
}
		modSpan		= Span.union( removeAllPr( collToRemove, ce ), addAllPr( collToAdd, ce ));

		// ____ dep ____
		if( dependants != null ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).editClear( source, span, touchMode, ce  );
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

	public Trail getCuttedTrail( Span span, int touchMode, long shiftVirtual )
	{
		final BasicTrail		trail	= createEmptyCopy();
		final List				stakes	= getCuttedRange( span, true, touchMode, shiftVirtual );
		
//		trail.setRate( this.getRate() );

//		Collections.sort( stakes, startComparator );
		trail.collStakesByStart.addAll( stakes );
		Collections.sort( stakes, stopComparator );
		trail.collStakesByStop.addAll( stakes );
	
		return trail;
	}
	
	protected abstract BasicTrail createEmptyCopy();

	public static List getCuttedRange( List stakes, Span span, boolean byStart,
												 int touchMode, long shiftVirtual )
	{
		if( stakes.isEmpty() ) return stakes;
		
		final List		collResult		= new ArrayList();
		final long		start			= span.start;
		final long		stop			= span.stop;
		final boolean	shift			= shiftVirtual != 0;
		Stake			stake, stake2;
		Span			stakeSpan;
		
		switch( touchMode ) {
		case TOUCH_NONE:
			for( int i = 0; i < stakes.size(); i++ ) {
				stake		= (Stake) stakes.get( i );
				stakeSpan	= stake.getSpan();
				if( stakeSpan.start >= start ) {
					if( shift ) {
						collResult.add( stake.shiftVirtual( shiftVirtual ));
					} else {
						collResult.add( stake.duplicate() );
					}
				}
			}
			break;
			
		case TOUCH_SPLIT:
			for( int i = 0; i < stakes.size(); i++ ) {
				stake		= (Stake) stakes.get( i );
				stakeSpan	= stake.getSpan();
				
				if( stakeSpan.start >= start ) {			// start portion not splitted
					if( stakeSpan.stop <= stop ) {			// completely included, just make a copy
						if( shift ) {
							collResult.add( stake.shiftVirtual( shiftVirtual ));
						} else {
							collResult.add( stake.duplicate() );
						}
					} else {								// adjust stop
						stake = stake.replaceStop( stop );
						if( shift ) {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose();	// delete temp product
						}
						collResult.add( stake );
					}
				} else {
					if( stakeSpan.stop <= stop ) {			// stop included, just adjust start
						stake = stake.replaceStart( start );
						if( shift ) {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose();	// delete temp product
						}
						collResult.add( stake );
					} else {								// adjust both start and stop
						stake2	= stake.replaceStart( start );
						stake	= stake2.replaceStop( stop );
						stake2.dispose();	// delete temp product
						if( shift ) {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose();	// delete temp product
						}
						collResult.add( stake );
					}
				}
			}
			break;
			
		default:
			throw new IllegalArgumentException( "TouchMode : " + touchMode );
		}
		
		return collResult;
	}

	public List getCuttedRange( Span span, boolean byStart, int touchMode, long shiftVirtual )
	{
		return BasicTrail.getCuttedRange( getRange( span, byStart ), span, byStart, touchMode, shiftVirtual );
	}

	public Stake get( int idx, boolean byStart )
	{
		final List coll = byStart ? collStakesByStart : collStakesByStop;
		return (Stake) coll.get( idx );
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
		final	List		coll;
		final	Comparator	comp	= byStart ? startComparator : stopComparator;
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
		idx = Collections.binarySearch( coll, stake, comp );
		if( idx >= 0 ) {
			Stake stake2 = (Stake) coll.get( idx );
			if( stake2.equals( stake )) return idx;
			for( int idx2 = idx - 1; idx2 >= 0; idx2-- ) {
				stake2 = (Stake) coll.get( idx2 );
				if( stake2.equals( stake )) return idx2;
			}
			for( int idx2 = idx + 1; idx2 < coll.size(); idx2++ ) {
				stake2 = (Stake) coll.get( idx2 );
				if( stake2.equals( stake )) return idx2;
			}
		}
		return idx;
	}

	public int indexOf( long pos, boolean byStart )
	{
		return editIndexOf( pos, byStart, null );
	}
	
	public int editIndexOf( long pos, boolean byStart, AbstractCompoundEdit ce )
	{
		if( byStart ) {
			return Collections.binarySearch( editGetCollByStart( ce ), new Long( pos ), startComparator );
		} else {
			return Collections.binarySearch( editGetCollByStop( ce ), new Long( pos ), stopComparator );
		}
	}

	public Stake editGetLeftMost( int idx, boolean byStart, AbstractCompoundEdit ce )
	{
		if( idx < 0 ) {
			idx = -(idx + 2);
			if( idx < 0 ) return null;
		}
		
		final List	coll		= byStart ? editGetCollByStart( ce ) : editGetCollByStop( ce );
		Stake		lastStake	= (Stake) coll.get( idx );
		final long	pos			= byStart ? lastStake.getSpan().start : lastStake.getSpan().stop;
		Stake		nextStake;
		
		while( idx > 0 ) {
			nextStake = (Stake) coll.get( --idx );
			if( (byStart ? nextStake.getSpan().start : nextStake.getSpan().stop) != pos ) break;
			lastStake = nextStake;
		}
		
		return lastStake;
	}

	public Stake editGetRightMost( int idx, boolean byStart, AbstractCompoundEdit ce )
	{
		final List	coll		= byStart ? editGetCollByStart( ce ) : editGetCollByStop( ce );
		final int	sizeM1		= coll.size() - 1;
		
		if( idx < 0 ) {
			idx = -(idx + 1);
			if( idx > sizeM1 ) return null;
		}
		
		Stake					lastStake	= (Stake) coll.get( idx );
		final long				pos			= byStart ? lastStake.getSpan().start : lastStake.getSpan().stop;
		Stake					nextStake;
		
		while( idx < sizeM1 ) {
			nextStake = (Stake) coll.get( ++idx );
			if( (byStart ? nextStake.getSpan().start : nextStake.getSpan().stop) != pos ) break;
			lastStake = nextStake;
		}
		
		return lastStake;
	}

	public int editGetLeftMostIndex( int idx, boolean byStart, AbstractCompoundEdit ce )
	{
		final List coll = byStart ? editGetCollByStart( ce ) : editGetCollByStop( ce );
		return getLeftMostIndex( coll, idx, byStart );
	}

	private int getLeftMostIndex( List coll, int idx, boolean byStart )
	{
		if( idx < 0 ) {
			idx = -(idx + 2);
			if( idx < 0 ) return -1;
		}
		
		Stake		stake		= (Stake) coll.get( idx );
		final long	pos			= byStart ? stake.getSpan().start : stake.getSpan().stop;
		
		while( idx > 0 ) {
			stake = (Stake) coll.get( idx - 1 );
			if( (byStart ? stake.getSpan().start : stake.getSpan().stop) != pos ) break;
			idx--;
		}
		
		return idx;
	}

	public int editGetRightMostIndex_( int idx, boolean byStart, AbstractCompoundEdit ce )
	{
		final List coll = byStart ? editGetCollByStart( ce ) : editGetCollByStop( ce );
		return getRightMostIndex( coll, idx, byStart );
	}
	
	private int getRightMostIndex( List coll, int idx, boolean byStart )
	{
		final int sizeM1 = coll.size() - 1;
		
		if( idx < 0 ) {
			idx = -(idx + 1);
			if( idx > sizeM1 ) return -1;
		}
		
		Stake					stake		= (Stake) coll.get( idx );
		final long				pos			= byStart ? stake.getSpan().start : stake.getSpan().stop;
		
		while( idx < sizeM1 ) {
			stake = (Stake) coll.get( idx + 1 );
			if( (byStart ? stake.getSpan().start : stake.getSpan().stop) != pos ) break;
			idx++;
		}
		
		return idx;
	}

	public List getAll( boolean byStart )
	{
		final List coll = byStart ? collStakesByStart : collStakesByStop;
		return new ArrayList( coll );
	}

	public List getAll( int startIdx, int stopIdx, boolean byStart )
	{
		final List coll = byStart ? collStakesByStart : collStakesByStop;
		return new ArrayList( coll.subList( startIdx, stopIdx ));
	}
	
	public void add( Object source, Stake stake )
	throws IOException
	{
//		if( DEBUG ) System.err.println( "add "+stake.getClass().getName() );
		editAddAll( source, Collections.singletonList( stake ), null );	// ____ dep ____ handled there
	}

	public void editAdd( Object source, Stake stake, AbstractCompoundEdit ce )
	throws IOException
	{
//		if( DEBUG ) System.err.println( "addAdd "+stake.getClass().getName() );
		editAddAll( source, Collections.singletonList( stake ), ce );	// ____ dep ____ handled there
	}

	public void addAll( Object source, List stakes )
	throws IOException
	{
//		if( DEBUG ) System.err.println( "addAll "+stakes.size() );
		editAddAll( source, stakes, null );
	}

	public void editAddAll( Object source, List stakes, AbstractCompoundEdit ce )
	throws IOException
	{
		if( DEBUG ) System.err.println( "editAddAll "+stakes.size() );
		if( stakes.isEmpty() ) return;
	
		if( ce != null ) {
			checkEdit( ce );
		}
	
		final Span span = addAllPr( stakes, ce );

		// ____ dep ____
		if( (dependants != null) && (span != null) ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).addAllDep( source, stakes, ce, span );
				}
			}
		}

		if( (source != null) && (span != null) ) {
			if( ce != null ) {
				ce.addPerform( new Edit( this, span ));
			} else {
				dispatchModification( source, span );
			}
		}
	}
	
	/**
	 *	To be overwritten by dependants.
	 */
	protected void addAllDep( Object source, List stakes, AbstractCompoundEdit ce, Span span )
	throws IOException
	{
		/* empty */ 
	}

	private Span addAllPr( List stakes, AbstractCompoundEdit ce )
	{
		if( stakes.isEmpty() ) return null;

		long		start	= Long.MAX_VALUE;
		long		stop	= Long.MIN_VALUE;
		Stake		stake;
		final Span	span;
		
		for( int i = 0; i < stakes.size(); i++ ) {
			stake	= (Stake) stakes.get( i );
			sortAddStake( stake, ce );
			start	= Math.min( start, stake.getSpan().start );
			stop	= Math.max( stop, stake.getSpan().stop );
		}
		span		= new Span( start, stop );
		if( ce != null ) ce.addPerform( new Edit( this, stakes, span, EDIT_ADD ));

		return span;
	}

	public void remove( Object source, Stake stake )
	throws IOException
	{
		editRemoveAll( source, Collections.singletonList( stake ), null );
	}

	public void editRemove( Object source, Stake stake, AbstractCompoundEdit ce )
	throws IOException
	{
		editRemoveAll( source, Collections.singletonList( stake ), ce );	// ____ dep ____ handled there
	}

	public void removeAll( Object source, List stakes )
	throws IOException
	{
		editRemoveAll( source, stakes, null );
	}

	public void editRemoveAll( Object source, List stakes, AbstractCompoundEdit ce )
	throws IOException
	{
		if( stakes.isEmpty() ) return;

		if( ce != null ) {
			checkEdit( ce );
		}

		final Span span = removeAllPr( stakes, ce );

		// ____ dep ____
		if( (dependants != null) && (span != null) ) {
			synchronized( dependants ) {
				for( int i = 0; i < dependants.size(); i++ ) {
					((BasicTrail) dependants.get( i )).removeAllDep( source, stakes, ce, span );
				}
			}
		}

		if( (source != null) && (span != null) ) {
			if( ce != null ) {
				ce.addPerform( new Edit( this, span ));
			} else {
				dispatchModification( source, span );
			}
		}
	}
	
	/**
	 *	To be overwritten by dependants.
	 */
	protected void removeAllDep( Object source, List stakes, AbstractCompoundEdit ce, Span span )
	throws IOException
	{
		/* empty */
	}

	private Span removeAllPr( List stakes, AbstractCompoundEdit ce )
	{
		if( stakes.isEmpty() ) return null;
	
		long		start	= Long.MAX_VALUE;
		long		stop	= Long.MIN_VALUE;
		Stake		stake;
		final Span	span;
	
		for( int i = 0; i < stakes.size(); i++ ) {
			stake	= (Stake) stakes.get( i );
			sortRemoveStake( stake, ce );
			start	= Math.min( start, stake.getSpan().start );
			stop	= Math.max( stop, stake.getSpan().stop );
			if( ce == null ) stake.dispose();
		}
		span		= new Span( start, stop );
		if( ce != null ) ce.addPerform( new Edit( this, stakes, span, EDIT_REMOVE ));

		return span;
	}

    public void debugDump()
	{
    	/* empty */ 
    }
    
    public void debugVerifyContiguity()
    {
    	Stake	 stake;
    	final 	Span totalSpan = this.getSpan();
    	long  	lastStop	= totalSpan.start;
    	Span  	stakeSpan;
    	boolean	ok			= true;
    	
    	System.err.println( "total Span = " + totalSpan );
    	for( int i = 0; i < collStakesByStart.size(); i++ ) {
    		stake		= (Stake) collStakesByStart.get( i );
    		stakeSpan	= stake.getSpan();
    		if( stakeSpan.start != lastStop ) {
    			System.err.println( "! broken contiguity for stake #" + i + " (" + stake + ") : "
    					+ stakeSpan + " should have start of " + lastStop );
    			ok	= false;
    		}
    		if( stakeSpan.getLength() == 0 ) {
    			System.err.println( "! warning : stake #" + i + " (" + stake + ") has zero length" );
    		} else if( stakeSpan.getLength() < 0 ) {
    			System.err.println( "! illegal span length for stake #" + i + " (" + stake + ") : " + stakeSpan );
    			ok	= false;
    		}
    		lastStop = stakeSpan.stop;
    	}
    	System.err.println( "--- result: " + (ok ? "OK." : "ERRORNEOUS!") );
    }
	
	protected void addIgnoreDependants( Stake stake )
	{
		sortAddStake( stake, null );
	}

	protected void sortAddStake( Stake stake, AbstractCompoundEdit ce)
	{
		final List	collByStart, collByStop;
		int						idx;

		if( ce == null ) {
			collByStart		= collStakesByStart;
			collByStop		= collStakesByStop;
		} else {
			ensureEditCopy();
			collByStart		= collEditByStart;
			collByStop		= collEditByStop;
		}

		idx		= editIndexOf( stake.getSpan().start, true, ce );	// look for position only!
		if( idx < 0 ) idx = -(idx + 1);
		collByStart.add( idx, stake );
		idx		= editIndexOf( stake.getSpan().stop, false, ce );
		if( idx < 0 ) idx = -(idx + 1);
		collByStop.add( idx, stake );
		
		stake.setTrail( this );	// ???
	}
	
	protected void sortRemoveStake( Stake stake, AbstractCompoundEdit ce )
	{
		final List	collByStart, collByStop;
		int						idx;
		
		if( ce == null ) {
			collByStart		= collStakesByStart;
			collByStop		= collStakesByStop;
		} else {
			ensureEditCopy();
			collByStart		= collEditByStart;
			collByStop		= collEditByStop;
		}
		idx		= editIndexOf( stake, true, ce );
		if( idx >= 0 ) collByStart.remove( idx );		// look for object equality!
		idx		= editIndexOf( stake, false, ce );
		if( idx >= 0 ) collByStop.remove( idx );

//		stake.setTrail( null );
	}
	
	public void addListener( Trail.Listener listener )
	{
		if( elm == null ) {
			elm = new EventManager( this );
		}
		elm.addListener( listener );
	}

	public void removeListener( Trail.Listener listener )
	{
		elm.removeListener( listener );
	}

	public void addDependant( Trail sub )
	{
		if( dependants == null ) {
			dependants = new ArrayList();
		}
		synchronized( dependants ) {
			if( dependants.contains( sub )) {
				System.err.println( "BasicTrail.addDependant : WARNING : duplicate add" );
			}
			dependants.add( sub );
		}
	}

	public void removeDependant( Trail sub )
	{
		synchronized( dependants ) {
			if( !dependants.remove( sub )) {
				System.err.println( "BasicTrail.removeDependant : WARNING : was not in list" );
			}
		}
	}
	
	public int getNumDependants()
	{
		if( dependants == null ) {
			return 0;
		} else {
			synchronized( dependants ) {
				return dependants.size();
			}
		}
	}
	
	public BasicTrail getDependant( int i )
	{
		synchronized( dependants ) {
			return (BasicTrail) dependants.get( i );
		}
	}
	
	protected void dispatchModification( Object source, Span span )
	{
		if( elm != null ) {
			elm.dispatchEvent( new Trail.Event( this, source, span ));
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
	
	public Enumeration children()
	{
		return new ListEnum( getAll( true ));
	}

// --------------------- EventManager.Processor interface ---------------------
	
	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed. This
	 *  will invoke the listener's <code>propertyChanged</code> method.
	 */
	public void processEvent( BasicEvent e )
	{
		Trail.Listener listener;
		int i;
		Trail.Event te = (Trail.Event) e;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (Trail.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case Trail.Event.MODIFIED:
				listener.trailModified( te );
				break;
			default:
				assert false : e.getID();
				break;
			}
		} // for( i = 0; i < this.countListeners(); i++ )
	}

// --------------------- internal classes ---------------------
	
	// undable edits
		
	private static final int EDIT_ADD		= 0;
	private static final int EDIT_REMOVE	= 1;
	private static final int EDIT_DISPATCH	= 2;

	protected static final String[] EDIT_NAMES = { "Add", "Remove", "Dispatch" };

	// @todo	disposal is wrong (leaks?) when edit is not performed (e.g. EDIT_ADD not performed)
	// @todo	dispatch should not be a separate edit but one that is sucked and collapsed through multiple EDIT_ADD / EDIT_REMOVE stages
	private static class Edit
	extends BasicUndoableEdit
	{
		private final int				cmd;
		private final List				stakes;
		private final String			key;
		private final BasicTrail		trail;
//		private boolean					removed;
		private boolean					disposeWhenDying;
		private Span					span;
		
		protected Edit( BasicTrail t, Span span )
		{
			this( t, null, span, EDIT_DISPATCH, "editChangeTrail" );
		}
	
		protected Edit( BasicTrail t, List stakes, Span span, int cmd )
		{
			this( t, stakes, span, cmd, "editChangeTrail" );
		}

		private Edit( BasicTrail t, List stakes, Span span, int cmd, String key )
		{
			this.stakes	= stakes;
			this.cmd	= cmd;
			this.key	= key;
			this.span	= span;
			this.trail	= t;
//			removed		= false;
			disposeWhenDying = stakes != null;
		}
		
		private void addAll()
		{
			for( int i = 0; i < stakes.size(); i++ ) {
				trail.sortAddStake( (Stake) stakes.get( i ), null );
			}
//			removed		= false;
			disposeWhenDying	= false;
		}

		private void removeAll()
		{
			for( int i = 0; i < stakes.size(); i++ ) {
				trail.sortRemoveStake( (Stake) stakes.get( i ), null );
			}
//			removed		= true;
			disposeWhenDying	= true;
		}
		
		private void disposeAll()
		{
			for( int i = 0; i < stakes.size(); i++ ) {
				((Stake) stakes.get( i )).dispose();
			}
		}

		public void undo()
		{
			super.undo();
			
			switch( cmd ) {
			case EDIT_ADD:
				removeAll();
				break;
			case EDIT_REMOVE:
				addAll();
				break;
			case EDIT_DISPATCH:
				trail.dispatchModification( trail, span );
				break;
			default:
				assert false : cmd;
			}
		}
		
		public void redo()
		{
			super.redo();
			perform();
		}
		
		public PerformableEdit perform()
		{
			switch( cmd ) {
			case EDIT_ADD:
				addAll();
				break;
			case EDIT_REMOVE:
				removeAll();
				break;
			case EDIT_DISPATCH:
				trail.dispatchModification( trail, span );
				break;
			default:
				assert false : cmd;
			}
			return this;
		}
		
		public void die()
		{
			super.die();
//			if( removed ) {
			if( disposeWhenDying ) {
				disposeAll();
			}
		}
		
		public String getPresentationName()
		{
			return getResourceString( key );
		}
		
//		public void debugDump( int nest )
//		{
//			System.err.println( this.toString() + " -> " + EDIT_NAMES[ cmd ] + "; span = "+span );
//		}
		
		public String toString()
		{
			return( trail.getClass().getName() + "$Edit:" + EDIT_NAMES[ cmd ]+"; span = "+span+
				"; canUndo = "+canUndo()+"; canRedo = "+canRedo()+"; isSignificant = "+isSignificant() );
		}

		/**
		 *  Collapse multiple successive edits
		 *  into one single edit. The new edit is sucked off by
		 *  the old one.
		 */
		public boolean addEdit( UndoableEdit anEdit )
		{
			if( !(anEdit instanceof Edit) ) return false;
			
			final Edit old = (Edit) anEdit;
			
			if( (old.trail == this.trail) && (old.cmd == this.cmd) ) {
				switch( cmd ) {
				case EDIT_ADD:
				case EDIT_REMOVE:
					this.stakes.addAll( old.stakes );
					// THRU
				case EDIT_DISPATCH:
					this.span = this.span.union( old.span );
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
		 *  Collapse multiple successive edits
		 *  into one single edit. The old edit is sucked off by
		 *  the new one.
		 */
		public boolean replaceEdit( UndoableEdit anEdit )
		{
			return addEdit( anEdit );	// same behaviour in this case
		}
	}

	// ---------------- comparators ----------------

	private static class StartComparator
	implements Comparator
	{
		protected StartComparator() { /* empty */ }
		
		public int compare( Object o1, Object o2 )
		{			
			if( o1 instanceof Stake ) o1 = ((Stake) o1).getSpan();
			if( o2 instanceof Stake ) o2 = ((Stake) o2).getSpan();
			
			return Span.startComparator.compare( o1, o2 );
		}
				   
		public boolean equals( Object o )
		{
			return( (o != null) && (o instanceof StartComparator) );
		}
	}

	private static class StopComparator
	implements Comparator
	{
		protected StopComparator() { /* empty */ }

		public int compare( Object o1, Object o2 )
		{
			if( o1 instanceof Stake ) o1 = ((Stake) o1).getSpan();
			if( o2 instanceof Stake ) o2 = ((Stake) o2).getSpan();
			
			return Span.stopComparator.compare( o1, o2 );
		}
				   
		public boolean equals( Object o )
		{
			return( (o != null) && (o instanceof StopComparator) );
		}
	}
}
