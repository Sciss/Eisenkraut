/*
 *  Transport.java
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
 *		25-Jan-05	created from de.sciss.meloncillo.realtime.Transport
 *		15-Jul-05	timeline insertion follows playback
 *		18-Jul-05	fixes a wrong return statement in the run() method
 *		22-Jul-05	doesn't extend Thread any more (allows re-running if thread dies)
 *		02-Aug-05	conforms to new document handler
 *		08-Sep-05	modified stopAndWait as to return directly if transport wasn't running
 *					; uses floating point rates
 *		21-Jan-06	added OSC support
 *		25-Feb-06	moved to double precision
 *		20-Sep-06	radically stripped down, removed realtime consumer stuff, everything in event thread now
 */

package de.sciss.eisenkraut.realtime;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.sciss.app.AbstractApplication;
import de.sciss.io.Span;
import de.sciss.util.Disposable;

import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;
import de.sciss.eisenkraut.util.PrefsUtil;

/**
 *	The realtime "motor" or "clock". The transport
 *	deals with realtime playback of the timeline.
 *	It provides means for registering and unregistering
 *	realtime consumers and communicates with a
 *	RealtimeProducer which is responsible for the
 *	actual data production. Transort clocking is
 *	performed within an extra thread from within
 *	the consumer's methods are called and registered
 *	transport listeners are informed about actions.
 * 
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 02-May-08
 *
 *	@todo	the methods for adding and removing consumers should
 *			be moved to the realtime host interface?
 *
 *	@todo	changing sample rate while playing doesn't have an effect
 *
 *	@synchronization	all methods must be executed in event thread!
 */
public class Transport
implements TimelineListener, OSCRouter, Disposable
{
	protected final Session				doc;

    private	boolean						looping			= false;
	private boolean						loopInPlay		= false;
	private long						loopStart, loopStop;
	private double						rate;
	private double						frameFactor;
	private long						lastUpdate;
	
	// high level listeners
	private final List					collListeners  = new ArrayList();

	// realtime control
	private long						startFrame;
	private long						stopFrame;
	private long						currentFrame;
	private long						startTime;
	
	private double						rateScale	= 1.0;
	
	private boolean						running		= false;
				
	// --- actions ---
	
	private static final String			OSC_TRANSPORT = "transport";
	private final OSCRouter				osc;

	protected final Transport			enc_this		= this;

	// sync : call in event thread!
	/**
	 *	Creates a new transport. The thread will
	 *	be started and set to pause to await
	 *	transport commands.
	 *
	 *	@param	doc		Session document
	 */
    public Transport( Session doc )
    {
        this.doc    = doc;
        
		doc.timeline.addTimelineListener( this );

		osc				= new OSCRouterWrapper( doc, this );
		rate			= doc.timeline.getRate();
		frameFactor		= rateScale * rate / 1000;
    }
	
	public void dispose()
	{
		collListeners.clear();
		running = false;
		doc.timeline.removeTimelineListener( this );
	}
	
//	public AbstractAction getPlayAction()
//	{
//		return actionPlay;
//	}

//	public AbstractAction getStopAction()
//	{
//		return actionStop;
//	}

	public Session getDocument()
	{
		return doc;
	}
	
	/**
	 *	Registers a new transport listener
	 *
	 *	@param	listener	the listener to register for information
	 *						about transport actions such as play or stop
	 */
	public void addTransportListener( TransportListener listener )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
	
		collListeners.add( listener );
		if( running ) {
			listener.transportPlay( this, updateCurrentFrame(), rateScale );
		}
	}

	/**
	 *	Unregisters a transport listener
	 *
	 *	@param	listener	the listener to remove from the event dispatching
	 */
	public void removeTransportListener( TransportListener listener )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		collListeners.remove( listener );
	}

	private void dispatchStop( long pos )
	{
		for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
			((TransportListener) iter.next()).transportStop( this, pos );
		}
		if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
			PrefsUtil.KEY_INSERTIONFOLLOWSPLAY, false )) {

			doc.timeline.editPosition( this, pos );
		} else {
			doc.timeline.setPosition( this, doc.timeline.getPosition() );
		}
	}

	private void dispatchPosition( long pos )
	{
		for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
			((TransportListener) iter.next()).transportPosition( this, pos, rateScale );
		}
	}

	private void dispatchPlay( long pos )
	{
		for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
			((TransportListener) iter.next()).transportPlay( this, pos, rateScale );
		}
	}

	private void dispatchReadjust( long pos )
	{
		for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
			((TransportListener) iter.next()).transportReadjust( this, pos, rateScale );
		}
	}

	private void dispatchQuit()
	{
		for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
			try {
				((TransportListener) iter.next()).transportQuit( this );
			}
			catch( Exception e1 ) {
				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
			}
		}
	}
	
//	/**
//	 *	The transport core is
//	 *	executed within the thread's run method
//	 */
//    public void run()
//    {
//		// all initial values are just here to please the compiler
//		// who doesn't know commandLp is exited only after at least
//		// one CMD_PLAY (see assertion in CMD_CONFIG_RESUME)
//        long			startTime = 0, sysTime;
//        long			frameCount = 0, oldFrameCount = 0;
//        double			currentRate, targetRate = 1.0;
//		int				i;
////		UndoableEdit	edit;
//		RealtimeConsumerRequest	r;
//
//		do {
//			synchronized( this ) {
//commandLp:		do {
//					switch( rt_command ) {
//					case CMD_CONFIG_PAUSE:
//						notifyAll();
//						break;
//						
//					case CMD_CONFIG_RESUME:
//						assert startTime > 0 : startTime;
//						notifyAll();
//						break commandLp;
//						
//					case CMD_STOP:
//						dispatchStop( currentFrame );
//						// translate into a valid time offset
//						if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 400 )) break;
//						try {
//							currentFrame	= Math.max( 0, Math.min( doc.timeline.getLength(), currentFrame ));
//							if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
//								PrefsUtil.KEY_INSERTIONFOLLOWSPLAY, false )) {
//
////								doc.getUndoManager().addEdit( TimelineVisualEdit.position( this, doc, currentFrame ));
//								doc.timeline.editPosition( this, currentFrame );
////								doc.timeline.setPosition( this, currentFrame );
//							} else {
//								// this is for notifying objects for visual update
//								doc.timeline.setPosition( this, doc.timeline.getPosition() );
//							}
////							edit	= new EditSetTimelinePosition( this, doc, currentFrame );
////							doc.getUndoManager().addEdit( edit );
//						}
//						finally {
//							doc.bird.releaseExclusive( Session.DOOR_TIME );
//						}
//						notifyAll();
//						break;
//						
//					case CMD_PLAY:
//					case CMD_POSITION:
//						if( rt_command == CMD_PLAY ) {
//							dispatchPlay( startFrame );
//						} else {
//							dispatchPosition( startFrame );
//						}
//						// THRU
//						targetRate		= rt_context.getSourceRate() * rateScale;
//						// e.g. bufSizeH == 512 --> 0x1FF . Maske fuer frameCount
//						// wir geben dem producer einen halben halben buffer zeit (in millisec)
//						// d.h. bei 1000 Hz und halber buffer size von 512 sind das 256 millisec.
//						startTime		= System.currentTimeMillis() - 1;   // division by zero vermeiden
//						frameCount		= 0;
//						currentFrame			= startFrame;
//						notifyAll();
//						break commandLp;
//						
//					case CMD_QUIT:
//						dispatchQuit();
//						notifyAll();
//						return;
//						
//					default:
//						assert rt_command == CMD_IGNORE : rt_command;
//						break;
//					}
//					// sleep until next rt_command arrives
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				} while( true );
//			} // synchronized( this )
// 
//rt_loop:	while( threadRunning ) {
//				frameCount += rt_notifyTickStep;
//				currentFrame	   += rt_notifyTickStep;
//				sysTime		= System.currentTimeMillis();
//				currentRate = (double) (1000 * frameCount) / (sysTime - startTime);
//				while( currentRate > targetRate ) { // wir sind der zeit voraus
//					Thread.yield();
//					sysTime		= System.currentTimeMillis();
//					currentRate = (double) (1000 * frameCount) / (sysTime - startTime);
//				}
//
//				// handle stop + loop
//				if( currentFrame >= stopFrame ) {
//					if( isLooping() ) {
//						startFrame   = loopStart;
//						if( startFrame >= stopFrame ) {
//							goStop();
//							break rt_loop;
//						}
//						dispatchPosition( startFrame );
//						currentFrame		= startFrame;
//						startTime	= System.currentTimeMillis() - 1;
//						frameCount	= 0;
////						rt_producer.requestProduction(
////							new Span( startFrame, startFrame + rt_producer.source.bufSizeH ),
////							true, sysTime + deadline );
////						rt_producer.requestProduction(
////							new Span( startFrame + rt_producer.source.bufSizeH,
////									  startFrame + rt_producer.source.bufSize ),
////							false, sysTime + deadline );
//
//					} else {
//						goStop();
//						break rt_loop;
//					}
//				}
//				
//				for( i = 0; i < rt_numConsumers; i++ ) {
//					// XXX performativer mit bitshifted mask + AND ?
//					r = rt_requests[ i ];
//					if( r.active && r.notifyTicks && (frameCount % r.notifyTickStep == 0) ) {
//						rt_consumers[ i ].realtimeTick( rt_context, currentFrame );
////						rt_consumers[ i ].realtimeTick( rt_context, rt_producer.source, currentFrame );
//					}
//				}
//
//				try {
//					Thread.sleep( 0, 1 );
//				} catch( InterruptedException e1 ) {}
//			} // while( threadRunning )
//		} while( true );
//    }
    
	/**
	 *  Requests the thread to start
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really starts.
	 *
	 *  @synchronization	To be called in the event thread.
	 */
    public void play( double scale )
    {
		playSpan( new Span( doc.timeline.getPosition(), doc.timeline.getLength() ), scale );	// XXX sync?
    }

    public void playSpan( Span span, double scale )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		if( running ) return;

		startFrame		= span.start;
		loopInPlay		= isLooping() && loopStop > startFrame;
		stopFrame		= loopInPlay ? loopStop : span.stop;
		this.rateScale	= scale;
		frameFactor		= scale * rate / 1000;
		currentFrame	= startFrame;
		running			= true;
		dispatchPlay( startFrame );
		startTime		= System.currentTimeMillis();
	}
	
	public double getRateScale()
	{
		return rateScale;
	}
    
	/**
	 *  Sets the loop span for playback
	 *
	 *  @param  loopSpan	Span describing the new loop start and stop.
	 *						Passing null stops looping. 
	 *
	 *	@synchronization	If loopSpan != null, the caller must have sync on doc.timeline!
	 */
	public void setLoop( Span loopSpan )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		long	testFrame;
//		boolean readjust = false;
		
		if( loopSpan != null ) {
			if( !looping || (loopStart != loopSpan.start) || (loopStop != loopSpan.stop) ) {
				loopStart   = loopSpan.start;
				loopStop	= loopSpan.stop;
				looping		= true;
				if( running ) {
					if( currentFrame < loopStop ) {
						loopInPlay	= true;
						stopFrame	= loopStop;
					}
					// check for possible jumps
					testFrame = startFrame + (long) ((lastUpdate - startTime) * frameFactor + 0.5);
					if( loopInPlay && (testFrame >= loopStop) ) {
						testFrame = ((testFrame - loopStart) % (loopStop - loopStart)) + loopStart;
					}
					// seemingless re-adjustment of startFrame
					// so currentFrame doesn't jump
					if( testFrame != currentFrame ) {	
//System.err.println( "testFrame is " + testFrame + "; should be " + currentFrame );
						startFrame -= testFrame - currentFrame;
					}
					dispatchReadjust( startFrame );
				}
			}
		} else {
			if( looping ) {
				if( running && loopInPlay ) {
					// check for possible jumps
					testFrame = startFrame + (long) ((lastUpdate - startTime) * frameFactor + 0.5);
					// seemingless re-adjustment of startFrame
					// so currentFrame doesn't jump
					if( testFrame != currentFrame ) {
// System.err.println( "testFrame is " + testFrame + "; should be " + currentFrame );
						startFrame -= testFrame - currentFrame;
					}
				}
				loopInPlay	= false;
				looping		= false;
				if( running ) {
					stopFrame	= doc.timeline.getLength();
					dispatchReadjust( startFrame );
				}
			}
		}
	}
/*
	currentFrame = startFrame + (long) ((now - startTime) * frameFactor + 0.5);
	if( loopInPlay ) {
		if( currentFrame >= loopStop ) {
			currentFrame = ((currentFrame - loopStart) % (loopStop - loopStart)) + loopStart;
		}
*/
	/**
	 *  Returns whether looping
	 *  is active or not
	 *
	 *	@return	<code>true</code> if looping is used
	 */
	public boolean isLooping()
	{
		return looping;
	}

// we can uncomment this any time when the method is really needed
//	/**
//	 *  Returns whether current playback
//	 *  involves the loop region.
//	 *	(i.e. loop is active and playback was
//	 *	started with position <= loop end; it needn't be
//	 *	that the playback position is >= loop start though!)
//	 *
//	 *	@return	<code>true</code> if loop is relevant in current playback
//	 */
//	public boolean isInLoop()
//	{
//		return loopInPlay;
//	}

	/**
	 *	'Folds' a time span with regard to current loop settings.
	 *	That is, if a transport listener is calculating linear increasing
	 *	time spans from transport play offset, this method checks against
	 *	active and relevant (loopInPlay) loop settings and clips back
	 *	the span or portions of the span to the loop region if necessary.
	 *	<p>
	 *	This does not check against the document length so span stops
	 *	beyond doc.timeline.getLength() are possible and allowed.
	 *
	 *	@param	unfolded	the linear extrapolated time span from transport play
	 *	@param	loopMin		a minimum length of the loop such as to prevent cpu overload or
	 *						osc message overflow (imagine the user would make a 1 sample long loop).
	 *						leave to zero if no minimum required.
	 *	@return				an array of folded spans (array length is >= 1)
	 *
	 *	@todo	this method is not thread safe, hence should be called in the event
	 *			thread. this means, the trigger responder in SuperColliderPlayer
	 *			must be deferred!!!
	 */
	public Span[] foldSpans( Span unfolded, int loopMin )
	{
		// the quick one
		if( !loopInPlay || (unfolded.stop <= loopStop)) return new Span[] { unfolded };
		
		final long loopLen		= Math.max( loopMin, loopStop - loopStart );
		final long loopMinStop	= loopStart + loopLen;
		final long foldStart	= (unfolded.start < loopMinStop) ? unfolded.start : ((unfolded.start - loopStart) % loopLen) + loopStart;
		final long attemptStop	= foldStart + unfolded.getLength();
		
		// no splitting up required
		if( attemptStop <= loopMinStop ) return new Span[] { new Span( foldStart, attemptStop )};
		
		// pseudo-code:
		// numSpans				= (attemptStop - loopMinStop + loopLen-1) / loopLen + 1
		final int numSpans		= (int) ((attemptStop - loopStart - 1) / loopLen) + 1;
		final long foldStop		= ((attemptStop - loopStart) % loopLen) + loopStart;
		final Span[] folded		= new Span[ numSpans ];
		folded[ 0 ]				= new Span( foldStart, loopMinStop );
		for( int i = 1, j = numSpans - 1; i <= j; i++ ) {
			folded[ i ]			= new Span( loopStart, i < j ? loopMinStop : foldStop );
		}
		return folded;
	}
	
	/**
	 *  Requests the thread to stop
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really stops.
	 */
    public void stop()
    {
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

		if( running ) {
			running = false;
			updateCurrentFrame();
			dispatchStop( currentFrame );
		}
	}

	/**
	 *  Sends quit rt_command to the transport
	 *  returns only after the transport thread
	 *  stopped!
	 */
    public void quit()
    {
		running = false;
		dispatchQuit();
    }

	public long getCurrentFrame()
	{
		return updateCurrentFrame();
	}

	private long updateCurrentFrame()
	{
		final long now = System.currentTimeMillis();
		if( (now == lastUpdate) || !running ) return currentFrame;
		
		currentFrame	= startFrame + (long) ((now - startTime) * frameFactor + 0.5);
		lastUpdate		= now;
		if( loopInPlay ) {
			if( currentFrame >= loopStop ) {
				currentFrame = ((currentFrame - loopStart) % (loopStop - loopStart)) + loopStart;
			}
		} else if( currentFrame > stopFrame ) {
//			final boolean dispatch = (currentFrame - stopFrame) >= 128;
			currentFrame	= stopFrame;
			running			= false;
//			if( dispatch ) {
				dispatchStop( currentFrame );
//			}
		}
		return currentFrame;
	}

// ---------------- TimelineListener interface ---------------- 

	public void timelinePositioned( TimelineEvent e )
	{
		if( e.getSource() == this ) return;
		
		if( running ) {
			startFrame		= doc.timeline.getPosition();   // XXX sync?
			loopInPlay		= isLooping() && loopStop > startFrame;
			stopFrame		= loopInPlay ? loopStop : doc.timeline.getLength();
//			rateScale		= rate;
			currentFrame	= startFrame;
			dispatchPosition( startFrame );
			startTime		= System.currentTimeMillis();
			lastUpdate		= startTime;
		}
	}

	public void timelineChanged( TimelineEvent e ) {
		rate			= doc.timeline.getRate();
		frameFactor		= rateScale * rate / 1000;
	}
	
	public void timelineSelected( TimelineEvent e ) { /* ignored */ }
    public void timelineScrolled( TimelineEvent e ) { /* ignored */ }

// --------------- RealtimeHost interface ---------------

	/**
	 *  Returns whether the
	 *  thread is currently playing
	 *
	 *	@return	<code>true</code> if the transport is currently playing
	 */
	public boolean isRunning()
	{
		return running;
	}

	public void	showMessage( int type, String text )
	{
		System.err.println( text );
//		((ProgressComponent) root.getComponent( Main.COMP_MAIN )).showMessage( type, text );
	}

	// ------------- OSCRouter interface -------------
	
	public String oscGetPathComponent()
	{
		return OSC_TRANSPORT;
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
	
	public void oscCmd_play( RoutedOSCMessage rom )
	{
		try {
			final float r = rom.msg.getArgCount() == 1 ? 1.0f :
				Math.max( 0.25f, Math.min( 4f, ((Number) rom.msg.getArg( 1 )).floatValue() ));
//			actionPlay.perform( r );
			play( r );
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, 1 );
		}
	}

	public void oscCmd_stop( RoutedOSCMessage rom )
	{
		stop();
	}
}