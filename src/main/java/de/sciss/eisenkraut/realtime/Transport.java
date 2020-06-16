/*
 *  Transport.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.realtime;

import java.awt.EventQueue;
import java.util.ArrayList;
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
 *	It provides means for registering and un-registering
 *	realtime consumers and communicates with a
 *	RealtimeProducer which is responsible for the
 *	actual data production. Transport clocking is
 *	performed within an extra thread from within
 *	the consumer's methods are called and registered
 *	transport listeners are informed about actions.
 */
public class Transport
        implements TimelineListener, OSCRouter, Disposable {

    protected final Session doc;

    private boolean looping 	= false;
    private boolean loopInPlay 	= false;
    private long 	loopStart, loopStop;
    private double 	rate;
    private double 	frameFactor;
    private long 	lastUpdate;

    // high level listeners
    private final List<TransportListener> collListeners  = new ArrayList<TransportListener>();

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
    public void addTransportListener(TransportListener listener) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        collListeners.add(listener);
        if (running) {
            listener.transportPlay(this, updateCurrentFrame(), rateScale);
        }
    }

    /**
     * Unregisters a transport listener
     *
     * @param    listener    the listener to remove from the event dispatching
     */
    public void removeTransportListener(TransportListener listener) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        collListeners.remove(listener);
    }

    private void dispatchStop(long pos) {
        for (TransportListener collListener : collListeners) {
            (collListener).transportStop(this, pos);
        }
        if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
            PrefsUtil.KEY_INSERTIONFOLLOWSPLAY, false )) {

            doc.timeline.editPosition( this, pos );
        } else {
            doc.timeline.setPosition( this, doc.timeline.getPosition() );
        }
    }

    private void dispatchPosition(long pos) {
        for (TransportListener collListener : collListeners) {
            (collListener).transportPosition(this, pos, rateScale);
        }
    }

    private void dispatchPlay(long pos) {
        for (TransportListener collListener : collListeners) {
            (collListener).transportPlay(this, pos, rateScale);
        }
    }

    private void dispatchReadjust(long pos) {
        for (TransportListener collListener : collListeners) {
            (collListener).transportReadjust(this, pos, rateScale);
        }
    }

    private void dispatchQuit() {
        for (TransportListener collListener : collListeners) {
            try {
                (collListener).transportQuit(this);
            } catch (Exception e1) {
                System.err.println("[@transport]" + e1.getLocalizedMessage());
            }
        }
    }

    /**
     *  Requests the thread to start
     *  playing. TransportListeners
     *  are informed when the
     *  playing really starts.
     */
    public void play(double scale) {
        playSpan(new Span(doc.timeline.getPosition(), doc.timeline.getLength()), scale);    // XXX sync?
    }

    public void playSpan(Span span, double scale) {
        if (!EventQueue.isDispatchThread()) throw new IllegalMonitorStateException();

        if (running) return;

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
     */
    public void setLoop( Span loopSpan )
    {
        if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();

        long	testFrame;

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
                    // seamless re-adjustment of startFrame
                    // so currentFrame doesn't jump
                    if( testFrame != currentFrame ) {
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
                    // seamless re-adjustment of startFrame
                    // so currentFrame doesn't jump
                    if( testFrame != currentFrame ) {
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
     *	Note: this method is not thread safe, hence should be called in the event
     *			thread. this means, the trigger responder in SuperColliderPlayer
     *			must be deferred!!!
     *
     *	@param	unfolded	the linear extrapolated time span from transport play
     *	@param	loopMin		a minimum length of the loop such as to prevent cpu overload or
     *						osc message overflow (imagine the user would make a 1 sample long loop).
     *						leave to zero if no minimum required.
     *	@return				an array of folded spans (array length is greater than or equal to 1)
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
        } else {
            currentFrame	= doc.timeline.getPosition();
        }
    }

    public void timelineChanged( TimelineEvent e ) {
        rate			= doc.timeline.getRate();
        frameFactor		= rateScale * rate / 1000;
    }

    public void timelineSelected(TimelineEvent e) { /* ignored */ }

    public void timelineScrolled(TimelineEvent e) { /* ignored */ }

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

    public void showMessage(int type, String text) {
        System.err.println(text);
    }

    // ------------- OSCRouter interface -------------

    public String oscGetPathComponent() {
        return OSC_TRANSPORT;
    }

    public void oscRoute(RoutedOSCMessage rom) {
        osc.oscRoute(rom);
    }

    public void oscAddRouter(OSCRouter subRouter) {
        osc.oscAddRouter(subRouter);
    }

    public void oscRemoveRouter(OSCRouter subRouter) {
        osc.oscRemoveRouter(subRouter);
    }

    public Object oscQuery_position() {
        return getCurrentFrame();
    }

    public Object oscQuery_running() {
        return isRunning() ? 1 : 0;
    }

    public void oscCmd_play(RoutedOSCMessage rom) {
        try {
            final float r = rom.msg.getArgCount() == 1 ? 1.0f :
                    Math.max(0.25f, Math.min(4f, ((Number) rom.msg.getArg(1)).floatValue()));
//			actionPlay.perform( r );
            play(r);
        } catch (ClassCastException e1) {
            OSCRoot.failedArgType(rom, 1);
        }
    }

    public void oscCmd_stop(RoutedOSCMessage rom) {
        stop();
    }
}