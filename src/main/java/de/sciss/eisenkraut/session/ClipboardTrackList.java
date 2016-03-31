/*
 *  ClipboardTrackList.java
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.sciss.eisenkraut.timeline.Track;

import de.sciss.io.Span;
import de.sciss.timebased.Trail;
import de.sciss.util.Disposable;

public class ClipboardTrackList
		implements Transferable, ClipboardOwner, Disposable {

	private static final boolean DEBUG	= false;

	public static final DataFlavor		trackListFlavor	= new DataFlavor( ClipboardTrackList.class, null );

	// the items in this map are only removed in disposeAll(), it's therefore
	// crucial that disposeAll() is called when a document is closed!
	private static final Map<Session, Set<ClipboardTrackList>> mapTrackLists =
			new HashMap<Session, Set<ClipboardTrackList>>();	// key = Document, value = Set (element = ClipboardTrackList)

	private final static DataFlavor[]	flavors			= {
		trackListFlavor // , AudioFileRegion.flavor
	};
	private final static DataFlavor[]	noFlavors		= new DataFlavor[ 0 ];

	private final Span					span;
	private boolean						disposed		= false;
	
	// key = Class of Trail ; value = Track.Info
	private final Map<Class<?>, Track.Info> mapInfos	= new HashMap<Class<?>, Track.Info>();
	// key = Class of Trail; value = Trail (sub)
	private final Map<Class<?>, Trail> mapTrails	= new HashMap<Class<?>, Trail>();

	public ClipboardTrackList( Session doc )
	{
		this( doc, doc.timeline.getSelectionSpan(), doc.selectedTracks.getAll() );
	}
	
	public ClipboardTrackList( Session doc, Span span, List<SessionObject> tracks )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		this.span	= span;
		
		final List<Track.Info> infos = Track.getInfos( tracks, doc.tracks.getAll() );
		Trail					subTrail;
		Set<ClipboardTrackList> setTrackLists;

		for (Track.Info ti : infos) {
			subTrail = ti.trail.getCutTrail(span, Trail.TOUCH_SPLIT, 0);
			mapTrails.put(ti.trail.getClass(), subTrail);
			mapInfos .put(ti.trail.getClass(), ti);
		}
		
		setTrackLists = mapTrackLists.get(doc);
		if (setTrackLists == null) {
			setTrackLists = new HashSet<ClipboardTrackList>();
			mapTrackLists.put(doc, setTrackLists);
		}
		setTrackLists.add(this);
		if (DEBUG) System.err.println("new : " + hashCode());
	}
	
	public Span getSpan()
	{
		return span;
	}

	public int getTrackNum(Class<? extends Trail> trailClass) {
		final Track.Info ti = mapInfos.get(trailClass);
		if (ti == null) return 0;
		return ti.numTracks;
	}

	public boolean[] getTrackMap(Class<?> trailClass) {
		final Track.Info ti = mapInfos.get(trailClass);
		if (ti == null) return new boolean[0];
		return ti.trackMap;
	}

	public Trail getSubTrail(Class<?> trailClass) {
		return (mapTrails.get(trailClass));
	}

	public void dispose() {
		if (DEBUG) System.err.println("dispose : " + hashCode());
		if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();
		if (disposed) return;

		for (Trail trail : mapTrails.values()) {
			trail.dispose();
		}
		
		mapInfos.clear();
		mapTrails.clear();

//		final Set setTrackLists = (Set) mapTrackLists.get( doc );
//		if( setTrackLists

		disposed = true;
	}

	public static void disposeAll(Session doc) {
		if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

		final Set<ClipboardTrackList> setTrackLists = mapTrackLists.remove(doc);
		if (setTrackLists != null) {
			for (ClipboardTrackList setTrackList : setTrackLists) {
				setTrackList.dispose();
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

	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		dispose();
	}
						  
	// ---------------- Transferable interface ---------------- 

	public DataFlavor[] getTransferDataFlavors()
	{
		return disposed ? noFlavors : flavors;
	}

	public boolean isDataFlavorSupported(DataFlavor flavor) {
		if (disposed) return false;

		for (DataFlavor flavor1 : flavors) {
			if (flavor.equals(flavor1)) return true;
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
	public Object getTransferData(DataFlavor flavor)
			throws UnsupportedFlavorException, IOException {
		if (!disposed && flavor.equals(trackListFlavor)) {
			return this;
//		} else if( flavor.equals( AudioFileRegion.flavor ) && (size() == 1) ) {
//			final SampledChunk ts = get( 0 );
//			return new AudioFileRegion( ts.f.getFile(), ts.span );
		} else {
			throw new UnsupportedFlavorException(flavor);
		}
	}
}
