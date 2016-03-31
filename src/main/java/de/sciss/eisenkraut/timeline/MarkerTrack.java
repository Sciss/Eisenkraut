/*
 *  MarkerTrack.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.timeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.tree.TreeNode;

import de.sciss.eisenkraut.edit.BasicCompoundEdit;
import de.sciss.eisenkraut.io.MarkerTrail;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.session.Session;

import de.sciss.app.AbstractApplication;
import de.sciss.io.Marker;
import de.sciss.io.Span;
import de.sciss.timebased.MarkerStake;
import de.sciss.timebased.Stake;
import de.sciss.timebased.Trail;

public class MarkerTrack
		extends Track
		implements OSCRouter {

	private final MarkerTrail trail;

	private static final String		OSC_MARKERS			= "markers";
	
	private final OSCRouterWrapper	osc;
	private final Session			doc;

	public MarkerTrack( Session doc )
	{
		super();
		
//		this.trail	= new MarkerTrail( doc );
		this.trail	= new MarkerTrail();
	
		setName( AbstractApplication.getApplication().getResourceString( "labelMarkers" ));

		this.doc	= doc;
		osc			= new OSCRouterWrapper( doc, this );
	}
	
	public Trail getTrail()
	{
		return trail;
	}

	public Class<?> getDefaultEditor()
	{
		return null;	// XXX
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

// ---------------- TreeNode interface ---------------- 

	public TreeNode getChildAt( int childIndex )
	{
		return trail.get( childIndex );
	}
	
	public int getChildCount()
	{
		return trail.getNumStakes();
	}
	
	public TreeNode getParent()
	{
		return null;
	}
	
	public int getIndex( TreeNode node )
	{
		if( node instanceof Stake ) {
			return trail.indexOf( (Stake) node, true );
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
	
	public Enumeration<?> children()
	{
		return trail.children();
	}

	// ------------- OSCRouter interface -------------
	
	public String oscGetPathComponent()
	{
		return OSC_MARKERS;
	}

	public void oscRoute( RoutedOSCMessage rom )
	{
		osc.oscRoute( rom );
	}
	
	public void oscAddRouter( OSCRouter subRouter )
	{
		osc.oscAddRouter( subRouter );
	}

	public void oscRemoveRouter( OSCRouter subRouter )
	{
		osc.oscRemoveRouter( subRouter );
	}
	
	/**
	 *	This command queries the index of a marker
	 *	specified by its position. If no marker is found
	 *	at this position, the resulting index is
	 *	(-(insertion point) - 1). If more than one
	 *	marker is found at this position, the smallest
	 *	index is returned.
	 *	The reply message looks as follows:
	 *	<pre>
	 *	[ "/get.reply", &lt;getID&gt;, [ &lt;(int) idx&gt; * N ]]
	 *	</pre>
	 *	Quick calculation : (1 &lt;&lt; 31 - 1) / 96000 / 60 / 60 --&gt; with 32bit signed ints, audio at 96 kHz can be represented if length doesn't exceed about 6 hours
	 */
	public Object[] oscGet_indexOf( RoutedOSCMessage rom )
	{
		final Object[]	values	= new Object[ rom.msg.getArgCount() - 3 ];
		int				argIdx	= 3;
		long			n1;
		int				idx1;
	
		try {
			for( int i = 0; i < values.length; argIdx++, i++ ) {
				n1			= ((Number) rom.msg.getArg( argIdx )).longValue();
				idx1		= trail.indexOf( n1, true );
				if( idx1 > 0 ) {
					idx1	= trail.editGetLeftMostIndex( idx1, true, null );
				}
				values[ i ] = idx1;
			}
			return values;
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		return null;
	}

	public Object[] oscGet_span( RoutedOSCMessage rom )
	{
		if( rom.msg.getArgCount() != 5 ) {
			OSCRoot.failedArgCount( rom );
			return null;
		}
		int argIdx = 3;
		try {
			final long n1	= ((Number) rom.msg.getArg( argIdx )).longValue();
			final long n2	= ((Number) rom.msg.getArg( ++argIdx )).longValue();
			return oscGetMarkers( trail.getRange( new Span( n1, n2 ), true ));
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		return null;
	}

	public Object[] oscGet_range( RoutedOSCMessage rom )
	{
		if( rom.msg.getArgCount() != 5 ) {
			OSCRoot.failedArgCount( rom );
			return null;
		}
		int argIdx = 3;
		try {
			final int idx1	= Math.max( 0, ((Number) rom.msg.getArg( argIdx )).intValue() );
			final int idx2	= Math.max( idx1, Math.min( trail.getNumStakes(), ((Number) rom.msg.getArg( ++argIdx )).intValue() ));
//			return oscGetMarkers( new ArrayList( trail.editGetCollByStart( null ).subList( idx1, idx2 )));
			return oscGetMarkers( trail.getAll( idx1, idx2, true ));
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		return null;
	}

	public Object[] oscGet_at(RoutedOSCMessage rom) {
		final List<Object> coll = new ArrayList<Object>();
		int argIdx = 3;
		int idx1;
		try {
			for (; argIdx < rom.msg.getArgCount(); argIdx++) {
				idx1 = ((Number) rom.msg.getArg(argIdx)).intValue();
				if ((idx1 >= 0) && (idx1 < trail.getNumStakes())) {
					coll.add(trail.get(idx1, true));
				} else {
					coll.add(new Marker(-1, ""));
				}
			}
			return oscGetMarkers(coll);
		} catch (ClassCastException e1) {
			OSCRoot.failedArgType(rom, argIdx);
		}
		return null;
	}

	/*
	 *	These commands query a range of markers.
	 *	The reply message looks as follows:
	 *
	 *	[ "/get.reply", &lt;getID&gt;, [ &lt;(int) pos&gt;, &lt;(String) markName&gt; ] * N ]]
	 *
	 *	NOTE: as soon as SuperCollider supports 64bit OSC tags,
	 *	the reply will send &lt;pos&gt; as a 64bit long!!
	 *
	 *	Quick calculation : (1 &lt;&lt; 31 - 1) / 96000 / 60 / 60 --&gt; with 32bit signed ints, audio at 96 kHz can be represented if length doesn't exceed about 6 hours
	 */
	private Object[] oscGetMarkers(List<?> coll) {
		final Object[] args = new Object[coll.size() << 1];
		Marker m;
		for (int i = 0, j = 0; i < args.length; j++) {
			m = (Marker) coll.get(j);
			args[i++] = m.pos;
			args[i++] = m.name;
		}
		return args;
	}

	public Object oscQuery_count()
	{
		return trail.getNumStakes();
	}

	public Object oscQuery_spanStart()
	{
		return trail.getSpan().start;
	}
	
	public Object oscQuery_spanStop()
	{
		return trail.getSpan().stop;
	}
	
	public Object oscQuery_trackSelected()
	{
		return doc.selectedTracks.contains(doc.markerTrack) ? 1 : 0;
	}

	/**
	 *	<pre>
	 *	[ &lt;address&gt;, "add", [ &lt;(long) pos&gt;, &lt;(String) name&gt; ] * N ]
	 *	</pre>
	 */
	public void oscCmd_add( RoutedOSCMessage rom )
	{
		final int					num			= rom.msg.getArgCount() >> 1;
		if( num == 0 ) return;
//		final long					timelineLen	= doc.timeline.getLength();	// XXX sync
		final BasicCompoundEdit		ce;
		final List<Stake> coll = new ArrayList<Stake>(num);
		int							argIdx		= 1;
		long						pos;
		String						name;
		
		
		try {
			for( int i = 0; i < num; i++ ) {
				pos		= Math.max( 0, ((Number) rom.msg.getArg( argIdx )).longValue() );
				argIdx++;
				name	= rom.msg.getArg( argIdx ).toString();
				argIdx++;
				coll.add( new MarkerStake( pos, name ));
			}
			ce = new BasicCompoundEdit(getResourceString(num > 1 ? "editAddMarkers" : "editAddMarker"));
			trail.editBegin(ce);
			try {
				trail.editAddAll(this, coll, ce);
			} finally {
				trail.editEnd(ce);
			}
			ce.perform();
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IOException e1 ) {	// should never happen
			System.err.println( e1.getLocalizedMessage() );
		}
	}

	/**
	 *	either of:
	 *	<pre>
	 *	[ &lt;address&gt;, "remove", "span", &lt;(long) startPos&gt;, &lt;(long) stopPos&gt; ]
	 *	[ &lt;address&gt;, "remove", "range", &lt;(int) startIdx&gt;, &lt;(int) stopIdx&gt; ]
	 *	[ &lt;address&gt;, "remove", "at", [ &lt;(int) idx&gt; ] * N ]
	 *	</pre>
	 */
	public void oscCmd_remove( RoutedOSCMessage rom )
	{
		final BasicCompoundEdit	ce;
		final List<Stake> coll;
		final long					n1, n2;
		int							i1, i2;
		int							argIdx		= 1;
		
		try {
			if( rom.msg.getArg( argIdx ).equals( "span" )) {
				argIdx++;
				n1		= ((Number) rom.msg.getArg( argIdx )).longValue();
				argIdx++;
				n2		= ((Number) rom.msg.getArg( argIdx )).longValue();
				coll	= trail.getRange( new Span( n1, n2 ), true );
				
			} else if( rom.msg.getArg( argIdx ).equals( "range" )) {
				argIdx++;
				i1		= Math.max( 0, ((Number) rom.msg.getArg( argIdx )).intValue() );
				argIdx++;
				i2 = Math.min(trail.getNumStakes(), ((Number) rom.msg.getArg(argIdx)).intValue());
				coll = new ArrayList<Stake>(Math.max(1, i2 - i1));
				while (i1 < i2) {
					coll.add(trail.get(i1++));
				}

			} else if( rom.msg.getArg( argIdx ).equals( "at" )) {
				argIdx++;
				coll = new ArrayList<Stake>( rom.msg.getArgCount() - argIdx );
				for( ; argIdx < rom.msg.getArgCount(); argIdx++ ) {
					i1	= ((Number) rom.msg.getArg( argIdx )).intValue();
					if( (i1 >= 0) && (i1 < trail.getNumStakes()) ) {
						coll.add( trail.get( i1 ));
					}
				}
			
			} else {
				OSCRoot.failedArgValue( rom, argIdx );
				return;
			}

			if( coll.isEmpty() ) return;

			ce = new BasicCompoundEdit(getResourceString(coll.size() > 1 ? "editDeleteMarkers" : "editDeleteMarker"));
			trail.editBegin(ce);
			try {
				trail.editRemoveAll(this, coll, ce);
			} finally {
				trail.editEnd(ce);
			}
			ce.perform();
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IOException e1 ) {	// should never happen
			System.err.println( e1.getLocalizedMessage() );
		}
	}
}