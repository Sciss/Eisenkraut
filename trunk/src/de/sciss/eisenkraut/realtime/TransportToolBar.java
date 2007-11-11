/*
 *  TransportToolBar.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2007 Hanns Holger Rutz. All rights reserved.
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
 *  Change log:
 *		12-May-05	re-created from de.sciss.meloncillo.realtime.TransportPalette
 *		16-Jul-05	fixed empty loop spans
 *		25-Jul-05	permanently adds timeline + transport listener
 *					(crucial for instant loop span update)
 *		03-Aug-05	converted to tool bar ; cue shortcuts
 *		26-Feb-06	moved to double precision
 */

package de.sciss.eisenkraut.realtime;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.sciss.eisenkraut.gui.GraphicsUtil;
import de.sciss.eisenkraut.gui.TimeLabel;
import de.sciss.eisenkraut.gui.ToolBar;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.ParamField;
import de.sciss.io.Span;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Disposable;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *	A GUI component showing
 *	basic transport gadgets. This class
 *	invokes the appropriate methods in the
 *	<code>Transport</code> class when these
 *	gadgets are clicked.
 *	<p><pre>
 *	Keyb.shortcuts :	space or numpad-0 : play / stop
 *						G : go to time
 *						shift + (alt) + space : play half or double speed
 *						numpad 1 / 2 : rewind / fast forward
 *	</pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 20-Sep-06
 *
 *	@todo		(FIXED?) cueing sometimes uses an obsolete start position.
 *				idea: cue speed changes with zoom level
 *
 *	@todo		(FIXED?) when palette is opened when transport is running(?)
 *				realtime listener is not registered (only after timeline change)
 */
public class TransportToolBar
extends Box
implements  TimelineListener, TransportListener,	// RealtimeConsumer,
			DynamicListening, Disposable
{
	private final Session			doc;
	private final Transport			transport;
    
	private final JButton			ggPlay, ggStop;
	private final JToggleButton		ggLoop;
	private final actionLoopClass	actionLoop;

	private final ToolBar			toolBar;
	private final TimeLabel			lbTime;
	
	private double					rate;
	private int						customGroup		= 3;

//	private static final MessageFormat   msgFormat =
//		new MessageFormat( "{0,number,integer}:{1,number,00.000}", Locale.US );		// XXX US locale

	// forward / rewind cueing
	private boolean					isCueing		= false;
	private int						cueStep;
	private final Timer				cueTimer;
	private long					cuePos;
	
	private final Timer				playTimer;

	/**
	 *	Creates a new transport palette. Other classes
	 *	may wish to add custom gadgets using <code>addButton</code>
	 *	afterwards.
	 *
	 *	@param	doc		Session Session
	 */
	public TransportToolBar( final Session doc )
	{
		super( BoxLayout.X_AXIS );
		
		this.doc	= doc;
		transport   = doc.getTransport();
		rate		= doc.timeline.getRate();

		final AbstractAction	actionPlay, actionStop, actionGoToTime;
		final JButton			ggFFwd, ggRewind;
		final InputMap			imap		= this.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap			amap		= this.getActionMap();

		toolBar			= new ToolBar( ToolBar.HORIZONTAL );

        ggRewind		= new JButton();
		GraphicsUtil.setToolIcons( ggRewind, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_REWIND ));
		ggRewind.addChangeListener( new CueListener( ggRewind, -100 ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1, 0, false ), "startrwd" );
		amap.put( "startrwd", new actionCueClass( ggRewind, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1, 0, true ), "stoprwd" );
		amap.put( "stoprwd", new actionCueClass( ggRewind, false ));

		actionStop		= transport.getStopAction();
        ggStop			= new JButton( actionStop );
		GraphicsUtil.setToolIcons( ggStop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_STOP ));

		actionPlay		= transport.getPlayAction();
        ggPlay			= new JButton( actionPlay );
		GraphicsUtil.setToolIcons( ggPlay, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_PLAY ));

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, 0 ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, KeyEvent.SHIFT_MASK ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, KeyEvent.SHIFT_MASK | KeyEvent.ALT_MASK ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD0, 0 ), "playstop" );
		amap.put( "playstop", new actionTogglePlayStopClass() );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK ), "playsel" );
		amap.put( "playsel", new actionPlaySelectionClass() );

        ggFFwd			= new JButton();
		GraphicsUtil.setToolIcons( ggFFwd, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_FASTFORWARD ));
		ggFFwd.addChangeListener( new CueListener( ggFFwd, 100 ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD2, 0, false ), "startfwd" );
		amap.put( "startfwd", new actionCueClass( ggFFwd, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD2, 0, true ), "stopfwd" );
		amap.put( "stopfwd", new actionCueClass( ggFFwd, false ));

		actionLoop		= new actionLoopClass();
		ggLoop			= new JToggleButton( actionLoop );
		GraphicsUtil.setToolIcons( ggLoop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_LOOP ));
		GUIUtil.createKeyAction( ggLoop, KeyStroke.getKeyStroke( KeyEvent.VK_DIVIDE, 0));
		toolBar.addButton( ggRewind );
		toolBar.addButton( ggStop );
		toolBar.addButton( ggPlay );
		toolBar.addButton( ggFFwd );
		toolBar.addToggleButton( ggLoop, 2 );
//        HelpGlassPane.setHelp( toolBar, "TransportTools" );
        
		actionGoToTime  = new actionGoToTimeClass();
		lbTime			= new TimeLabel();
//        HelpGlassPane.setHelp( lbTime, "TransportPosition" );
		lbTime.setCursor( new Cursor( Cursor.HAND_CURSOR ));
		lbTime.addMouseListener( new MouseAdapter() {
			public void mouseClicked( MouseEvent e )
			{
				actionGoToTime.actionPerformed( null );
				lbTime.black();
			}
			
			public void mouseEntered( MouseEvent e )
			{
				lbTime.blue();
			}

			public void mouseExited( MouseEvent e )
			{
				lbTime.black();
			}
		});
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_G, 0 ), "gototime" );
		amap.put( "gototime", actionGoToTime );
		
		this.add( toolBar );
Box b2 = Box.createVerticalBox();
b2.add( Box.createVerticalGlue() );
b2.add( lbTime );
b2.add( Box.createVerticalGlue() );
this.add( Box.createHorizontalStrut( 4 ));
		this.add( b2 );
//		this.add( Box.createHorizontalGlue() );
		
		// --- Listener ---
		new DynamicAncestorAdapter( this ).addTo( this );

		cueTimer = new Timer( 25, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				cuePos = Math.max( 0, Math.min( doc.timeline.getLength(), cuePos + (long) (cueStep * rate) / 1000 ));
//					doc.getUndoManager().addEdit( TimelineVisualEdit.position( this, doc, cuePos ));
				doc.timeline.editPosition( this, cuePos );
			}
		});
		
		playTimer = new Timer( 27, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				lbTime.setTime( new Double( transport.getCurrentFrame() / rate ));
			}
		});
	
		doc.timeline.addTimelineListener( this );
		transport.addTransportListener( this );
	}
	
//	/**
//	 *	Causes the timeline position label
//	 *	to blink red to indicate a dropout error
//	 */
//	public void blink()
//	{
//		lbTime.blink();
//	}

	/**
	 *	Adds a new button to the transport palette
	 *
	 *	@param	b	the button to add
	 */
	public void addButton( AbstractButton b )
	{
		if( b instanceof JToggleButton ) {
			toolBar.addToggleButton( (JToggleButton) b, customGroup );
			customGroup++;
		} else {
			toolBar.addButton( b );
		}
	}
	
	public void setOpaque( boolean b )
	{
		toolBar.setOpaque( b );
		lbTime.setOpaque( b );
		super.setOpaque( b );
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
//		transport.addRealtimeConsumer( this );
//		updateTimeLabel();
    }

    public void stopListening()
    {
//		transport.removeRealtimeConsumer( this );
    }
    
// ---------------- RealtimeConsumer interface ---------------- 

//	/**
//	 *  Requests 15 fps notification (no data block requests).
//	 *  This is used to update the timeline position label during transport
//	 *  playback.
//	 */
//	public RealtimeConsumerRequest createRequest( RealtimeContext context )
//	{
//		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
//		// 15 fps is enough for text update
//		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 15 );
//		request.notifyTicks		= true;
//		request.notifyOffhand	= true;
//		return request;
//	}
//	
//	public void realtimeTick( RealtimeContext context, long currentPos )
//	{
//		lbTime.setTime( new Double( (double) currentPos / context.getSourceRate() ));
//	}
//
//	public void offhandTick( RealtimeContext context, long currentPos )
//	{
//		lbTime.setTime( new Double( (double) currentPos / context.getSourceRate() ));
//		if( !isCueing ) cuePos = currentPos;
//	}
//
//	public void realtimeBlock( RealtimeContext context, boolean even ) {}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
	{
		if( ggLoop.isSelected() ) {
			actionLoop.updateLoop();
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
			rate = doc.timeline.getRate();
			lbTime.setTime( new Double( transport.getCurrentFrame() / rate ));
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
	}
	
    public void timelineScrolled( TimelineEvent e ) {}

	public void timelinePositioned( TimelineEvent e )
	{
		final long pos = doc.timeline.getPosition();
	
		if( !isCueing ) cuePos = pos;
		lbTime.setTime( new Double( pos / rate ));
	}

// ---------------- TransportListener interface ---------------- 

	public void transportStop( Transport transport, long pos )
	{
		ggPlay.setSelected( false );
		if( isCueing ) {
			cuePos = pos;
			cueTimer.restart();
		}
		playTimer.stop();
	}
	
	public void transportPlay( Transport transport, long pos, double rate )
	{
		ggPlay.setSelected( true );
		cueTimer.stop();
		playTimer.restart();
	}
	
	public void transportQuit( Transport transport )
	{
		cueTimer.stop();
		playTimer.stop();
	}
	
	public void transportPosition( Transport transport, long pos, double rate ) {}
	public void transportReadjust( Transport transport, long pos, double rate ) {}

// ---------------- Disposable interface ---------------- 

	public void dispose()
	{
		playTimer.stop();
	}

// ---------------- actions ---------------- 

	private class actionGoToTimeClass
//	extends KeyedAction
	extends AbstractAction
	{
		private Param		value	= null;
		private ParamSpace	space	= null;
	
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
	public void actionPerformed( ActionEvent e )
	{
		ggPosition.setValue( new Param( doc.timeline.getPosition(), ParamSpace.TIME | ParamSpace.SMPS ));	// XXX sync
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

			result = JOptionPane.showOptionDialog( BasicWindowHandler.getWindowAncestor( lbTime ), msgPane,
				app.getResourceString( "inputDlgGoToTime" ),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );

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
	
	private class actionTogglePlayStopClass
//	extends KeyedAction
	extends AbstractAction
	{
//		private actionTogglePlayStopClass( KeyStroke stroke )
//		{
//			super( stroke );
//		}

//		protected void validActionPerformed( ActionEvent e )
		public void actionPerformed( ActionEvent e )
		{
			if( transport.isRunning() ) {
				ggStop.doClick();
			} else {
				ggPlay.doClick();
			}
		}
	} // class actionTogglePlayStopClass


	private class actionPlaySelectionClass
	extends AbstractAction
	{
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

	private static class actionCueClass
	extends AbstractAction
	{
		private final boolean			onOff;
		private final AbstractButton	b;
	
		private actionCueClass( AbstractButton b, boolean onOff )
		{
			this.onOff	= onOff;
			this.b		= b;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			final ButtonModel bm = b.getModel();
			if( bm.isPressed() != onOff ) bm.setPressed( onOff );
			if( bm.isArmed()   != onOff ) bm.setArmed(   onOff );
		}
	} // class actionCueClass
		
	private class actionLoopClass
	extends AbstractAction
	{	
		private actionLoopClass()
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
		
		private void updateLoop()
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
		private CueListener( AbstractButton b, int step )
		{
			bm			= b.getModel();
			this.step	= step;
		}

		public void stateChanged( ChangeEvent e )
		{
			if( isCueing && !bm.isArmed() ) {
				isCueing	= false;
				cueTimer.stop();
				if( transportWasRunning ) {
					transport.play( 1.0f );
				}
			} else if( !isCueing && bm.isArmed() ) {
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
}