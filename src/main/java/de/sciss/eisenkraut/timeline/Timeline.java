/*
 *  Timeline.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.timeline;

import java.awt.event.ActionEvent;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.undo.CompoundEdit;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.eisenkraut.edit.BasicCompoundEdit;
import de.sciss.eisenkraut.edit.TimelineVisualEdit;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.session.AbstractSessionObject;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.MapManager;
import de.sciss.io.Span;

/**
 *  This class describes the document's timeline
 *  properties, e.g. length, selection, visible span.
 *  It contains an event dispatcher for TimelineEvents
 *  which get fired when methods like setPosition are
 *  called.
 *
 *	TODO: view based stuff (visible span) should be removed
 */
public class Timeline
        extends AbstractSessionObject
        implements EventManager.Processor, OSCRouter {

    public static final String			MAP_KEY_RATE			= "rate";
    private static final String			MAP_KEY_LENGTH			= "len";
    private static final String			MAP_KEY_POSITION		= "pos";

    private double						rate;				// sample frames per second
    private long						length;				// total number of sample frames
    private long						position;			// current head position
    private Span						visibleSpan;		// what's being viewed in the TimelineFrame
    private Span						selectionSpan;

    protected final Session				doc;

    /**
     *	Name attribute of the object
     *	element in a session's XML file
     */
    public static final String			XML_OBJECT_NAME			= "timeline";

    // --- event handling ---

    private final EventManager			elm						= new EventManager( this );

    // --- actions ---

    private final ActionSelToPos	actionPosToSelBeginC	= new ActionSelToPos( 0.0f, true );
    private final ActionSelToPos	actionPosToSelEndC		= new ActionSelToPos( 1.0f, true );
    private final ActionSelToPos	actionPosToSelBegin		= new ActionSelToPos( 0.0f, false );
    private final ActionSelToPos	actionPosToSelEnd		= new ActionSelToPos( 1.0f, false );

    private static final String			OSC_TIMELINE			= "timeline";

    private final OSCRouterWrapper		osc;

    /**
     *  Creates a new empty timeline
     */
    public Timeline( Session doc )
    {
        super();

        this.doc		= doc;

        final MapManager map = getMap();

        map.putContext(this, MAP_KEY_RATE, new MapManager.Context(0, MapManager.Context.TYPE_DOUBLE, null, null, null,
                (double) 1000));
        map.putContext(this, MAP_KEY_LENGTH, new MapManager.Context(0, MapManager.Context.TYPE_LONG, null, null, null,
                (long) 0));
        map.putContext(this, MAP_KEY_POSITION, new MapManager.Context(0, MapManager.Context.TYPE_LONG, null, null, null,
                (long) 0));

        osc	= new OSCRouterWrapper( doc, this );

        clear( this );
        setName( XML_OBJECT_NAME );
    }

    public AbstractAction getPosToSelAction( boolean begin, boolean deselect )
    {
        return begin ? (deselect ? actionPosToSelBeginC : actionPosToSelBegin) :
                       (deselect ? actionPosToSelEndC : actionPosToSelEnd);
    }

    /**
     *  Clears the timeline, i.e. the length is set to zero,
     *  selection and visible span are cleared.
     *  The caller should ensure
     *  that event dispatching is paused!
     *
     *  @param  source  who originated the action
     */
    public void clear( Object source )
    {
        getMap().clearValues( source );
        setRate( source, 1000 );
        setPosition( source, 0 );
        setSelectionSpan( source, new Span() );
        setVisibleSpan( source, new Span() );
        setLength( source, 0 );
    }

    /**
     *  Pauses the event dispatching. No
     *  events are destroyed, only execution
     *  is deferred until resumeDispatcher is
     *  called.
     */
    public void pauseDispatcher()
    {
        elm.pause();
    }

    /**
     *  Resumes the event dispatching. Pending
     *  events will be diffused during the
     *  next run of the event thread.
     */
    public void resumeDispatcher()
    {
        elm.resume();
    }

    /**
     *  Queries the timeline sample rate
     *
     *  @return the rate of timeline data (trajectories etc.)
     *			in frames per second
     */
    public double getRate()
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        return rate;
    }

    /**
     *  Queries the timeline's length
     *
     *  @return the timeline length in frames
     */
    public long getLength()
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        return length;
    }

    /**
     *  Queries the timeline's position
     *
     *  @return the current timeline offset (the position
     *			of the vertical bar in the timeline frame)
     *			in frames
     */
    public long getPosition()
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        return position;
    }

    /**
     *  Queries the timeline's visible span
     *
     *  @return the portion of the timeline currently
     *			visible in the timeline frame. start and
     *			stop are measured in frames
     */
    public Span getVisibleSpan()
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        return visibleSpan;
    }

    /**
     *  Queries the timeline's selected span
     *
     *  @return the portion of the timeline currently
     *			selected (highlighted hoverState). start and
     *			stop are measured in frames
     */
    public Span getSelectionSpan()
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        return selectionSpan;
    }

    /**
     *  Changes the timeline sample rate. This
     *  fires a <code>TimelineEvent</code> (<code>CHANGED</code>). Note
     *  that there's no corresponding undoable edit.
     *
     *  @param  source  the source of the <code>TimelineEvent</code>
     *  @param  rate	the new rate of timeline data (trajectories etc.)
     *					in frames per second
     *
     *  @see	TimelineEvent#CHANGED
     */
    public void setRate( Object source, double rate )
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        this.rate = rate;
        if( source != null ) dispatchChange( source );
        getMap().putValue( this, MAP_KEY_RATE, rate);
    }

    /**
     *  Changes the timeline's length. This is
     *  normally done indirectly by creating an
     *  <code>EditInsertTimeSpan</code> or
     *  <code>EditRemoveTimeSpan</code> object.
     *  Alternatively <code>EditSetTimelineLength</code> provides
     *  a more low level object.
     *  This fires a <code>TimelineEvent</code> (<code>CHANGED</code>).
     *
     *  @param  source  the source of the <code>TimelineEvent</code>
     *  @param  length  the new timeline length in frames
     *
     *  @see	de.sciss.eisenkraut.edit.EditSetTimelineLength
     *  @see	TimelineEvent#CHANGED
     */
    public void setLength( Object source, long length )
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        this.length = length;
        if( source != null ) dispatchChange( source );
        getMap().putValue( this, MAP_KEY_LENGTH, length);
    }

    /**
     *  Changes the current the timeline's playback/insert position. This
     *  fires a <code>TimelineEvent</code> (<code>POSITIONED</code>).
     *  Often you'll want to use an <code>EditSetTimelinePosition</code> object.
     *
     *  @param  source		the source of the <code>TimelineEvent</code>
     *  @param  position	the new timeline offset (the position
     *						of the vertical bar in the timeline frame)
     *						in frames
     *
     *  @see	de.sciss.eisenkraut.edit.TimelineVisualEdit
     *  @see	TimelineEvent#POSITIONED
     */
    public void setPosition( Object source, long position )
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        this.position = position;
        if( source != null ) dispatchPosition( source );
        getMap().putValue( this, MAP_KEY_POSITION, position);
    }

    /**
     *  Changes the timeline's visible span. This
     *  fires a <code>TimelineEvent</code> (<code>SCROLLED</code>).
     *  Often you'll want to use an <code>EditSetTimelineScroll</code> object.
     *
     *  @param  source	the source of the <code>TimelineEvent</code>
     *  @param  span	the portion of the timeline that should be
     *					displayed in the timeline frame. start and
     *					stop are measured in frames
     *
     *  @see	de.sciss.eisenkraut.edit.TimelineVisualEdit
     *  @see	TimelineEvent#SCROLLED
     */
     public void setVisibleSpan( Object source, Span span )
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        this.visibleSpan = new Span( span );
        if( source != null ) dispatchScroll( source );
    }

    /**
     *  Changes the timeline's selected span. This
     *  fires a <code>TimelineEvent</code> (<code>SELECTED</code>).
     *  Often you'll want to use an <code>EditSetTimelineSelection</code> object.
     *
     *  @param  source	the source of the <code>TimelineEvent</code>
     *  @param  span	the portion of the timeline which should be
     *					selected (highlighted hoverState). start and
     *					stop are measured in frames
     *
     *  @see	de.sciss.eisenkraut.edit.TimelineVisualEdit
     *  @see	TimelineEvent#SELECTED
     */
    public void setSelectionSpan( Object source, Span span )
    {
        if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
        this.selectionSpan = new Span( span );
        if( source != null ) dispatchSelection( source );
    }

    /**
     *  Register a <code>TimelineListener</code>
     *  which will be informed about changes of
     *  the timeline (i.e. changes in rate and length,
     *  scrolling in the timeline frame and selection
     *  of timeline portions by the user).
     *
     *  @param  listener	the <code>TimelineListener</code> to register
     *  @see	de.sciss.app.EventManager#addListener( Object )
     */
    public void addTimelineListener( TimelineListener listener )
    {
        elm.addListener( listener );
    }

    /**
     *  Unregister a <code>TimelineListener</code>
     *  from receiving timeline events.
     *
     *  @param  listener	the <code>TimelineListener</code> to unregister
     *  @see	de.sciss.app.EventManager#removeListener( Object )
     */
    public void removeTimelineListener( TimelineListener listener )
    {
        elm.removeListener( listener );
    }

    public void editPosition( Object source, long pos )
    {
        doc.getUndoManager().addEdit( TimelineVisualEdit.position( source, doc, pos ).perform() );
    }

    public void editScroll( Object source, Span span )
    {
        doc.getUndoManager().addEdit( TimelineVisualEdit.scroll( source, doc, span ).perform() );
    }

    public void editSelect( Object source, Span span )
    {
        doc.getUndoManager().addEdit( TimelineVisualEdit.select( source, doc, span ).perform() );
    }

    /**
     *  This is called by the EventManager
     *  if new events are to be processed
     */
    public void processEvent( BasicEvent e )
    {
        TimelineListener listener;
        int i;

        for( i = 0; i < elm.countListeners(); i++ ) {
            listener = (TimelineListener) elm.getListener( i );
            switch( e.getID() ) {
            case TimelineEvent.CHANGED:
                listener.timelineChanged( (TimelineEvent) e );
                break;
            case TimelineEvent.POSITIONED:
                listener.timelinePositioned( (TimelineEvent) e );
                break;
            case TimelineEvent.SELECTED:
                listener.timelineSelected( (TimelineEvent) e );
                break;
            case TimelineEvent.SCROLLED:
                listener.timelineScrolled( (TimelineEvent) e );
                break;
            }
        } // for( i = 0; i < elm.countListeners(); i++ )
    }

    // utility function to create and dispatch a TimelineEvent
    private void dispatchChange( Object source )
    {
        TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.CHANGED,
                                              System.currentTimeMillis(), 0, null );
        elm.dispatchEvent( e2 );
    }

    // utility function to create and dispatch a TimelineEvent
    private void dispatchPosition( Object source )
    {
        TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.POSITIONED,
                                              System.currentTimeMillis(), 0, (double) getPosition());
        elm.dispatchEvent( e2 );
    }
    

    // utility function to create and dispatch a TimelineEvent
    private void dispatchSelection( Object source )
    {
        TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.SELECTED,
                                              System.currentTimeMillis(), 0, getSelectionSpan() );
        elm.dispatchEvent( e2 );
    }

    // utility function to create and dispatch a TimelineEvent
    private void dispatchScroll( Object source )
    {
        TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.SCROLLED,
                                              System.currentTimeMillis(), 0, getVisibleSpan() );
        elm.dispatchEvent( e2 );
    }

    public Class<?> getDefaultEditor() { return null; }

// ---------------- MapManager.Listener interface ---------------- 

    public void mapChanged( MapManager.Event e )
    {
        super.mapChanged( e );

        final Object source = e.getSource();

        if( source == this ) return;

        final Set<String>	keySet		= e.getPropertyNames();
        Object		val;
        boolean		dChange		= false;
        boolean		dPosition	= false;

        if( keySet.contains( MAP_KEY_RATE )) {
            val		= e.getManager().getValue( MAP_KEY_RATE );
            if( val != null ) {
                rate	= ((Number) val).doubleValue(); // Float.parseFloat( val.toString() );
                dChange	= true;
            }
        }
        if( keySet.contains( MAP_KEY_LENGTH )) {
            val		= e.getManager().getValue( MAP_KEY_LENGTH );
            if( val != null ) {
                length	= ((Number) val).longValue();
                dChange	= true;
                if( visibleSpan.isEmpty() && length > 0 ) setVisibleSpan( this, new Span( 0, length ));
            }
        }
        if( keySet.contains( MAP_KEY_POSITION )) {
            val		= e.getManager().getValue( MAP_KEY_POSITION );
            if( val != null ) {
                position	= ((Number) val).longValue();
                dPosition	= true;
            }
        }

        if( dChange ) dispatchChange( source );
        if( dPosition ) dispatchPosition( source );
    }

    // ------------- OSCRouter interface -------------

    public String oscGetPathComponent()
    {
        return OSC_TIMELINE;
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

    public Object oscQuery_position()
    {
        return getPosition();
    }

    public Object oscQuery_selectionStart()
    {
        return getSelectionSpan().start;
    }

    public Object oscQuery_selectionStop()
    {
        return getSelectionSpan().stop;
    }

    public Object oscQuery_viewStart()
    {
        return getVisibleSpan().start;
    }

    public Object oscQuery_viewStop()
    {
        return getVisibleSpan().stop;
    }

    public Object oscQuery_rate()
    {
        return getRate();
    }

    public Object oscQuery_length()
    {
        return getLength();
    }

    public void oscCmd_position( RoutedOSCMessage rom )
    {
        try {
            final long pos = ((Number) rom.msg.getArg( 1 )).longValue();
            editPosition( this, Math.max( 0, Math.min( getLength(), pos )));
        }
        catch( IndexOutOfBoundsException e1 ) {
            OSCRoot.failedArgCount( rom );
        }
        catch( ClassCastException e1 ) {
            OSCRoot.failedArgType( rom, 0 );
        }
    }

    public void oscCmd_select( RoutedOSCMessage rom )
    {
        final long		start, stop;
        int				argIdx	= 1;

        try	{
            start	= Math.max( 0, Math.min( getLength(), ((Number) rom.msg.getArg( argIdx )).longValue() ));
            argIdx++;
            stop	= Math.max( start, Math.min( getLength(), ((Number) rom.msg.getArg( argIdx )).longValue() ));
            editSelect( this, new Span( start, stop ));
        }
        catch( IndexOutOfBoundsException e1 ) {
            OSCRoot.failedArgCount( rom );
        }
        catch( ClassCastException e1 ) {
            OSCRoot.failedArgType( rom, argIdx );
        }
    }

    public void oscCmd_view( RoutedOSCMessage rom )
    {
        final long		start, stop;
        int				argIdx	= 1;

        try	{
            start	= Math.max( 0, Math.min( getLength(), ((Number) rom.msg.getArg( argIdx )).longValue() ));
            argIdx++;
            stop	= Math.max( start, Math.min( getLength(), ((Number) rom.msg.getArg( argIdx )).longValue() ));
            editScroll( this, new Span( start, stop ));
        }
        catch( IndexOutOfBoundsException e1 ) {
            OSCRoot.failedArgCount( rom );
        }
        catch( ClassCastException e1 ) {
            OSCRoot.failedArgType( rom, argIdx );
        }
    }

// ---------------- internal classes ---------------- 

    /*
     *	@warning	have to keep an eye on this. with weight as float
     *				there were quantization errors. with double seems
     *				to be fine. haven't checked with really long files!!
     */
    @SuppressWarnings("serial")
    private class ActionSelToPos
            extends AbstractAction {
        private final double	weight;
        private final boolean	deselect;

        protected ActionSelToPos( double weight, boolean deselect )
        {
            super();

            this.weight		= weight;
            this.deselect	= deselect;
        }

        public void actionPerformed( ActionEvent e )
        {
            perform();
        }

        private void perform()
        {
            Span			selSpan;
            CompoundEdit	edit;
            long			pos;

            selSpan		= getSelectionSpan();
            if( selSpan.isEmpty() ) return;

            edit	= new BasicCompoundEdit();
            if( deselect ) edit.addEdit( TimelineVisualEdit.select( this, doc, new Span() ).perform() );
            pos		= (long) (selSpan.getStart() + selSpan.getLength() * weight + 0.5);
            edit.addEdit( TimelineVisualEdit.position( this, doc, pos ).perform() );
            edit.end();
            doc.getUndoManager().addEdit( edit );
        }
    } // class actionSelToPosClass
}