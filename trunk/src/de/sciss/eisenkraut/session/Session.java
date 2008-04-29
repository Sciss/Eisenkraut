/*
 *  Session.java
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
 *		25-Jan-05	created
 *		15-Jul-05	removed SessionChangeListener stuff. relies more on SessionCollection.Listener
 *					for doc.tracks
 *		21-Jan-06	implements OSCRouter ; moved a lot of actions from DocumentFrame to this class
 */

package de.sciss.eisenkraut.session;

import java.awt.EventQueue;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

import de.sciss.eisenkraut.edit.BasicCompoundEdit;
import de.sciss.eisenkraut.edit.EditSetTimelineLength;
import de.sciss.eisenkraut.edit.TimelineVisualEdit;
import de.sciss.eisenkraut.edit.UndoManager;
import de.sciss.eisenkraut.gui.BlendingAction;
import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.io.BlendContext;
import de.sciss.eisenkraut.io.DecimatedSonaTrail;
import de.sciss.eisenkraut.io.DecimatedTrail;
import de.sciss.eisenkraut.io.DecimatedWaveTrail;
import de.sciss.eisenkraut.io.MarkerTrail;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.realtime.Transport;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.timeline.AudioTracks;
import de.sciss.eisenkraut.timeline.MarkerTrack;
import de.sciss.eisenkraut.timeline.Timeline;
import de.sciss.eisenkraut.timeline.Track;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.ParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.Span;
import de.sciss.timebased.Trail;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Flag;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.common.BasicDocument;
import de.sciss.common.ProcessingThread;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 *
 *	@todo		try get rid of the GUI stuff in here
 */
public class Session
extends BasicDocument
implements OSCRouter
{
	private AudioFileDescr					displayAFD;
	private AudioFileDescr[]				afds;
	
	public final Timeline					timeline;

	protected AudioTrail					at				= null;
	private DecimatedWaveTrail				dwt				= null;
	private DecimatedSonaTrail				dst				= null;

	public final MarkerTrail				markers;
	public final MarkerTrack				markerTrack;

	private static final int[]				waveDecims		= { 8, 12, 16 };
//	private static final int[]				sonaDecims		= { 0, 8 /*, 16 */ };

//	/**
//	 *  Use this <code>LockManager</code> to gain access to
//	 *  <code>receiverCollection</code>, <code>transmitterCollection</code>,
//	 *  <code>timeline</code> and a transmitter's <code>AudioTrail</code>.
//	 */
//	public final LockManager				bird			= new LockManager( 3 );
	
	/**
	 *  Bitmask for putting a lock on the <code>timeline</code>
	 */
	public  static final int				DOOR_TIME		= 0x01;
	/**
	 *  Bitmask for putting a lock on a <code>AudioTrail</code>
	 */
	public  static final int				DOOR_MTE		= 0x02;
	/**
	 *  Bitmask for putting a lock on the channel tracks
	 */
	public  static final int				DOOR_TRACKS		= 0x04;

	public static final int					DOOR_ALL		= DOOR_TIME | DOOR_TRACKS | DOOR_MTE;
	
	public final SessionCollection			tracks			= new SessionCollection();	// should be tracking audioTracks automatically
//	public final SessionCollection			audioTracks		= new SessionCollection();
	public final AudioTracks				audioTracks;
	public final SessionCollection			selectedTracks	= new SessionCollection();

	private Transport						transport		= null;
	private DocumentFrame					frame			= null;
	
	// --- actions ---

	private final ActionSave			actionSave;
	private final ActionCut				actionCut;
	protected final ActionCopy			actionCopy;
	private final ActionPaste			actionPaste;
	private final ActionDelete			actionDelete;
	private final ActionSilence			actionSilence;
	private final ActionTrim			actionTrim;

	// ---  ---
	private final UndoManager				undo			= new UndoManager( this );
	private boolean							dirty			= false;

	protected final Session					enc_this		= this;

	public static final int					EDIT_INSERT		= 0;
	public static final int					EDIT_OVERWRITE	= 1;
	public static final int					EDIT_MIX		= 2;
	
	private static final String[]			EDITMODES		= { "insert", "overwrite", "mix" };
	
	private int								editMode		= EDIT_INSERT;
	
	private static int						nodeIDAlloc		= 0;
	private final int						nodeID;

	private final OSCRouterWrapper			osc;
	
	private final BlendingAction			blending;
	
	protected ProcessingThread				pt				= null;
	
	private Session( AudioFileDescr[] afds, boolean createOSC )
	throws IOException
	{
		this( afds, null, createOSC );
	}

	private Session( AudioFileDescr afd, boolean createOSC )
	throws IOException
	{
		this( new AudioFileDescr[] { afd }, afd, createOSC );
	}

	private Session( AudioFileDescr[] afds, AudioFileDescr displayAFD, boolean createOSC )
	throws IOException
	{
		super();
	
		this.afds					= afds;
		if( displayAFD == null ) {
			this.displayAFD			= new AudioFileDescr();
			autoCreateDisplayDescr();
		} else {
			this.displayAFD			= displayAFD;
		}
		
		nodeID				= ++nodeIDAlloc;
		
		if( createOSC ) {
			osc				= new OSCRouterWrapper( null, this );
		} else {
			osc				= null;
		}
		
		timeline			= new Timeline( this );
		markerTrack			= new MarkerTrack( this );
		markers				= (MarkerTrail) markerTrack.getTrail();
		markers.copyFromAudioFile( afds[ 0 ]);	// XXX
		tracks.add( null, markerTrack );
		
		audioTracks			= new AudioTracks( this );

		actionSave			= new ActionSave();
		actionCut			= new ActionCut();
		actionCopy			= new ActionCopy();
		actionPaste			= new ActionPaste();
		actionDelete		= new ActionDelete();
		actionSilence		= new ActionSilence();
		actionTrim			= new ActionTrim();

		timeline.setRate( this, this.displayAFD.rate );
		timeline.setLength( this, this.displayAFD.length );
		timeline.setVisibleSpan( this, new Span( 0, this.displayAFD.length ));
		selectedTracks.add( this, markerTrack );
		
		blending			= new BlendingAction( timeline, null );
	}
	
	/**
	 * 	Checks if a process is currently running. This method should be called
	 * 	before launching a process using the <code>start()</code> method.
	 * 	If a process is ongoing, this method waits for a default timeout period
	 * 	for the thread to finish.
	 * 
	 *	@return	<code>true</code> if a new process can be launched, <code>false</code>
	 *			if a previous process is ongoing and a new process cannot be launched
	 *	@throws	IllegalMonitorStateException	if called from outside the event thread
	 *	@synchronization	must be called in the event thread
	 */
	public boolean checkProcess()
	{
		return checkProcess( 500 );
	}
	
	/**
	 * 	Checks if a process is currently running. This method should be called
	 * 	before launching a process using the <code>start()</code> method.
	 * 	If a process is ongoing, this method waits for a given timeout period
	 * 	for the thread to finish.
	 * 
	 * 	@param	timeout	the maximum duration in milliseconds to wait for an ongoing process
	 *	@return	<code>true</code> if a new process can be launched, <code>false</code>
	 *			if a previous process is ongoing and a new process cannot be launched
	 *	@throws	IllegalMonitorStateException	if called from outside the event thread
	 *	@synchronization	must be called in the event thread
	 */
	public boolean checkProcess( int timeout )
	{
//System.out.println( "checking..." );
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return true;
		if( timeout == 0 ) return false;

//System.out.println( "sync " + timeout );
		pt.sync( timeout );
//System.out.println( "sync done" );
		return( (pt == null) || !pt.isRunning() );
	}
	
	public void cancelProcess( boolean sync )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return;
		pt.cancel(  sync );
	}
	
	public String getProcessName()
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( pt == null ) return null;
		return pt.getName();
	}
	
	/**
	 * 	Starts a <code>ProcessingThread</code>. Only one thread
	 * 	can exist at a time. To ensure that no other thread is running,
	 * 	call <code>checkProcess()</code>.
	 * 
	 * 	@param	pt	the thread to launch
	 * 	@throws	IllegalMonitorStateException	if called from outside the event thread
	 * 	@throws	IllegalStateException			if another process is still running
	 * 	@see	#checkProcess()
	 * 	@synchronization	must be called in the event thread
	 */
	public void start( ProcessingThread process )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( this.pt != null ) throw new IllegalStateException( "Process already running" );
		
		pt = process;
		pt.addListener( new ProcessingThread.Listener() {
			public void processStarted( ProcessingThread.Event e ) { /* empty */ }
			public void processStopped( ProcessingThread.Event e )
			{
				pt = null;
			}
		});
		pt.start();
	}
	
	public BlendingAction getBlendingAction()
	{
		return blending;
	}
	
	public int getNodeID()
	{
		return nodeID;
	}
	
	public void setEditMode( int mode )
	{
		editMode = mode;
	}
	
	public int getEditMode()
	{
		return editMode;
	}
	
//	public Session( AudioFileDescr[] afds, AudioFileDescr displayAFD )
//	{
//		this.displayAFD	= displayAFD;
//		this.afds		= afds;
//		init();
//	}

//	public Session( AudioFile af )
//	{
//		this.afd	= af.getDescr();
//		init();
//	}

//	public void createOSC()
//	{
//		if( osc != null ) throw new IllegalStateException( "OSC already exists" );
//		osc	= new OSCRouterWrapper( null, this );
//	}

	public void createTransport()
	{
		if( transport != null ) throw new IllegalStateException( "Transport already exists" );
		transport = new Transport( this );
	}

	public Transport getTransport()
	{
		return transport;
	}

	public void createFrame()
	{
		if( frame != null ) throw new IllegalStateException( "Frame already exists" );
		frame = new DocumentFrame( this );
	}

	public DocumentFrame getFrame()
	{
		return frame;
	}

//	public void setFrame( DocumentFrame frame )
//	{
//		this.frame = frame;
//	}

//	public void clear( Object source )
//	throws IOException
//	{
//		timeline.clear( source );
//		selectedTracks.clear( source );
//		audioTracks.clear( source );
//		tracks.clear( source );
//		if( mte != null ) {
//			mte.clear( null );
//		}
//		markers.clear( source );
//		updateTitle();
//	}
	
	// pausing dispatcher is up to the caller!
	// should ensure that old mte was cleared!
//	public void setAudioTrail( Object source, AudioTrail mte )
	private void setAudioTrail( Object source, AudioTrail at )
	{
		this.at		= at;
//		tracks.pauseDispatcher();

//		audioTracks.clear( source );
if( !audioTracks.isEmpty() ) throw new IllegalStateException( "Cannot call repeatedly" );
		
		final List		collNewTracks	= new ArrayList();
		final int		numChannels		= at.getChannelNum();
		final double	deltaAngle		= 360.0 / numChannels;
		final double	startAngle		= numChannels < 2 ? 0.0 : -deltaAngle/2;	// reasonable for mono to octo
		AudioTrack		t;
		
		audioTracks.setTrail( at );
		
		for( int ch = 0; ch < at.getChannelNum(); ch++ ) {
//			t = new AudioTrack( at, ch );
			t = new AudioTrack( audioTracks, ch );
			t.setName( String.valueOf( ch + 1 ));
			t.getMap().putValue( source, AudioTrack.MAP_KEY_PANAZIMUTH, new Double( startAngle + ch * deltaAngle ));
			collNewTracks.add( t );
		}
		audioTracks.addAll( source, collNewTracks );
		tracks.addAll( source, collNewTracks );
		selectedTracks.addAll( source, collNewTracks );
//		tracks.resumeDispatcher();
//		dispatchChange( source );

		updateTitle();
	}

	public AudioTrail getAudioTrail()
	{
		return at;
	}
	
	public DecimatedWaveTrail getDecimatedWaveTrail()
	{
		return dwt;
	}
	
	public DecimatedSonaTrail getDecimatedSonaTrail()
	{
		return dst;
	}
	
	public void setDescr( AudioFileDescr[] afds )
	{
		this.afds = afds;
		autoCreateDisplayDescr();
		updateTitle();
	}

	private void autoCreateDisplayDescr()
	{
		if( afds.length == 0 ) {
			displayAFD.file				= null;
		} else {
			final AudioFileDescr proto	= afds[ 0 ];
			displayAFD.type				= proto.type;
			displayAFD.rate				= proto.rate;
			displayAFD.bitsPerSample	= proto.bitsPerSample;
			displayAFD.sampleFormat		= proto.sampleFormat;
			displayAFD.length			= proto.length;
			displayAFD.channels			= proto.channels;

			if( proto.file == null ) {
				displayAFD.file			= null;
			} else {
				final String			name	= proto.file.getName();
				int						left	= name.length();
				int						right	= name.length();
				String					name2;
				int						trunc;

				displayAFD.type				= proto.type;
				displayAFD.rate				= proto.rate;
				displayAFD.bitsPerSample	= proto.bitsPerSample;
				displayAFD.sampleFormat		= proto.sampleFormat;
				displayAFD.length			= proto.length;
				displayAFD.channels			= proto.channels;
				
				for( int i = 1; i < afds.length; i++ ) {
					name2				 = afds[ i ].file.getName();
					displayAFD.channels	+= afds[ i ].channels;
					for( trunc = 0; trunc < Math.min( name2.length(), left ); trunc++ ) {
						if( !(name2.charAt( trunc ) == name.charAt( trunc ))) break;
					}
					left	= trunc;
					for( trunc = 0; trunc < Math.min( name2.length(), right ); trunc++ ) {
						if( !(name2.charAt( name2.length() - trunc - 1 ) == name.charAt( name.length() - trunc - 1 ))) break;
					}
					right	= trunc;
//					System.out.println( "for '" + name2 + "' left = "+left+"; right = "+right );
				}
				
				if( left >= name.length() - right ) {
					displayAFD.file	= afds[ 0 ].file;
				} else {
					final StringBuffer strBuf = new StringBuffer();
					strBuf.append( name.substring( 0, left ));
					for( int i = 0; i < afds.length; i++ ) {
						strBuf.append( i == 0 ? '[' : ',' );
						name2 = afds[ i ].file.getName();
						strBuf.append( name2.substring( left, name2.length() - right ));
					}
					strBuf.append( ']' );
					strBuf.append( name.substring( name.length() - right ));
					displayAFD.file	= new File( afds[ 0 ].file.getParentFile(), strBuf.toString() );
				}
			}
		}
	}

	public DecimatedWaveTrail createDecimatedWaveTrail()
	throws IOException
	{
		if( dwt == null ) {
			dwt	= new DecimatedWaveTrail( at, DecimatedTrail.MODEL_FULLWAVE_PEAKRMS, waveDecims );
		}
		return dwt;
	}
	
	public DecimatedSonaTrail createDecimatedSonaTrail()
	throws IOException
	{
		if( dst == null ) {			
			dst	= new DecimatedSonaTrail( at, DecimatedTrail.MODEL_SONA /*, sonaDecims */ );
		}
		return dst;
	}

	public AudioFileDescr getDisplayDescr()
	{
		return displayAFD;
	}
	
	public String getName()
	{
		return( displayAFD.file == null ? null : displayAFD.file.getName() );
	}
	
	public AudioFileDescr[] getDescr()
	{
		return afds;
	}

	private void updateTitle()
	{
		if( frame != null ) frame.updateTitle();
	}

	public ProcessingThread procDelete( String name, Span span, int mode )
	{
		return actionDelete.initiate( name, span, mode );
	}

	public ProcessingThread procSave( String name, Span span, AudioFileDescr[] targetAFDs,
									  int[] channelMap, boolean saveMarkers, boolean asCopy )
	{
		return actionSave.initiate( name, span, targetAFDs, channelMap, saveMarkers, asCopy );
	}
	
	public MenuAction getCutAction()
	{
		return actionCut;
	}

	public MenuAction getCopyAction()
	{
		return actionCopy;
	}

	public MenuAction getPasteAction()
	{
		return actionPaste;
	}

	public MenuAction getDeleteAction()
	{
		return actionDelete;
	}
	
	public MenuAction getSilenceAction()
	{
		return actionSilence;
	}
	
	public MenuAction getTrimAction()
	{
		return actionTrim;
	}
	
	public ProcessingThread insertSilence( long pos, long numFrames )
	{
		return actionSilence.initiate( pos, numFrames );
	}

	public ProcessingThread closeDocument( boolean force, Flag wasClosed )
	{
		return frame.closeDocument( force, wasClosed );	// XXX should be in here not frame!!!
	}
	
	public ClipboardTrackList getSelectionAsTrackList()
	{
		return actionCopy.getSelectionAsTrackList();
	}
	
	public ProcessingThread pasteTrackList( ClipboardTrackList tl, long insertPos, String name, int mode )
	{
		return actionPaste.initiate( tl, insertPos, name, mode );
	}

	public static Session newEmpty( AudioFileDescr afd )
	throws IOException
	{
		return newEmpty( afd, true, true );
	}

	// NOTE: does not add the document to a handler
	public static Session newEmpty( AudioFileDescr afd, boolean createTransport, boolean createOSC )
	throws IOException
	{
		final Session			doc			= new Session( afd, createOSC );
		final AudioTrail		at			= AudioTrail.newFrom( afd );	// does _not_ throw an IOException

//		try {
			doc.setAudioTrail( null, at );
//			if( createOSC ) doc.createOSC();
			if( createTransport ) doc.createTransport();
//			if( createDecimated ) {
//				doc.createDecimatedWaveTrail();
//				doc.createDecimatedSonaTrail();
//			}
//		}
//		catch( IOException e1 ) {
//			doc.dispose();
//			throw e1;
//		}

		return doc;
	}
	
	public static Session newFrom( File path )
	throws IOException
	{
		return newFrom( path, true, true );
	}

	public static Session newFrom( File path, boolean createTransport, boolean createOSC )
	throws IOException
	{
		final AudioFile			af	= AudioFile.openAsRead( path );
		final AudioFileDescr	afd	= af.getDescr();
		Session					doc	= null;
		AudioTrail				at	= null;
		
		try {
// System.err.println( "readMarkers" );
			af.readMarkers();
			at					= AudioTrail.newFrom( af );
			doc					= new Session( afd, createOSC );
			doc.setAudioTrail( null, at );
//			if( createOSC ) doc.createOSC();
			if( createTransport ) doc.createTransport();
//			if( createDecimated ) {
//				doc.createDecimatedWaveTrail();
//				doc.createDecimatedSonaTrail();
//			}
			return doc;
		}
		catch( IOException e1 ) {
			if( doc != null ) {
				doc.dispose();
			} else if( at != null ) {
				at.dispose();
			} else {
				af.cleanUp();
			}
			throw e1;
		}
	}
	
	public static Session newFrom( File[] paths )
	throws IOException
	{
		return newFrom( paths, true, true );
	}

	public static Session newFrom( File[] paths, boolean createTransport, boolean createOSC )
	throws IOException
	{
		final AudioFile[]		afs		= new AudioFile[ paths.length ];
		final AudioFileDescr[]	afds	= new AudioFileDescr[ paths.length ]; 
		AudioTrail				at		= null;
		Session					doc		= null;
	
		try {
			for( int i = 0; i < paths.length; i++ ) {
				afs[ i ]	= AudioFile.openAsRead( paths[ i ]);
				afds[ i ]	= afs[ i ].getDescr();
				if( i > 0 ) {
					if( (afds[ i ].length != afds[ 0 ].length) || (afds[ i ].rate != afds[ 0 ].rate) ||
						(afds[ i ].bitsPerSample != afds[ 0 ].bitsPerSample) || (afds[ i ].sampleFormat != afds[ 0 ].sampleFormat) ) {
				
						throw new IOException( getResourceString( "errHeadersNotMatching" ));
					}
				}
//				System.err.println( "readMarkers" );
				afs[ i ].readMarkers();
			}
			at					= AudioTrail.newFrom( afs );
			doc					= new Session( afds, createOSC );
			doc.setAudioTrail( null, at );
//			if( createOSC ) doc.createOSC();
			if( createTransport ) doc.createTransport();
//			if( createDecimated ) {
//				doc.createDecimatedWaveTrail();
//				doc.createDecimatedSonaTrail();
//			}
			return doc;
		}
		catch( IOException e1 ) {
			if( doc != null ) {
				doc.dispose();
			} else if( at != null ) {
				at.dispose();
			} else {
				for( int i = 0; i < paths.length; i++ ) {
					if( afs[ i ] != null ) afs[ i ].cleanUp();
				}
			}
			throw e1;
		}
	}

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

//	private static BlendContext createBlendContext( Session doc, long maxLeft, long maxRight, Flag hasSelectedAudio )
//	{
//		if( !hasSelectedAudio.isSet() || ((maxLeft == 0L) && (maxRight == 0L)) ) {
//			return null;
//		} else {
//System.err.println( "maxLeft = "+maxLeft+"; maxRight = "+maxRight );
//			return BlendingAction.createBlendContext(
//				AbstractApplication.getApplication().getUserPrefs().node( BlendingAction.DEFAULT_NODE ),
//				doc.timeline.getRate(), maxLeft, maxRight );
//		}
//	}
	
	protected BlendContext createBlendContext( long maxLeft, long maxRight, Flag hasSelectedAudio )
	{
		if( !hasSelectedAudio.isSet() || ((maxLeft == 0L) && (maxRight == 0L)) ) {
			return null;
		} else {
			return blending.createBlendContext( maxLeft, maxRight );
		}
	}

	protected void discardEditsAndClipboard()
	{
		undo.discardAllEdits();
//		ClipboardTrackList.checkDispose( AbstractApplication.getApplication().getClipboard() );
		ClipboardTrackList.disposeAll( this );
	}

	// ------------- OSCRouter interface -------------
	
	public String oscGetPathComponent()
	{
		return null;	// special schoko in doc handler
	}

	public void oscRoute( RoutedOSCMessage rom )
	{
		osc.oscRoute( rom );
	}
	
	public void oscAddRouter( OSCRouter subRouter )
	{
		if( osc != null ) osc.oscAddRouter( subRouter );
	}

	public void oscRemoveRouter( OSCRouter subRouter )
	{
		if( osc != null ) osc.oscRemoveRouter( subRouter );
	}

//	public ProcessingThread procSave( String name, Span span, AudioFileDescr[] afds, boolean asCopy )
//	{
//		return actionSave.initiate( name, span, afds, asCopy );
//	}
	
	public void oscCmd_close( RoutedOSCMessage rom )
	{
		if( frame == null ) {
			OSCRoot.failed( rom.msg, getResourceString( "errWindowNotFound" ));
		}
	
		final ProcessingThread	proc;
		final boolean			force;

		try {
			if( rom.msg.getArgCount() > 1 ) {
				force = ((Number) rom.msg.getArg( 1 )).intValue() != 0;
			} else {
				force = false;
			}
			proc = closeDocument( force, new Flag( false ));
			if( proc != null ) start( proc );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
			return;
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, 1 );
		}
	}

	public void oscCmd_activate( RoutedOSCMessage rom )
	{
		if( frame == null ) {
			OSCRoot.failed( rom.msg, getResourceString( "errWindowNotFound" ));
		}
	
		frame.setVisible( true );
		frame.toFront();
//		frame.requestFocus();
	}

	public void oscCmd_cut( RoutedOSCMessage rom )
	{
		actionCut.perform();
	}
	
	public void oscCmd_copy( RoutedOSCMessage rom )
	{
		actionCopy.perform();
	}
	
	public void oscCmd_paste( RoutedOSCMessage rom )
	{
		actionPaste.perform();
	}

	public void oscCmd_delete( RoutedOSCMessage rom )
	{
		actionDelete.perform();
	}

	/**
	 *	"insertSilence", <numFrames>
	 */
	public void oscCmd_insertSilence( RoutedOSCMessage rom )
	{
		final long				pos, numFrames;
		final ProcessingThread	proc;
		int						argIdx	= 1;
	
//		if( !bird.attemptShared( DOOR_TIME, 250 )) return;
		try {
			pos			= timeline.getPosition();
			numFrames	= Math.max( 0, Math.min( timeline.getLength() - pos, ((Number) rom.msg.getArg( argIdx )).longValue() ));
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
			return;
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
			return;
		}
//		finally {
//			bird.releaseExclusive( Session.DOOR_TIME );
//		}
		
		proc = actionSilence.initiate( pos, numFrames );
		if( proc != null ) start( proc );
	}

	public void oscCmd_trim( RoutedOSCMessage rom )
	{
		actionTrim.perform();
	}

	public Object oscQuery_id()
	{
		return new Integer( getNodeID() );
	}

	public Object oscQuery_dirty()
	{
		return new Integer( isDirty() ? 1 : 0 );
	}
	
	public Object oscQuery_editMode()
	{
		return EDITMODES[ getEditMode() ];
	}
	
	public Object oscQuery_name()
	{
		return getDisplayDescr().file.getName();
	}

// ---------------- Document interface ----------------

	public de.sciss.app.Application getApplication()
	{
		return AbstractApplication.getApplication();
	}

	public de.sciss.app.UndoManager getUndoManager()
	{
		return undo;
	}

	public void dispose()
	{
//System.err.println( "Session.dispose(). hashCode = "+hashCode()+"; frame hash = "+(frame == null ? 0 : frame.hashCode()) );
		discardEditsAndClipboard();
		
		if( osc != null ) {
			osc.remove();
//			osc = null;
		}
	
		if( transport != null ) {
			transport.quit();
			transport.dispose();
			transport = null;
		}
		if( frame != null ) {
//			frame.setVisible( false );
			frame.dispose();
			frame = null;
		}
		if( at != null ) {
			at.dispose();
//			mte.clear( null );
			at = null;
		}
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public void setDirty( boolean dirty )
	{
		if( !this.dirty == dirty ) {
			this.dirty = dirty;
			updateTitle();
		}
	}
	
// ------------------ internal classes ------------------
	
	private class ActionSave
	implements ProcessingThread.Client
	{
		protected ActionSave() { /* empty */ }
		
		/**
		 *  Initiate the save process.
		 *  Transport is stopped before, if it was running.
		 *  On success, undo history is purged and
		 *  <code>setModified</code> and <code>updateTitle</code>
		 *  are called, and the file is added to
		 *  the Open-Recent menu. Note that returned
		 *	process has not yet been started, as to allow
		 *	other objects to add listeners. So it's the
		 *	job of the caller to invoke the processing thread's
		 *	<code>start</code> method.
		 *
		 *  @synchronization	this method is to be called in the event thread
		 */
		protected ProcessingThread initiate( String name, Span span, AudioFileDescr[] descrs,
											 int[] channelMap, boolean saveMarkers, boolean asCopy )
		{
			final ProcessingThread proc;
		
			getTransport().stop();
			if( !checkProcess() ) return null;
			
//			pt				= new ProcessingThread( this, getFrame(), bird, name, args, Session.DOOR_ALL );
			proc				= new ProcessingThread( this, getFrame(), name );
			proc.putClientArg( "afds", descrs );
			proc.putClientArg( "doc", enc_this );
			proc.putClientArg( "asCopy", new Boolean( asCopy ));
			proc.putClientArg( "chanMap", channelMap );
			proc.putClientArg( "markers", new Boolean( saveMarkers ));
			proc.putClientArg( "span", span == null ? new Span( 0, timeline.getLength() ) : span );
			return proc;
		}

		/**
		 *	- wenn das audio file format marker unterstuetzt, kopiere die marker in das erste file
		 *	- fuer jedes file: wenn ein file gleichen namens existiert, erzeuge zunaechst ein temporaeres file
		 *	- oeffne alle files zum schreiben (AudioFile.openAsWrite)
		 *	- schreibe alle files (at.flatten)
		 *	- liefere ein array aller geschriebener files in client arg "afs"
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final AudioFileDescr[]			clientAFDs	= (AudioFileDescr[]) context.getClientArg( "afds" );
			final int						numFiles	= clientAFDs.length;
			final Session					doc			= (Session) context.getClientArg( "doc" );
			final boolean					saveMarkers	= ((Boolean) context.getClientArg( "markers" )).booleanValue();
			final Span						span		= (Span) context.getClientArg( "span" );
			final int[]						channelMap	= (int[]) context.getClientArg( "chanMap" );
			final AudioTrail				audioTrail	= doc.getAudioTrail();
//			final File[]					tempFs		= new File[ numFiles ];
//			final boolean[]					renamed		= new boolean[ numFiles ];
			final AudioFile[]				afs			= new AudioFile[ numFiles ];
			AudioFileDescr					afdTemp;
			File							tempF;
			
			context.putClientArg( "afs", afs );

			if( saveMarkers ) {
				if( clientAFDs[ 0 ].isPropertySupported( AudioFileDescr.KEY_MARKERS )) {
					doc.markers.copyToAudioFile( clientAFDs[ 0 ], span );	// XXX
				} else if( !doc.markers.isEmpty() ) {
					System.err.println( "WARNING: markers are not saved in this file format!!!" );
				}
			} else { // WARNING: we must clear KEY_MARKERS, it might contain copied data!
				clientAFDs[ 0 ].setProperty( AudioFileDescr.KEY_MARKERS, null );
			}
			for( int i = 0; i < numFiles; i++ ) {
				if( clientAFDs[ i ].file.exists() ) {
//						tempFs[ i ]			= File.createTempFile( "eis", null, afds[ i ].file.getParentFile() );
//						tempFs[ i ].delete();
					tempF				= File.createTempFile( "eis", null, clientAFDs[ i ].file.getParentFile() );
					afdTemp				= new AudioFileDescr( clientAFDs[ i ]);
//						afdTemp.file		= tempFs[ i ];
					afdTemp.file		= tempF;
//						renamed[ i ]		= true;
					afs[ i ]			= AudioFile.openAsWrite( afdTemp );
				} else {
					afs[ i ]			= AudioFile.openAsWrite( clientAFDs[ i ]);
				}
			}
			
			audioTrail.flatten( afs, span, channelMap );
			return DONE;
		} // run

		public void processFinished( ProcessingThread context )
		{
			final AudioFileDescr[]			clientAFDs	= (AudioFileDescr[]) context.getClientArg( "afds" );
			final AudioFile[]				afs			= (AudioFile[]) context.getClientArg( "afs" );
			final Session					doc			= (Session) context.getClientArg( "doc" );
			final boolean					asCopy		= ((Boolean) context.getClientArg( "asCopy" )).booleanValue();
			File							tempF;
					
			if( context.getReturnCode() == DONE ) {	// ------------------------------- DONE -------------------------------
				if( asCopy ) {						// ............................... asCopy ...............................
					for( int i = 0; i < afs.length; i++ ) {
						try {
							afs[ i ].close();
						} catch( IOException e1 ) {
							System.err.println( "File '" + afs[ i ].getFile().getName() + "' could not be closed ("+
								e1.getClass().getName() + " : " + e1.getLocalizedMessage() + ")" );
						}
						if( !clientAFDs[ i ].file.equals( afs[ i ].getFile() )) {
							if( clientAFDs[ i ].file.delete() ) {
								if( !afs[ i ].getFile().renameTo( clientAFDs[ i ].file )) {
									System.err.println( "Newly saved file '" + afs[ i ].getFile().getName() + "' "+
										"could not be renamed!" );
								}
							} else {
								System.err.println( "Previous file '" +clientAFDs[ i ].file.getAbsolutePath() + "' "+
									"could not be deleted! Newly saved file is '" + afs[ i ].getFile().getName() + "'!" );
							}
						}
					}
				} else {							// ............................... replace ...............................
//					doc.getUndoManager().discardAllEdits();
					doc.discardEditsAndClipboard();
//					at.clear( null );
					try {
						at.closeAll();
					} catch( IOException e1 ) {
						System.err.println( "Previous audio files could not be closed ("+
							e1.getClass().getName() + " : " + e1.getLocalizedMessage() + ")" );
					}
					for( int i = 0; i < afs.length; i++ ) {
						final File f;
						try {
							afs[ i ].close();
						} catch( IOException e1 ) {
							System.err.println( "File '" + afs[ i ].getFile().getName() + "' could not be closed ("+
								e1.getClass().getName() + " : " + e1.getLocalizedMessage() + ")" );
						}
						if( clientAFDs[ i ].file.equals( afs[ i ].getFile() )) {
							f = afs[ i ].getFile();
						} else {
							if( clientAFDs[ i ].file.delete() ) {
								if( afs[ i ].getFile().renameTo( clientAFDs[ i ].file )) {
									f = clientAFDs[ i ].file;
								} else {
									System.err.println( "New current working file '" + afs[ i ].getFile().getName() + "' "+
										"could not be renamed!" );
									f = afs[ i ].getFile();
								}
							} else
tryRename:					  {
								try {
									tempF = File.createTempFile( "eis", null, clientAFDs[ i ].file.getParentFile() );
								} catch( IOException e1 ) {
									System.err.println( "Previous file '" + clientAFDs[ i ].file.getAbsolutePath() + "' could neither be " +
										"deleted nor renamed. New current working file is '" + afs[ i ].getFile().getName() + "'!" );
									f = afs[ i ].getFile();
									break tryRename;
								}
								if( clientAFDs[ i ].file.renameTo( tempF )) {
									System.err.println( "Previous file '" + clientAFDs[ i ].file.getAbsolutePath() + "' could not be " +
										"deleted. It was renamed to '" + tempF.getName() + "'!" );
									if( afs[ i ].getFile().renameTo( clientAFDs[ i ].file )) {
										f = clientAFDs[ i ].file;
									} else {
										System.err.println( "New current working file '" + afs[ i ].getFile().getName() + "' "+
											"could not be renamed!" );
										f = afs[ i ].getFile();
									}
								} else {
									System.err.println( "Previous file '" + clientAFDs[ i ].file.getAbsolutePath() + "' could neither be " +
										"deleted nor renamed. New current working file is '" + afs[ i ].getFile().getName() + "'!" );
									f = afs[ i ].getFile();
								}
							}
						}
						try {
							afs[ i ]	= AudioFile.openAsRead( f );
						} catch( IOException e1 ) {
							System.err.println( "File '" + f.getName() + "' could not be opened ("+
								e1.getClass().getName() + " : " + e1.getLocalizedMessage() + ")" );
						}
						clientAFDs[ i ]	= afs[ i ].getDescr();
					}
					try {
						at.exchange( afs );
					} catch( IOException e1 ) {
						System.err.println( "Audio files could not be exchanged ("+
							e1.getClass().getName() + " : " + e1.getLocalizedMessage() + ")" );
					}
					doc.setDescr( clientAFDs );
				}
			} else {	// ------------------------------- FAILED or CANCELLED -------------------------------
				if( afs != null ) {
					for( int i = 0; i < afs.length; i++ ) {
						if( afs[ i ] != null ) {
							afs[ i ].cleanUp();
							if( !afs[ i ].getFile().delete() ) {
								System.err.println( "The file '" + afs[ i ].getFile().getAbsolutePath() + "' "+
													"(created during saving) could not be deleted!" );
							}
						}
					}
				}
			}
		}
		
		public void processCancel( ProcessingThread context ) { /* ignored */ }
	}
	
	private class ActionCut
	extends MenuAction
	{
		protected ActionCut() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			final ProcessingThread proc; // = null;
			
			if( actionCopy.perform() ) {
//				if( !bird.attemptShared( Session.DOOR_TIME | Session.DOOR_MTE )) return;
//				try {
					proc = procDelete( getValue( NAME ).toString(), timeline.getSelectionSpan(), getEditMode() );
//				}
//				finally {
//					bird.releaseShared( Session.DOOR_TIME | Session.DOOR_MTE );
//				}
				if( proc != null ) start( proc );
			}
		}
	}

	private class ActionCopy
	extends MenuAction
	{
		protected ActionCopy() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}

		protected ClipboardTrackList getSelectionAsTrackList()
		{
			final Span span;
			
//			if( !bird.attemptShared( Session.DOOR_TIME | Session.DOOR_TRACKS, 250 )) return null;
//			try {
				span = timeline.getSelectionSpan();
				if( span.isEmpty() ) return null;

				return new ClipboardTrackList( enc_this );
//			}
//			finally {
//				bird.releaseShared( Session.DOOR_TIME | Session.DOOR_TRACKS );
//			}
		}

		protected boolean perform()
		{
			boolean						success	= false;
			final ClipboardTrackList	tl		= getSelectionAsTrackList();

			if( tl == null ) return success;

			try {
				AbstractApplication.getApplication().getClipboard().setContents( tl, tl );
				success = true;
			}
			catch( IllegalStateException e1 ) {
				System.err.println( getResourceString( "errClipboard" ));
			}

			return success;
		}
	}
	
	private class ActionPaste
	extends MenuAction
	implements ProcessingThread.Client
	{
		protected ActionPaste() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			perform( getValue( NAME ).toString(), getEditMode() );
		}
		
		private void perform( String name, int mode )
		{
			final Transferable			t;
			final ClipboardTrackList	tl;

			try {
				t = AbstractApplication.getApplication().getClipboard().getContents( this );
				if( t == null ) return;
				
				if( !t.isDataFlavorSupported( ClipboardTrackList.trackListFlavor )) return;
				tl = (ClipboardTrackList) t.getTransferData( ClipboardTrackList.trackListFlavor );
			}
			catch( IOException e11 ) {
				System.err.println( e11.getLocalizedMessage() );
				return;
			}
			catch( UnsupportedFlavorException e11 ) {
				System.err.println( e11.getLocalizedMessage() );
				return;
			}
			catch( IllegalStateException e11 ) {
				System.err.println( getResourceString( "errClipboard" ));
				return;
			}
			
			if( !checkProcess() ) return;
			final ProcessingThread proc = initiate( tl, timeline.getPosition(), name, mode );	// XXX sync
			if( proc != null ) {
				start( proc );
			}
		}

		protected ProcessingThread initiate( ClipboardTrackList tl, long insertPos, String name, int mode )
		{
			if( !checkProcess() ) return null;
			
			if( (insertPos < 0) || (insertPos > timeline.getLength()) ) throw new IllegalArgumentException( String.valueOf( insertPos ));
			
			final ProcessingThread		proc;
			final Span					oldSelSpan, insertSpan, copySpan, cutTimelineSpan;
			final AbstractCompoundEdit	edit;
			final Flag					hasSelectedAudio;
			final List					tis;
			final boolean				expTimeline, cutTimeline;
			final long					docLength, pasteLength, preMaxLen, postMaxLen;
			final BlendContext			bcPre, bcPost;
			
			hasSelectedAudio	= new Flag( false );
			tis					= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
			if( !AudioTracks.checkSyncedAudio( tis, mode == EDIT_INSERT, null, hasSelectedAudio )) return null;

			expTimeline			= (mode == EDIT_INSERT) && hasSelectedAudio.isSet();
			docLength			= timeline.getLength();
			pasteLength			= expTimeline ? tl.getSpan().getLength() :
				Math.min( tl.getSpan().getLength(), docLength - insertPos );
			if( pasteLength == 0 ) return null;
			
			if( mode == EDIT_INSERT ) {
				/*
				 *	before paste:
				 * 
				 *	 maxRight / post   maxLeft / pre
				 *
				 *	|                 |              |
				 *	|                 |              |
				 *	|                 |              |
				 *	|        A        |     B        |
				 *	+-----------------+--------------+
				 *	                  |
				 *	               insertPos
				 *
				 *	after paste:
				 * 
				 *	|                 | B #$$$$# A |              |
				 *	|                 |  ##$$$$##  |              |
				 *	|                 | ###$$$$### |              |
				 *	|        A        |####$$$$####|      B       |
				 *	+-----------------+------------+--------------+
				 *	                  |
				 *	               insertPos
				 */
				// note: now the discrepancy between postMaxLen and preMaxLen is
				// limited to 100%, so pasting at the very end or beginning of
				// a doc will not produce a single sided xfade any more
				// (answering bug 1922862)
				if( insertPos < (docLength - insertPos) ) {
					postMaxLen	= Math.min( insertPos, pasteLength >> 1 );
//					preMaxLen	= Math.min( docLength - insertPos, pasteLength - postMaxLen );
					preMaxLen	= Math.min( postMaxLen << 1, Math.min( docLength - insertPos, pasteLength - postMaxLen ));
//System.out.println( "A" );
				} else {
					preMaxLen	= Math.min( docLength - insertPos, pasteLength >> 1 );
					postMaxLen	= Math.min( preMaxLen << 1, Math.min( insertPos, pasteLength - preMaxLen ));
//System.out.println( "B" );
				}
			} else {
				preMaxLen	= pasteLength >> 1;	// note: pasteLength already clipped to be <= docLength - insertPos !
				postMaxLen	= pasteLength - preMaxLen;
//System.out.println( "C" );
			}
			bcPre			= createBlendContext( preMaxLen, 0, hasSelectedAudio );
			bcPost			= createBlendContext( postMaxLen, 0, hasSelectedAudio );
//System.out.println( "D ; preMaxLen = " + preMaxLen + "; postMaxLen = " + postMaxLen + "; bcPre.getLeftLen() = " + (bcPre == null ? null : String.valueOf( bcPre.getLeftLen())) + "; bcPre.getRightLen() = " + (bcPre == null ? null : String.valueOf( bcPre.getRightLen() )) + "; bcPost.getLeftLen() = " + (bcPost == null ? null : String.valueOf( bcPost.getLeftLen() )) + "; bcPost.getRightLen() = " + (bcPost == null ? null : String.valueOf( bcPost.getRightLen() )));

//			if( bcPre != null )  System.out.println( "bcPre  : " + bcPre.getLen() + ", " + bcPre.getLeftLen() + ", "+ bcPre.getRightLen() );
//			if( bcPost != null ) System.out.println( "bcPost : " + bcPost.getLen() + ", " + bcPost.getLeftLen() + ", "+ bcPost.getRightLen() );
			
			insertSpan			= new Span( insertPos, insertPos + pasteLength );
			copySpan			= new Span( tl.getSpan().start, tl.getSpan().start + pasteLength );
			cutTimeline			= (mode == EDIT_INSERT) && !hasSelectedAudio.isSet();
			cutTimelineSpan		= cutTimeline ? new Span( docLength, docLength + pasteLength ) : null;
			
			edit			= new BasicCompoundEdit( name );
			oldSelSpan		= timeline.getSelectionSpan();
			if( !oldSelSpan.isEmpty() ) { // deselect
				edit.addPerform( TimelineVisualEdit.select( this, enc_this, new Span() ));
			}

			proc	= new ProcessingThread( this, getFrame(), name );
			proc.putClientArg( "tl", tl );
			proc.putClientArg( "pos", new Long( insertPos ));
			proc.putClientArg( "mode", new Integer( mode ));
			proc.putClientArg( "tis", tis );
			proc.putClientArg( "pasteLen", new Long( pasteLength ));
			proc.putClientArg( "exp", new Boolean( expTimeline ));
			proc.putClientArg( "bcPre", bcPre );
			proc.putClientArg( "bcPost", bcPost );
			proc.putClientArg( "insertSpan", insertSpan );
			proc.putClientArg( "copySpan", copySpan );
			proc.putClientArg( "cut", new Boolean( cutTimeline ));
			proc.putClientArg( "cutSpan", cutTimelineSpan );
			proc.putClientArg( "edit", edit );

			return proc;
		}
		
		// --------- ProcessingThread.Client interface ---------

		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final ClipboardTrackList		tl					= (ClipboardTrackList) context.getClientArg( "tl" );
			final long						insertPos			= ((Long) context.getClientArg( "pos" )).longValue();
			final int						mode				= ((Integer) context.getClientArg( "mode" )).intValue();
			final List						tis					= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit		edit				= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final BlendContext				bcPre				= (BlendContext) context.getClientArg( "bcPre" );
			final BlendContext				bcPost				= (BlendContext) context.getClientArg( "bcPost" );
			final Span						insertSpan			= (Span) context.getClientArg( "insertSpan" );
			final Span						copySpan			= (Span) context.getClientArg( "copySpan" );
			final boolean					cutTimeline			= ((Boolean) context.getClientArg( "cut" )).booleanValue();
			final Span						cutTimelineSpan		= (Span) context.getClientArg( "cutSpan" );
			final long						delta				= insertPos - tl.getSpan().start;
			Track.Info						ti;
			Trail							srcTrail;
			AudioTrail						audioTrail;
			boolean[]						trackMap;
			boolean							isAudio, pasteAudio;

			for( int i = 0; i < tis.size(); i++ ) {
				ti		= (Track.Info) tis.get( i );
				if( ti.selected ) {	// ----------------- selected tracks -----------------
					try {
						ti.trail.editBegin( edit );
						isAudio	= ti.trail instanceof AudioTrail;
						srcTrail = tl.getSubTrail( ti.trail.getClass() );
					
						if( isAudio ) {
							pasteAudio = (srcTrail != null) && (((AudioTrail) srcTrail).getChannelNum() > 0);
						} else {
							pasteAudio = false;
						}
						
						if( mode == EDIT_INSERT ) {
							ti.trail.editInsert( this, insertSpan, edit );
							if( cutTimeline ) ti.trail.editRemove( this, cutTimelineSpan, edit );
//							} else if( (mode == EDIT_OVERWRITE) && (pasteAudio || !isAudio) ) { // Audio needs to be cleared even in Mix mode!
						} else if( pasteAudio || !isAudio ) { // Audio needs to be cleared even in Mix mode!
							ti.trail.editClear( this, insertSpan, edit );
						}
						
						if( pasteAudio ) {
							audioTrail			= (AudioTrail) ti.trail;
							trackMap	= tl.getTrackMap( ti.trail.getClass() );
							
//System.err.println( "clipboard tm : " );
//for( int x = 0; x < trackMap.length; x++ ) { System.err.println( "  " + trackMap[ x ]); }
							int[] trackMap2 = new int[ audioTrail.getChannelNum() ];
							for( int j = 0, k = 0; j < trackMap2.length; j++ ) {
								if( ti.trackMap[ j ]) {	// target track selected
									for( ; (k < trackMap.length) && !trackMap[ k ] ; k++ ) ;
									if( k < trackMap.length ) {	// source track exiting
										trackMap2[ j ] = k++;
									} else if( tl.getTrackNum( ti.trail.getClass() ) > 0 ) {		// ran out of source tracks, fold over (simple mono -> stereo par exemple)
										for( k = 0; !trackMap[ k ] ; k++ ) ;
										trackMap2[ j ] = k++;
									} else {
										trackMap2[ j ] = -1;		// there aren't any clipboard tracks ....
									}
								} else {							// target track not selected
									trackMap2[ j ] = -1;
								}
							}
							if( !audioTrail.copyRangeFrom( (AudioTrail) srcTrail, copySpan, insertPos, mode, this, edit, trackMap2, bcPre, bcPost )) return CANCELLED;

						} else if( (ti.numTracks == 1) && (tl.getTrackNum( ti.trail.getClass() ) == 1) ) {
							ti.trail.editAddAll( this, srcTrail.getCuttedRange(
								copySpan, true, srcTrail.getDefaultTouchMode(), delta ), edit );
						}
					}
					finally {
						ti.trail.editEnd( edit );
					}
				}
			}

			return DONE;
		}

		public void processFinished( ProcessingThread context )
		{
			final ProcessingThread.Client	doneAction	= (ProcessingThread.Client) context.getClientArg( "doneAction" );
			final AbstractCompoundEdit		edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final boolean					expTimeline	= ((Boolean) context.getClientArg( "exp" )).booleanValue();
			final long						pasteLength	= ((Long) context.getClientArg( "pasteLen" )).longValue();
			final Span						insertSpan	= (Span) context.getClientArg( "insertSpan" );
			
			if( (context.getReturnCode() == DONE) ) {
				if( expTimeline && (pasteLength != 0) ) {	// adjust timeline
					edit.addPerform( new EditSetTimelineLength( this, enc_this, timeline.getLength() + pasteLength ));
					if( timeline.getVisibleSpan().isEmpty() ) {
						edit.addPerform( TimelineVisualEdit.scroll( this, enc_this, insertSpan ));
					}
				}
				if( !insertSpan.isEmpty() ) {
					edit.addPerform( TimelineVisualEdit.select( this, enc_this, insertSpan ));
					edit.addPerform( TimelineVisualEdit.position( this, enc_this, insertSpan.stop ));
				}

				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}

//			if( doneAction != null ) doneAction.processFinished( context, doc );
			if( doneAction != null ) doneAction.processFinished( context );
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignored */ }
	} // class actionPasteClass

	/**
	 *	@todo	when a cutted region spans entire view,
	 *			selecting undo results in empty visible span
	 */
	private class ActionDelete
	extends MenuAction
	implements ProcessingThread.Client
	{
		protected ActionDelete() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{		
			final Span				span	= timeline.getSelectionSpan(); // XXX sync
			if( span.isEmpty() ) return;
			
			final ProcessingThread	proc		= initiate( getValue( NAME ).toString(), span, getEditMode() );
			if( proc != null ) start( proc );
		}
		
		// XXX sync
		protected ProcessingThread initiate( String name, Span span, int mode )
		{
			if( !checkProcess() ) return null;

			final BlendContext			bc;
			final long					cutLength, docLength, newDocLength, maxLen;
			final Flag					hasSelectedAudio;
			final List					tis;
			final AbstractCompoundEdit	edit;
			final boolean 				cutTimeline;
			final Span					cutTimelineSpan, selSpan;
			Span						visiSpan;

			hasSelectedAudio	= new Flag( false );
			tis					= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
			if( !AudioTracks.checkSyncedAudio( tis, mode == EDIT_INSERT, null, hasSelectedAudio )) return null;
			
			docLength			= timeline.getLength();
			cutLength			= span.getLength();
			if( mode == EDIT_INSERT ) {
				/*
				 *	before delete:
				 * 
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$$$$$#######|............|
				 *	|,,,,,,,,A,,,,,,,,|$$B1$$$###B2##|......C.....|
				 *	+-----------------+--------------+------------+
				 *	                  |     span     |
				 *
				 *	after delete:
				 *	              left right
				 *	|,,,,,,,,,,,,,    |            |
				 *	|,,,,,,,,,,,,,,,  |            |
				 *	|,,,,,,,,,,,,,,,,,|            |
				 *	|,,,,,,,,,,,,,,,,,|$$          |
				 *	|,,,,,,,,,,,,,,,,,|$$$$        |
				 *	|,,,,,,,,A,,,,,,,,|$B2$$$      |
				 *	+-----------------+------------+
				 *	                  |
				 *			plus
				 *	|                 |    ........|
				 *	|                 |  ..........|
				 *	|                 |............|
				 *	|               ##|............|
				 *	|             ####|............|
				 *	|           ###B2#|......C.....|
				 *	+-----------------+------------+
				 *	                  |
				 *	              span.start
				 */
				maxLen				= Math.min( cutLength, Math.min( span.start, docLength - span.stop ) << 1 );
				bc					= createBlendContext( maxLen >> 1, (maxLen + 1) >> 1, hasSelectedAudio );
			} else {
				/*
				 *	after delete:
				 *                     blend-   blend-
				 *                     Len      Len
				 *	|,,,,,,,,,,,,,,,,,|$            #|............|
				 *	|,,,,,,,,,,,,,,,,,|$$          ##|............|
				 *	|,,,,,,,,,,,,,,,,,|$$$        ###|............|
				 *	|,,,,,,,,A,,,,,,,,|$B1$      #B2#|......C.....|
				 *	+-----------------+--------------+------------+
				 *	                  |     span     |
				 */
				maxLen				= cutLength >> 1;
				bc					= createBlendContext( maxLen, 0, hasSelectedAudio );
			}
//			bc					= createBlendContext( Math.min( cutLength, span.start ), Math.min( cutLength, docLength - span.stop ), hasSelectedAudio );
			edit				= new BasicCompoundEdit( name );

//			if( bc != null )  System.out.println( "bc  : " + bc.getLen() + ", " + bc.getLeftLen() + ", "+ bc.getRightLen() );
			
			cutTimeline			= (mode == EDIT_INSERT) && hasSelectedAudio.isSet();
			newDocLength		= cutTimeline ? docLength - cutLength : docLength;
			cutTimelineSpan		= cutTimeline ? new Span( newDocLength, docLength ) : null;
			selSpan				= timeline.getSelectionSpan();
			
			if( (mode == EDIT_INSERT) && !selSpan.isEmpty() ) {
				edit.addPerform( TimelineVisualEdit.position( this, enc_this, span.start ));
				edit.addPerform( TimelineVisualEdit.select( this, enc_this, new Span() ));
			}
			if( cutTimeline ) {
				visiSpan = timeline.getVisibleSpan();
				if( visiSpan.stop > span.start ) {
					if( visiSpan.stop > newDocLength ) {
						visiSpan = new Span( Math.max( 0, newDocLength - visiSpan.getLength() ), newDocLength );
						TimelineVisualEdit tve = TimelineVisualEdit.scroll( this, enc_this, visiSpan );
						edit.addPerform( tve );
					} // else visiSpan untouched
				}
				edit.addPerform( new EditSetTimelineLength( this, enc_this, newDocLength ));
			}

			final ProcessingThread proc = new ProcessingThread( this, getFrame(), name );
			proc.putClientArg( "span", span );
			proc.putClientArg( "mode", new Integer( mode ));
			proc.putClientArg( "tis", tis );
			proc.putClientArg( "edit", edit );
			proc.putClientArg( "bc", bc );
			proc.putClientArg( "cut", new Boolean( cutTimeline ));
			proc.putClientArg( "cutSpan", cutTimelineSpan );
			return proc;
		}

		// --------- ProcessingThread.Client interface ---------
		
		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final Span						span				= (Span) context.getClientArg( "span" );
			final int						mode				= ((Integer) context.getClientArg( "mode" )).intValue();
			final List						tis					= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit		edit				= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final BlendContext				bc					= (BlendContext) context.getClientArg( "bc" );
			final long						left				= bc == null ? 0L : bc.getLeftLen();
			final long						right				= bc == null ? 0L : bc.getRightLen();
			final boolean					cutTimeline			= ((Boolean) context.getClientArg( "cut" )).booleanValue();
			final Span						cutTimelineSpan		= (Span) context.getClientArg( "cutSpan" );
			AudioTrail						audioTrail;
			Track.Info						ti;
			boolean							isAudio;

			for( int i = 0; i < tis.size(); i++ ) {
				ti		= (Track.Info) tis.get( i );
				try {
					ti.trail.editBegin( edit );
					isAudio = ti.trail instanceof AudioTrail;
					if( ti.selected ) {
						if( mode == EDIT_INSERT ) {
							if( isAudio ) {
								if( bc == null ) {
									ti.trail.editRemove( this, span, edit );
								} else {
									ti.trail.editRemove( this, new Span( span.start - left, span.stop + right ), edit );
									ti.trail.editInsert( this, new Span( span.start - left, span.start + right ), edit );
								}
								audioTrail = (AudioTrail) ti.trail;
								audioTrail.clearRange( span, EDIT_INSERT, this, edit, ti.trackMap, bc );
							} else {
								ti.trail.editRemove( this, span, edit );
							}
						} else {
							ti.trail.editClear( this, span, edit );
							if( isAudio ) {
								audioTrail = (AudioTrail) ti.trail;
								audioTrail.clearRange( span, EDIT_OVERWRITE, this, edit, ti.trackMap, bc );
							}
						}
					} else if( cutTimeline ) {
						ti.trail.editRemove( this, cutTimelineSpan, edit );
					}
				}
				finally {
					ti.trail.editEnd( edit );
				}
			}
			return DONE;
		} // run

		public void processFinished( ProcessingThread context )
		{
			final AbstractCompoundEdit edit = (AbstractCompoundEdit) context.getClientArg( "edit" );

			if( context.getReturnCode() == DONE ) {
				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignore */ }
	} // class actionDeleteClass

	private class ActionTrim
	extends MenuAction
	{
		protected ActionTrim() { /* empty */ }

		// performs inplace (no runnable processing) coz it's always fast
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		protected void perform()
		{
			final Span						selSpan, deleteBefore, deleteAfter;
			final BasicCompoundEdit		edit;
			final List						tis;
			Track.Info						ti;
			boolean							success	= false;

			edit			= new BasicCompoundEdit( getValue( NAME ).toString() );
			
			try {
				selSpan			= timeline.getSelectionSpan();
//				if( selSpan.isEmpty() ) return;
				tis				= Track.getInfos( selectedTracks.getAll(), tracks.getAll() );
				deleteBefore	= new Span( 0, selSpan.start );
				deleteAfter		= new Span( selSpan.stop, timeline.getLength() );

				// deselect
				edit.addPerform( TimelineVisualEdit.select( this, enc_this, new Span() ));
				edit.addPerform( TimelineVisualEdit.position( this, enc_this, 0 ));

				if( !deleteAfter.isEmpty() || !deleteBefore.isEmpty() ) {
					for( int i = 0; i < tis.size(); i++ ) {
						ti = (Track.Info) tis.get( i );
						ti.trail.editBegin( edit );
						try {
							if( !deleteAfter.isEmpty() ) ti.trail.editRemove( this, deleteAfter, edit );
							if(	!deleteBefore.isEmpty() ) ti.trail.editRemove( this, deleteBefore, edit );
						}
						finally {
							ti.trail.editEnd( edit );
						}
					}
				}

				edit.addPerform( new EditSetTimelineLength( this, enc_this, selSpan.getLength() ));
				edit.addPerform( TimelineVisualEdit.select( this, enc_this, selSpan.shift( -selSpan.start )));

				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
				success = true;
			}
			finally {
				if( !success ) edit.cancel();
			}
		}
	} // class actionTrimClass

	/**
	 *	@todo	when edit mode != EDIT_INSERT, audio tracks are cleared which should be bypassed and vice versa
	 *	@todo	waveform display not automatically updated when edit mode != EDIT_INSERT
	 */
	private class ActionSilence
	extends MenuAction
	implements ProcessingThread.Client
	{
		private Param		value = null;
		private ParamSpace	space = null;
	
		protected ActionSilence() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		private void perform()
		{
			final SpringPanel			msgPane;
			final int					result;
			final ParamField			ggDuration;
			final Param					durationSmps;
			final DefaultUnitTranslator	timeTrans;
			
			msgPane			= new SpringPanel( 4, 2, 4, 2 );
			timeTrans		= new DefaultUnitTranslator();
			ggDuration		= new ParamField( timeTrans );
			ggDuration.addSpace( ParamSpace.spcTimeHHMMSS );
			ggDuration.addSpace( ParamSpace.spcTimeSmps );
			ggDuration.addSpace( ParamSpace.spcTimeMillis );
			ggDuration.addSpace( ParamSpace.spcTimePercentF );
			msgPane.gridAdd( ggDuration, 0, 0 );
			msgPane.makeCompactGrid();
			GUIUtil.setInitialDialogFocus( ggDuration );

			timeTrans.setLengthAndRate( timeline.getLength(), timeline.getRate() );

			if( value == null ) {
				ggDuration.setValue( new Param( 1.0, ParamSpace.TIME | ParamSpace.SECS ));
			} else {
				ggDuration.setSpace( space );
				ggDuration.setValue( value );
			}

			result = JOptionPane.showOptionDialog( getFrame() == null ? null : getFrame().getWindow(), msgPane, getValue( NAME ).toString(),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );

			if( result == JOptionPane.OK_OPTION ) {
				value			= ggDuration.getValue();
				space			= ggDuration.getSpace();
				durationSmps	= timeTrans.translate( value, ParamSpace.spcTimeSmps );
				if( durationSmps.val > 0.0 ) {
					final ProcessingThread proc;
					
					proc = initiate( timeline.getPosition(), (long) durationSmps.val );
					if( proc != null ) start( proc );
				}
			}
		}

		public ProcessingThread initiate( long pos, long numFrames )
		{
			if( !checkProcess() || (numFrames == 0) ) return null;
			
			if( numFrames < 0 ) throw new IllegalArgumentException( String.valueOf( numFrames ));
			if( (pos < 0) || (pos > timeline.getLength()) ) throw new IllegalArgumentException( String.valueOf( pos ));

			final ProcessingThread 		proc;
			final AbstractCompoundEdit	edit;
			final Span					oldSelSpan, insertSpan;

			proc = new ProcessingThread( this, getFrame(), getValue( NAME ).toString() );

			edit		= new BasicCompoundEdit( proc.getName() );
			oldSelSpan	= timeline.getSelectionSpan();
			insertSpan	= new Span( pos, pos + numFrames );

			if( !oldSelSpan.isEmpty() ) { // deselect
				edit.addPerform( TimelineVisualEdit.select( this, enc_this, new Span() ));
			}

			proc.putClientArg( "tis", Track.getInfos( selectedTracks.getAll(), tracks.getAll() ));
			proc.putClientArg( "edit", edit );
			proc.putClientArg( "span", insertSpan );
			return proc;
		}

		/**
		 *  This method is called by ProcessingThread
		 */
		public int processRun( ProcessingThread context )
		throws IOException
		{
			final List					tis			= (List) context.getClientArg( "tis" );
			final AbstractCompoundEdit	edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final Span					insertSpan	= (Span) context.getClientArg( "span" );
			Track.Info					ti;
			AudioTrail					audioTrail;

			for( int i = 0; i < tis.size(); i++ ) {
				ti = (Track.Info) tis.get( i );
				ti.trail.editBegin( edit );
				try {
					ti.trail.editInsert( this, insertSpan, edit );
					if( ti.trail instanceof AudioTrail ) {
						audioTrail			= (AudioTrail) ti.trail;							
						audioTrail.editAdd( this, audioTrail.allocSilent( insertSpan ), edit );
					}
				}
				finally {
					ti.trail.editEnd( edit );
				}
			}
			return DONE;
		}

		public void processFinished( ProcessingThread context )
		{
			final AbstractCompoundEdit	edit		= (AbstractCompoundEdit) context.getClientArg( "edit" );
			final Span					insertSpan	= (Span) context.getClientArg( "span" );

			if( context.getReturnCode() == DONE ) {
				if( !insertSpan.isEmpty() ) {	// adjust timeline
					edit.addPerform( new EditSetTimelineLength( this, enc_this, timeline.getLength() + insertSpan.getLength() ));
					if( timeline.getVisibleSpan().isEmpty() ) {
						edit.addPerform( TimelineVisualEdit.scroll( this, enc_this, insertSpan ));
					}
				}
				if( !insertSpan.isEmpty() ) {
					edit.addPerform( TimelineVisualEdit.select( this, enc_this, insertSpan ));
					edit.addPerform( TimelineVisualEdit.position( this, enc_this, insertSpan.stop ));
				}
				edit.perform();
				edit.end();
				getUndoManager().addEdit( edit );
			} else {
				edit.cancel();
			}
		}

		// mte will check pt.shouldCancel() itself
		public void processCancel( ProcessingThread context ) { /* ignore */ }
	} // class actionSilenceClass
}
