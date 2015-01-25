/*
 *  MarkerTrail.java
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

package de.sciss.eisenkraut.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.sciss.io.AudioFileDescr;
import de.sciss.io.Marker;
import de.sciss.io.Span;
import de.sciss.timebased.BasicTrail;
import de.sciss.timebased.MarkerStake;
import de.sciss.timebased.Stake;

/**
 *	Note: all stop indices are considered <STRONG>inclusive</STRONG>
 *	unlike in practically all other classes.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class MarkerTrail
extends BasicTrail
//implements OSCRouter
{
//	public MarkerTrail( Session doc )
	public MarkerTrail()
	{
		super();
//		this.doc	= doc;
//		osc			= new OSCRouterWrapper( doc, this );
	}
	
//	private MarkerTrail( Session doc, boolean createOSC )
//	{
//		super();
//		this.doc	= doc;
//		if( createOSC ) {
//			osc		= new OSCRouterWrapper( doc, this );
//		} else {
//			osc		= null;
//		}
//	}

	protected BasicTrail createEmptyCopy()
	{
//		return new MarkerTrail( doc, false );	// don't re-create osc listener!!
		return new MarkerTrail();
	}

	public int getDefaultTouchMode()
	{
		return TOUCH_NONE;
	}
	
	public int indexOf( long pos )
	{
		return indexOf( pos, true );
	}

	public MarkerStake get( int idx )
	{
		return (MarkerStake) get( idx, true );
	}

	// clears list and copies all markers from afd
	public void copyFromAudioFile( AudioFileDescr afd )
//	public void copyFromAudioFile( AudioFile af )
	throws IOException
	{
		final List<Stake>	markStakes;
		final List	marks;
		final int	removed;
		Marker		mark;

//		final AudioFileDescr afd = af.getDescr();

		marks = (java.util.List) afd.getProperty(AudioFileDescr.KEY_MARKERS);

		if ((marks != null) && !marks.isEmpty()) {
			markStakes = new ArrayList<Stake>(marks.size());
			for (Object mark1 : marks) {
				mark = (Marker) mark1;
				if (mark.pos >= 0 && mark.pos <= afd.length) {
					markStakes.add(new MarkerStake(mark));
				}
			}

			if (!markStakes.isEmpty()) addAll(null, markStakes);
			
			removed = marks.size() - markStakes.size();
			
			if( removed > 0 ) {
				System.err.println( "Warning: removed " + removed + " illegal marker positions!" );
			}
		}
		
		setRate( afd.rate );
	}

	// copies all markers to afd
	public void copyToAudioFile( AudioFileDescr afd )
	{
		afd.setProperty( AudioFileDescr.KEY_MARKERS, getAll( true ));
	}

	// copies markers in given range to afd (shifts marker positions)
	public void copyToAudioFile( AudioFileDescr afd, Span span )
	{
		if( (span.start == 0) && (span.stop > this.getSpan().stop) ) {
			copyToAudioFile( afd );	// more efficient
		} else {
			afd.setProperty( AudioFileDescr.KEY_MARKERS, getCuttedRange( span, true, TOUCH_NONE, -span.start ));
		}
	}
}