/*
 *  ClipboardTrackList.java
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
 *		27-Jan-06	created
 */

package de.sciss.eisenkraut.session;

import java.awt.EventQueue;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.sciss.eisenkraut.timeline.Track;

import de.sciss.io.Span;
import de.sciss.timebased.Trail;
import de.sciss.util.Disposable;

/**
 *  @author				Hanns Holger Rutz
 *  @version			0.70, 28-Apr-08
 *
 *	@todo				disposeAll is not a safe decision because another
 *						document might be in the middle of pasting this track list!!
 *
 *	@synchronization	all methods must be called in the event thread
 */
public class ClipboardTrackList
implements Transferable, ClipboardOwner, Disposable
{
	private static final boolean DEBUG	= false;

	public static final DataFlavor		trackListFlavor	= new DataFlavor( ClipboardTrackList.class, null );

	// the items in this map are only removed in disposeAll(), it's therefore
	// crucial that disposeAll() is called when a document is closed!
	private static final Map	mapTrackLists	= new HashMap();	// key = Document, value = Set (element = ClipboardTrackList)

	private final static DataFlavor[]	flavors			= {
		trackListFlavor // , AudioFileRegion.flavor
	};
	private final static DataFlavor[]	noFlavors		= new DataFlavor[ 0 ];

	private final Span					span;
	private boolean						disposed		= false;
	
	// key = Class of Trail ; value = Track.Info
	private final Map			mapInfos	= new HashMap();
	// key = Class of Trail; value = Trail (sub)
	private final Map			mapTrails	= new HashMap();

	public ClipboardTrackList( Session doc )
	{
		this( doc, doc.timeline.getSelectionSpan(), doc.selectedTracks.getAll() );
	}
	
	public ClipboardTrackList( Session doc, Span span, List tracks )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		this.span	= span;
		
		final List				infos = Track.getInfos( tracks, doc.tracks.getAll() );
		Track.Info				ti;
		Trail					subTrail;
		Set						setTrackLists;
		
		for( int i = 0; i < infos.size(); i++ ) {
			ti			= (Track.Info) infos.get( i );
			subTrail	= ti.trail.getCuttedTrail( span, Trail.TOUCH_SPLIT, 0 );
			mapTrails.put( ti.trail.getClass(), subTrail );
			mapInfos.put( ti.trail.getClass(), ti );
		}
		
		setTrackLists	= (Set) mapTrackLists.get( doc );
		if( setTrackLists == null ) {
			setTrackLists	= new HashSet();
			mapTrackLists.put( doc, setTrackLists );
		}
		setTrackLists.add( this );
		if( DEBUG ) System.err.println( "new : "+hashCode() );
	}
	
	public Span getSpan()
	{
		return span;
	}
	
//	public int getTrailNum()
//	{
//		return trackClasses.length;
//	}
//
//	public Class getTrailClass( int idx )
//	{
//		return trackClasses[ idx ];
//	}
	
	public int getTrackNum( Class trailClass )
	{
		final Track.Info ti = (Track.Info) mapInfos.get( trailClass );
		if( ti == null ) return 0;
		return ti.numTracks;
	}
	
	public boolean[] getTrackMap( Class trailClass )
	{
		final Track.Info ti = (Track.Info) mapInfos.get( trailClass );
		if( ti == null ) return new boolean[ 0 ];
		return ti.trackMap;
	}

	public Trail getSubTrail( Class trailClass )
	{
		return( (Trail) mapTrails.get( trailClass ));
	}
	
	public void dispose()
	{
		if( DEBUG ) System.err.println( "dispose : "+hashCode() );
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( disposed ) return;

		for( Iterator iter = mapTrails.values().iterator(); iter.hasNext(); ) {
			((Trail) iter.next()).dispose();
		}
		
		mapInfos.clear();
		mapTrails.clear();

//		final Set setTrackLists = (Set) mapTrackLists.get( doc );
//		if( setTrackLists

		disposed = true;
	}
	
	public static void disposeAll( Session doc )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		final Set setTrackLists = (Set) mapTrackLists.remove( doc );
		if( setTrackLists != null ) {
			for( Iterator iter = setTrackLists.iterator(); iter.hasNext(); ) {
				((ClipboardTrackList) iter.next()).dispose();
			}
		}

//		try {
//			final Transferable t = clipboard.getContents( ClipboardTrackList.class );
//			if( (t != null) && (t instanceof ClipboardTrackList) ) {
//				System.err.println( "yessa." );
//				((ClipboardTrackList) t).dispose();
//				return true;
//			} else {
//				System.err.println( "nopa. t is " + (t == null ? "null" : t.getClass().getName()) );
//				return false;
//			}
//		}
//		catch( IllegalStateException e1 ) {
//			System.err.println( AbstractApplication.getApplication().getResourceString( "errClipboard" ));
//			return false;
//		}
	}

	// ---------------- ClipboardOwner interface ---------------- 

	public void lostOwnership( Clipboard clipboard, Transferable contents )
	{
// we'll always get some sun.awt.datatransfer.TransferableProxy as the contents
// objects, not ourselves ; so we simply assume that since we only "own"
// ourselves, we can safely dipose the track list here
//		if( contents == this ) {
			dispose();
//		} else {
//			System.err.println( "Yukk! Clipboard transmutation??? " + contents + " != " + this );
//		}
	}
						  
	// ---------------- Transferable interface ---------------- 

	public DataFlavor[] getTransferDataFlavors()
	{
		return disposed ? noFlavors : flavors;
	}

	public boolean isDataFlavorSupported( DataFlavor flavor )
	{
		if( disposed ) return false;
	
		for( int i = 0; i < flavors.length; i++ ) {
			if( flavor.equals( flavors[i] )) return true;
		}
		return false;
	}

	/**
	 *  Returns the transfer data which is a SampledChunkList
	 *  whose contents is equal to this object's list.
	 *  It's safe to manipulate the returned SampledChunkList.
	 *
	 *  @param  flavor  must be <code>trackListFlavor</code>
	 *  @throws UnsupportedFlavorException  if the flavor is not <code>trackListFlavor</code>
	 *  @throws IOException when data cannot be transferred
	 */
	public Object getTransferData( DataFlavor flavor )
	throws UnsupportedFlavorException, IOException
	{
		if( !disposed && flavor.equals( trackListFlavor )) {
			return this;
//		} else if( flavor.equals( AudioFileRegion.flavor ) && (size() == 1) ) {
//			final SampledChunk ts = get( 0 );
//			return new AudioFileRegion( ts.f.getFile(), ts.span );
		} else {
			throw new UnsupportedFlavorException( flavor );
		}
	}
}
