/*
 *  TransportToolBar.java
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

package de.sciss.eisenkraut.realtime;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.gui.GraphicsUtil;
import de.sciss.eisenkraut.gui.TimeLabel;
import de.sciss.eisenkraut.gui.ToolBar;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.ParamField;
import de.sciss.io.Span;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Disposable;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 *	A GUI component showing
 *	basic transport gadgets. This class
 *	invokes the appropriate methods in the
 *	<code>Transport</code> class when these
 *	gadgets are clicked.
 *	<p><pre>
 *	Keyboard shortcuts :	space or numpad-0 : play / stop
 *						G : go to time
 *						shift + (alt) + space : play half or double speed
 *						numpad 1 / 2 : rewind / fast forward
 *	</pre>
 *
 *	TODO: (FIXED?) cueing sometimes uses an obsolete start position.
 *				idea: cue speed changes with zoom level
 *
 *	TODO: (FIXED?) when palette is opened when transport is running(?)
 *				realtime listener is not registered (only after timeline change)
 */
@SuppressWarnings("serial")
public class TransportToolBar
        extends Box
        implements TimelineListener, TransportListener,    // RealtimeConsumer,
        Disposable {

    protected final Session			doc;
    protected final Transport		transport;
    
    protected final JButton			ggPlay, ggStop;
    private final JToggleButton		ggLoop;
    private final ActionLoop		actionLoop;

    private final ToolBar			toolBar;
    protected final TimeLabel		lbTime;

    protected double				rate;
    private int						customGroup		= 3;

    // forward / rewind cueing
    protected boolean				isCueing		= false;
    protected int					cueStep;
    protected final Timer			cueTimer;
    protected long					cuePos;

    private final Timer				playTimer;

    /**
     *	Creates a new transport palette. Other classes
     *	may wish to add custom gadgets using <code>addButton</code>
     *	afterwards.
     *
     *	@param	doc		Session Session
     */
    public TransportToolBar(final Session doc) {
        super(BoxLayout.X_AXIS);

        this.doc	= doc;
        transport   = doc.getTransport();
        rate		= doc.timeline.getRate();

        final AbstractAction	actionPlay, actionStop, actionGoToTime;
        final JButton			ggFFwd, ggRewind;
        final InputMap			iMap		= this.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
        final ActionMap			aMap		= this.getActionMap();

        toolBar = new ToolBar(SwingConstants.HORIZONTAL);

        ggRewind = new JButton();
        GraphicsUtil.setToolIcons(ggRewind, GraphicsUtil.createToolIcons(GraphicsUtil.ICON_REWIND));
        ggRewind.addChangeListener(new CueListener(ggRewind, -100));
        ActionCue actionRwdOn = new ActionCue(ggRewind, true);
        ActionCue actionRwdOff = new ActionCue(ggRewind, false);
        actionRwdOn.setPair(actionRwdOff);
        actionRwdOff.setPair(actionRwdOn);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0, false), "startrwd");
        aMap.put("startrwd", actionRwdOn);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0, true), "stoprwd");
        aMap.put("stoprwd", actionRwdOff);

        actionStop = new ActionStop();
        ggStop = new JButton(actionStop);
        GraphicsUtil.setToolIcons(ggStop, GraphicsUtil.createToolIcons(GraphicsUtil.ICON_STOP));

        actionPlay = new ActionPlay();
        ggPlay = new JButton(actionPlay);
        GraphicsUtil.setToolIcons(ggPlay, GraphicsUtil.createToolIcons(GraphicsUtil.ICON_PLAY));

        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playstop");
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_MASK), "playstop");
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_MASK | InputEvent.ALT_MASK), "playstop");
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0), "playstop");
        aMap.put("playstop", new ActionTogglePlayStop());
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "playsel");
        aMap.put("playsel", new ActionPlaySelection());

        ggFFwd = new JButton();
        GraphicsUtil.setToolIcons(ggFFwd, GraphicsUtil.createToolIcons(GraphicsUtil.ICON_FASTFORWARD));
        ggFFwd.addChangeListener( new CueListener( ggFFwd, 100 ));
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0, false), "startfwd");
        ActionCue actionFwdOn  = new ActionCue( ggFFwd, true );
        ActionCue actionFwdOff = new ActionCue( ggFFwd, false );
        actionFwdOn .setPair(actionFwdOff);
        actionFwdOff.setPair(actionFwdOn);
        aMap.put("startfwd", actionFwdOn);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0, true), "stopfwd");
        aMap.put("stopfwd", actionFwdOff);

        actionLoop = new ActionLoop();
        ggLoop = new JToggleButton(actionLoop);
        GraphicsUtil.setToolIcons(ggLoop, GraphicsUtil.createToolIcons(GraphicsUtil.ICON_LOOP));
        GUIUtil.createKeyAction(ggLoop, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0));
        toolBar.addButton(ggRewind);
        toolBar.addButton(ggStop);
        toolBar.addButton(ggPlay);
        toolBar.addButton(ggFFwd);
        toolBar.addToggleButton(ggLoop, 2);

        actionGoToTime  = new ActionGoToTime();
        lbTime			= new TimeLabel();
        lbTime.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lbTime.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                actionGoToTime.actionPerformed(null);
                lbTime.normalState();
            }

            public void mouseEntered(MouseEvent e) {
                lbTime.hoverState();
            }

            public void mouseExited(MouseEvent e) {
                lbTime.normalState();
            }
        });
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "gototime");
        aMap.put("gototime", actionGoToTime);

        this.add(toolBar);
        final Box b2 = Box.createVerticalBox();
        b2.add(Box.createVerticalGlue());
        b2.add(lbTime);
        b2.add(Box.createVerticalGlue());
        this.add(Box.createHorizontalStrut(4));
        this.add(b2);

        // --- Listener ---
        // new DynamicAncestorAdapter(this).addTo(this);

        cueTimer = new Timer(25, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cuePos = Math.max(0, Math.min(doc.timeline.getLength(), cuePos + (long) (cueStep * rate) / 1000));
                doc.timeline.editPosition(this, cuePos);
                GraphicsUtil.sync();
            }
        });

        playTimer = new Timer(27, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                lbTime.setTime(transport.getCurrentFrame() / rate);
                GraphicsUtil.sync();
            }
        });

        doc.timeline.addTimelineListener (this);
        transport   .addTransportListener(this);
    }

    public void setLoop(boolean onOff) {
        ggLoop.setSelected(onOff);
    }

    /**
     *	Adds a new button to the transport palette
     *
     *	@param	b	the button to add
     */
    public void addButton(AbstractButton b) {
        if (b instanceof JToggleButton) {
            toolBar.addToggleButton((JToggleButton) b, customGroup);
            customGroup++;
        } else {
            toolBar.addButton(b);
        }
    }

    public void setOpaque(boolean b) {
        toolBar.setOpaque(b);
        lbTime .setOpaque(b);
        super  .setOpaque(b);
    }

// ---------------- TimelineListener interface ----------------

    public void timelineSelected(TimelineEvent e) {
        if (ggLoop.isSelected()) {
            actionLoop.updateLoop();
        }
    }

    public void timelineChanged(TimelineEvent e) {
        rate = doc.timeline.getRate();
        lbTime.setTime(transport.getCurrentFrame() / rate);
    }

    public void timelineScrolled(TimelineEvent e) { /* ignore */ }

    public void timelinePositioned(TimelineEvent e) {
        final long pos = doc.timeline.getPosition();

        if (!isCueing) cuePos = pos;
        lbTime.setTime(pos / rate);
    }

// ---------------- TransportListener interface ---------------- 

    public void transportStop( Transport t, long pos )
    {
        ggPlay.setSelected( false );
        if( isCueing ) {
            cuePos = pos;
            cueTimer.restart();
        }
        playTimer.stop();
    }

    public void transportPlay( Transport t, long pos, double pRate )
    {
        ggPlay.setSelected( true );
        cueTimer.stop();
        playTimer.restart();
    }

    public void transportQuit( Transport t )
    {
        cueTimer.stop();
        playTimer.stop();
    }

    public void transportPosition( Transport t, long pos, double pRate ) { /* ignore */ }
    public void transportReadjust( Transport t, long pos, double pRate ) { /* ignore */ }

// ---------------- Disposable interface ---------------- 

    public void dispose()
    {
        playTimer.stop();
    }

// ---------------- actions ---------------- 

    @SuppressWarnings("serial")
    private class ActionGoToTime
            extends AbstractAction {

        private Param		value	= null;
        private ParamSpace	space	= null;

        protected ActionGoToTime() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            final int					result;
            final Param					positionSmps;
            final Box					msgPane;
            final DefaultUnitTranslator	timeTrans;
            final ParamField			ggPosition;
//			final JComboBox				ggPosCombo;
            final Application			app	= AbstractApplication.getApplication();

            msgPane			= Box.createVerticalBox();
            // XXX sync
            timeTrans		= new DefaultUnitTranslator();
            ggPosition		= new ParamField( timeTrans );
            ggPosition.addSpace( ParamSpace.spcTimeHHMMSS );
            ggPosition.addSpace( ParamSpace.spcTimeSmps );
            ggPosition.addSpace( ParamSpace.spcTimeMillis );
            ggPosition.addSpace( ParamSpace.spcTimePercentF );
            timeTrans.setLengthAndRate( doc.timeline.getLength(), doc.timeline.getRate() );	// XXX sync
            if( value != null ) {
                ggPosition.setSpace( space );
                ggPosition.setValue( value );
            }
//			ggPosition.setValue( position );
//			lbCurrentTime	= new TimeLabel( new Color( 0xE0, 0xE0, 0xE0 ));

//			ggPosition.setBorder( new ComboBoxEditorBorder() );
//			ggPosCombo = new JComboBox();
//			ggPosCombo.setEditor( ggPosition );
//			ggPosCombo.setEditable( true );

//			msgPane.gridAdd( ggPosCombo, 0, 1, -1, 1 );
            msgPane.add( Box.createVerticalGlue() );
//			msgPane.add( ggPosCombo );
JButton ggCurrent = new JButton( app.getResourceString( "buttonSetCurrent" ));	// "Current"
ggCurrent.setFocusable( false );
//JLabel lbArrow = new JLabel( "\u2193" );	// "\u2939"
//Box b = Box.createHorizontalBox();
//b.add( lbArrow );
//b.add( ggCurrent );
ggCurrent.addActionListener( new ActionListener() {
    public void actionPerformed( ActionEvent ae )
    {
        final long pos = transport.isRunning() ? transport.getCurrentFrame() : doc.timeline.getPosition();
        ggPosition.setValue( new Param( pos, ParamSpace.TIME | ParamSpace.SMPS ));	// XXX sync
        ggPosition.requestFocusInWindow();
    }
});
//msgPane.add( b );
msgPane.add( ggCurrent );
            msgPane.add( ggPosition );
            msgPane.add( Box.createVerticalGlue() );

            GUIUtil.setInitialDialogFocus( ggPosition );

//			ggPosCombo.removeAllItems();
//			// XXX sync
//			ggPosCombo.addItem( new StringItem( new Param( doc.timeline.getPosition() / doc.timeline.getRate(), ParamSpace.TIME | ParamSpace.SECS | ParamSpace.HHMMSS ).toString(), "Current" ));

            final JOptionPane op = new JOptionPane( msgPane, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION );
//			result = JOptionPane.showOptionDialog( BasicWindowHandler.getWindowAncestor( lbTime ), msgPane,
//				app.getResourceString( "inputDlgGoToTime" ),
//				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );
            result = BasicWindowHandler.showDialog( op, BasicWindowHandler.getWindowAncestor( lbTime ), app.getResourceString( "inputDlgGoToTime" ));

            if( result == JOptionPane.OK_OPTION ) {
                value			= ggPosition.getValue();
                space			= ggPosition.getSpace();
                positionSmps	= timeTrans.translate( value, ParamSpace.spcTimeSmps );
                doc.timeline.editPosition( this,
                    Math.max( 0, Math.min( doc.timeline.getLength(),
                  (long) positionSmps.val )));
            }
        }
    } // class actionGoToTimeClass

    @SuppressWarnings("serial")
    private class ActionTogglePlayStop
            extends AbstractAction {

        protected ActionTogglePlayStop() { /* empty */ }

        public void actionPerformed(ActionEvent e) {
            if (transport.isRunning()) {
                ggStop.doClick();
            } else {
                ggPlay.doClick();
            }
        }
    } // class actionTogglePlayStopClass


    @SuppressWarnings("serial")
    private class ActionPlaySelection
            extends AbstractAction {

        protected ActionPlaySelection() { /* empty */ }

        public void actionPerformed( ActionEvent e )
        {
            final Span span;

            if( transport.isRunning() ) {
                transport.stop();
            }
            span = doc.timeline.getSelectionSpan();
            if( !span.isEmpty() ) {
                transport.playSpan( span, 1.0 );
            } else {
                transport.play( 1.0 );
            }
        }
    } // class actionPlaySelectionClass

    @SuppressWarnings("serial")
    private static class ActionCue
            extends AbstractAction {

        private final boolean			onOff;
        private final AbstractButton	b;
        private final Timer             t;
        private ActionCue pair;
        private long lastWhen = 0L;

        public void setPair(ActionCue p) {
            pair = p;
        }

        public long getLastWhen() {
            if (!onOff) t.stop();
            return lastWhen;
        }

        protected ActionCue(AbstractButton b, boolean onOff) {
            this.onOff = onOff;
            this.b = b;

            if (onOff) t = null;
            else t = new javax.swing.Timer(5, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    perform();
                }
            });
        }

        private void perform() {
            final ButtonModel bm = b.getModel();
            if( bm.isPressed() != onOff ) bm.setPressed( onOff );
            if( bm.isArmed()   != onOff ) bm.setArmed(   onOff );
        }

        public void actionPerformed(ActionEvent e) {
            lastWhen = e.getWhen();
            if (onOff) {
                if (pair.getLastWhen() == lastWhen) return;  // Linux repeat bullshit
                perform();
            } else {
                t.restart();
            }
        }
    } // class actionCueClass

    @SuppressWarnings("serial")
    private class ActionLoop
            extends AbstractAction {

        protected ActionLoop()
        {
            super();
        }

        public void actionPerformed( ActionEvent e )
        {
            if( ((AbstractButton) e.getSource()).isSelected() ) {
//				if( doc.bird.attemptShared( Session.DOOR_TIME, 200 )) {
//					try {
                        updateLoop();
//					}
//					finally {
//						doc.bird.releaseShared( Session.DOOR_TIME );
//					}
//				} else {
//					((AbstractButton) e.getSource()).setSelected( false );
//				}
            } else {
                transport.setLoop( null );
            }
        }

        protected void updateLoop()
        {
            Span span;

//			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//			try {
                span = doc.timeline.getSelectionSpan();
                transport.setLoop( span.isEmpty() ? null : span );
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIME );
//			}
        }
    } // class actionLoopClass

    private class CueListener
    implements ChangeListener
    {
        private final ButtonModel	bm;
        private boolean				transportWasRunning	= false;
        private final int			step;

        // step = in millisecs, > 0 = fwd, < = rwd
        protected CueListener( AbstractButton b, int step )
        {
            bm			= b.getModel();
            this.step	= step;
        }

        public void stateChanged( ChangeEvent e )
        {
            if( isCueing && !bm.isArmed() ) {
                // System.out.println("---1");
                isCueing	= false;
                cueTimer.stop();
                if( transportWasRunning ) {
                    transport.play( 1.0f );
                }
            } else if( !isCueing && bm.isArmed() ) {
                // System.out.println("---2");
                transportWasRunning = transport.isRunning();
                cueStep		= step;
                isCueing	= true;
                if( transportWasRunning ) {
                    transport.stop();
                } else {
                    cueTimer.restart();
                }
            }
        }
    }

    // --------------- internal actions ---------------

    @SuppressWarnings("serial")
    private class ActionPlay
            extends AbstractAction {

        protected ActionPlay()
        {
            super();
        }

        public void actionPerformed( ActionEvent e )
        {
            perform( (e.getModifiers() & ActionEvent.SHIFT_MASK) == 0 ? 1.0f :
                        ((e.getModifiers() & ActionEvent.ALT_MASK) == 0 ? 0.5f : 2.0f) );
        }

        protected void perform( float scale )
        {
            if( doc.timeline.getPosition() == doc.timeline.getLength() ) {
//				doc.getFrame().addCatchBypass();
                doc.timeline.editPosition( transport, 0 );
//				doc.getFrame().removeCatchBypass();
            }
            transport.play( scale );
        }
    } // class actionPlayClass

    @SuppressWarnings("serial")
    private class ActionStop
            extends AbstractAction {

        protected ActionStop()
        {
            super();
        }

        public void actionPerformed( ActionEvent e )
        {
            transport.stop();
        }
    } // class actionStopClass
}