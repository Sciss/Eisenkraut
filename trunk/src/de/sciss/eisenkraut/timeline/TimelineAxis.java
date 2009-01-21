/*
 *  TimelineAxis.java
 *  Eisenkraut
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
 *		12-May-05	re-created from de.sciss.meloncillo.timeline.TimelineAxis
 *		16-Jul-05	allows to switch between time and samples units
 */

// XXX TO-DO : dispose, removeTimelineListener

package de.sciss.eisenkraut.timeline;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;

import de.sciss.eisenkraut.edit.TimelineVisualEdit;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.Axis;
import de.sciss.gui.ComponentHost;
import de.sciss.gui.VectorSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;

import de.sciss.io.Span;

/**
 *  A GUI element for displaying
 *  the timeline's axis (ruler)
 *  which is used to display the
 *  time indices and to allow the
 *  user to position and select the
 *  timeline.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 20-Mar-08
 */
public class TimelineAxis
extends Axis
implements	TimelineListener, MouseListener, MouseMotionListener,
			DynamicListening, PreferenceChangeListener
{
    private final Session   doc;

	// when the user begins a selection by shift+clicking, the
	// initially fixed selection bound is saved to selectionStart.
	private long				selectionStart  = -1;
	private boolean				shiftDrag, altDrag;
    
	public TimelineAxis( Session doc )
	{
		this( doc, null );
	}
	
	/**
	 *  Constructs a new object for
	 *  displaying the timeline ruler
	 *
	 *  @param  root	application root
	 *  @param  doc		session Session
	 */
	public TimelineAxis( Session doc, ComponentHost host )
	{
		super( HORIZONTAL, 0, host );
        
        this.doc    = doc;
		
		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
        new DynamicAncestorAdapter( new DynamicPrefChangeManager(
			AbstractApplication.getApplication().getUserPrefs(), new String[] { PrefsUtil.KEY_TIMEUNITS }, this
		)).addTo( this );
		this.addMouseListener( this );
		this.addMouseMotionListener( this );

//		// ------
//		HelpGlassPane.setHelp( this, "TimelineAxis" );
	}
  
	private void recalcSpace()
	{
		final Span			visibleSpan;
		final double		d1;
		final VectorSpace	space;
	
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
			visibleSpan = doc.timeline.getVisibleSpan();
			if( (getFlags() & TIMEFORMAT) == 0 ) {
				space	= VectorSpace.createLinSpace( visibleSpan.getStart(),
													  visibleSpan.getStop(),
													  0.0, 1.0, null, null, null, null );
			} else {
				d1		= 1.0 / doc.timeline.getRate();
				space	= VectorSpace.createLinSpace( visibleSpan.getStart() * d1,
													  visibleSpan.getStop() * d1,
													  0.0, 1.0, null, null, null, null );
			}
			setSpace( space );
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
	}

	// Sync: attempts doc.timeline
	private void dragTimelinePosition( MouseEvent e )
	{
		int				x   = e.getX();
		Span			span, span2;
		long			position;
		UndoableEdit	edit;

		// translate into a valid time offset
        span        = doc.timeline.getVisibleSpan();
        position    = span.getStart() + (long) ((double) x / (double) getWidth() *
                                                span.getLength());
        position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
        
        if( shiftDrag ) {
			span2	= doc.timeline.getSelectionSpan();
			if( altDrag || span2.isEmpty() ) {
				selectionStart = doc.timeline.getPosition();
				altDrag = false;
			} else if( selectionStart == -1 ) {
				selectionStart = Math.abs( span2.getStart() - position ) >
								 Math.abs( span2.getStop() - position ) ?
								 span2.getStart() : span2.getStop();
			}
			span	= new Span( Math.min( position, selectionStart ),
								Math.max( position, selectionStart ));
			edit	= TimelineVisualEdit.select( this, doc, span ).perform();
        } else {
			if( altDrag ) {
				edit	= new CompoundEdit();
				edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ).perform() );
				edit.addEdit( TimelineVisualEdit.position( this, doc, position ).perform() );
				((CompoundEdit) edit).end();
				altDrag = false;
			} else {
				edit	= TimelineVisualEdit.position( this, doc, position ).perform();
			}
        }
		doc.getUndoManager().addEdit( edit );
	}

// ---------------- PreferenceChangeListener interface ---------------- 

		public void preferenceChange( PreferenceChangeEvent e )
		{
			final String key = e.getKey();
			
			if( key.equals( PrefsUtil.KEY_TIMEUNITS )) {
				int timeUnits = e.getNode().getInt( key, 0 );
				if( timeUnits == 0 ) {
					setFlags( INTEGERS );
				} else {
					setFlags( TIMEFORMAT );
				}
				recalcSpace();
			}
		}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
         doc.timeline.addTimelineListener( this );
    }

    public void stopListening()
    {
         doc.timeline.removeTimelineListener( this );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e )
	{
//		if( isEnabled() ) dispatchMouseMove( e );
	}
	
	public void mousePressed( MouseEvent e )
	{
		shiftDrag		= e.isShiftDown();
		altDrag			= e.isAltDown();
		selectionStart  = -1;
		dragTimelinePosition( e );
	}

	public void mouseExited( MouseEvent e ) { /* ignore */ }
	public void mouseReleased( MouseEvent e ) { /* ignore */ }
	public void mouseClicked( MouseEvent e ) { /* ignore */ }

// ---------------- MouseMotionListener interface ---------------- 
// we're listening to ourselves

    public void mouseMoved( MouseEvent e ) { /* ignore */ }

	public void mouseDragged( MouseEvent e )
	{
		dragTimelinePosition( e );
	}

// ---------------- TimelineListener interface ---------------- 
  
   	public void timelineSelected( TimelineEvent e ) { /* ignore */ }
	public void timelinePositioned( TimelineEvent e ) { /* ignore */ }

	public void timelineChanged( TimelineEvent e )
	{
		recalcSpace();
	}

   	public void timelineScrolled( TimelineEvent e )
    {
		recalcSpace();
    }
}