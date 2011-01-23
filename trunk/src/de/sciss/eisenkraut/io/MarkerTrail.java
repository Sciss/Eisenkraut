/*
 *  MarkerTrail.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *		26-Jul-05	created
 *		02-Aug-05	confirms to new document handler
 *		06-Jan-06	subclass of BasicTrail
 *		06-May-06	supports OSC interface
 *		15-Oct-06	OSC stuff moved to MarkerTrack (seems way more logical)
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
		final List	markStakes;
		final List	marks;
		final int	removed;
		Marker		mark;

//		final AudioFileDescr afd = af.getDescr();
		
		marks = (java.util.List) afd.getProperty( AudioFileDescr.KEY_MARKERS );
		
		if( (marks != null) && !marks.isEmpty() ) {
			markStakes = new ArrayList( marks.size() );
			for( int i = 0; i < marks.size(); i++ ) {
				mark = (Marker) marks.get( i );
				if( mark.pos >= 0 && mark.pos <= afd.length ) {
					markStakes.add( new MarkerStake( mark ));
				}
			}
			
			if( !markStakes.isEmpty() ) addAll( null, markStakes );
			
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