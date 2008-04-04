/*
 *  ObserverPalette.java
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
 *		12-May-05	created from de.sciss.meloncillo.gui.ObserverPalette
 *		02-Aug-05	confirms to new document handler
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DocumentListener;
import de.sciss.app.DynamicListening;
import de.sciss.common.AppWindow;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.timeline.TimelineEvent;
import de.sciss.eisenkraut.timeline.TimelineListener;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.ParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.io.Span;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  The <code>ObserverPalette</code> is a
 *  GUI component that displays context sensitive
 *  data and is closely linked to the user's
 *  mouse activities. It contains tabbed panels
 *  for general cursor information and information
 *  concerning receivers, transmitters and the
 *  timeline. Depending on the tab, the data is
 *  presented in editable text or number fields.
 *  <p>
 *  The cursor info pane is 'passive' because in this way
 *  it is easily expandible to display data of new
 *  components. The interested components are hence
 *  resonsible for calling <code>showCursorInfo</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class ObserverPalette
extends AppWindow
implements	ParamField.Listener, TimelineListener, DynamicListening, DocumentListener,
			ActionListener, SwingConstants
{
	private Session						doc					= null;
	
	private JLabel[]					lbCursorInfo;
//	private final NumberField			ggTimelineStart, ggTimelineStop;
	private final ParamField			ggTimelineStart, ggTimelineStop, ggTimelineLen;
	private final DefaultUnitTranslator	timeTrans;
	private final JTabbedPane			ggTabPane;
	private final LockButton			ggLockStop, ggLockLen;

	public static final int				CURSOR_TAB			= 0;
	public static final int				TIMELINE_TAB		= 1;
	
	public static final int				NUM_CURSOR_ROWS		= 5;

	/**
	 *  Constructs a new <code>ObserverPalette</code>
	 */
	public ObserverPalette()
	{
		super( PALETTE );
	
		final Application app = AbstractApplication.getApplication();
	
//		super( AbstractApplication.getApplication().getResourceString( "paletteObserver" ), HORIZONTAL, false );
		setTitle( app.getResourceString( "paletteObserver" ));
		setResizable( false );
		
		final Container		cp		= getContentPane();
		JPanel				c;
		SpringPanel			p;
		JLabel				lb;
		GridBagLayout		lay;
		GridBagConstraints  con;
//		JTextField			ggName;

		ggTabPane = new JTabbedPane();
//        HelpGlassPane.setHelp( ggTabPane, "ObserverPalette" );
        ggTabPane.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT );

		// ----- cursor tab ------
		c				= new JPanel();
		lay				= new GridBagLayout();
		con				= new GridBagConstraints();
		con.insets		= new Insets( 2, 2, 2, 2 );
		c.setLayout( lay );
		con.weightx		= 1.0;
		con.gridwidth   = GridBagConstraints.REMAINDER;
		lbCursorInfo	= new JLabel[ NUM_CURSOR_ROWS ];
		for( int i = 0; i < NUM_CURSOR_ROWS; i++ ) {
			lb			= new JLabel();
			lay.setConstraints( lb, con );
			c.add( lb );
			lbCursorInfo[i] = lb;
		}
		ggTabPane.addTab( app.getResourceString( "observerCursor" ), null, c, null );
//        HelpGlassPane.setHelp( c, "ObserverCursor" );
        
		// ----- timeline tab ------
		timeTrans		= new DefaultUnitTranslator();
		
		p				= new SpringPanel( 4, 2, 4, 2 );
		lb				= new JLabel( app.getResourceString( "observerStart" ), RIGHT );
		p.gridAdd( lb, 1, 0 );
		ggTimelineStart	= new ParamField( timeTrans );
		ggTimelineStart.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineStart.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineStart.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineStart.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineStart.addListener( this );
		p.gridAdd( ggTimelineStart, 2, 0 );
		lb.setLabelFor( ggTimelineStart );
		
		ggLockStop		= new LockButton( true );
		ggLockStop.addActionListener( this );
		p.gridAdd( ggLockStop, 0, 1 );
		lb				= new JLabel( app.getResourceString( "observerStop" ), RIGHT );
		p.gridAdd( lb, 1, 1 );
		ggTimelineStop	= new ParamField( timeTrans );
		ggTimelineStop.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineStop.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineStop.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineStop.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineStop.addListener( this );
		p.gridAdd( ggTimelineStop, 2, 1 );
		lb.setLabelFor( ggTimelineStop );

		ggLockLen		= new LockButton( true );
		ggLockLen.addActionListener( this );
		p.gridAdd( ggLockLen, 0, 2 );
		lb				= new JLabel( app.getResourceString( "observerLen" ), RIGHT );
		p.gridAdd( lb, 1, 2 );
		ggTimelineLen	= new ParamField( timeTrans );
		ggTimelineLen.addSpace( ParamSpace.spcTimeHHMMSS );
		ggTimelineLen.addSpace( ParamSpace.spcTimeSmps );
		ggTimelineLen.addSpace( ParamSpace.spcTimeMillis );
		ggTimelineLen.addSpace( ParamSpace.spcTimePercentR );
		ggTimelineLen.addListener( this );
		p.gridAdd( ggTimelineLen, 2, 2 );
		lb.setLabelFor( ggTimelineLen );
		
//		p.gridAdd( new JPanel( new BorderLayout() ), 1, 3 );
		p.makeCompactGrid( false, false );
		ggTabPane.addTab( app.getResourceString( "observerTimeline" ), null, p, null );
//        HelpGlassPane.setHelp( c, "ObserverTimeline" );
        
		cp.add( BorderLayout.CENTER, ggTabPane );
		
		AbstractWindowHandler.setDeepFont( ggTabPane );
		
		// --- Listener ---
//		new DynamicAncestorAdapter( this ).addTo( rp );
        addDynamicListening( this );
		
		app.getDocumentHandler().addDocumentListener( this );

		addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				dispose();
			}
		});
		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE ); // window listener see above!
		
		setPreferredSize( new Dimension( 272, 180 ));
		
		init();
		app.addComponent( Main.COMP_OBSERVER, this );
	}
	
	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected boolean restoreVisibility()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.95f, 0.8f );
	}

	public void dispose()
	{
		AbstractApplication.getApplication().removeComponent( Main.COMP_OBSERVER );
		super.dispose();
	}
	
	/**
	 *  Switch the display to a specific
	 *  tab, where zero is the cursor info tab.
	 *
	 *  @param  tabIndex	the index of the tab to show
	 */
	public void showTab( int tabIndex )
	{
		if( ggTabPane.getSelectedIndex() != tabIndex ) {
			ggTabPane.setSelectedIndex( tabIndex );
		}
	}
	
	public int getShownTab()
	{
		return ggTabPane.getSelectedIndex();
	}

	/**
	 *  Display information in the cursor pane.
	 *  It's up to the caller what kind of information
	 *  is displayed. This method simply displays
	 *  up to four lines of text. This method doesn't
	 *  switch the display to the cursor pane.
	 *
	 *  @param  info	an array of zero to four strings
	 *					which will be displayed in the
	 *					cursor tab.
	 */
	public void showCursorInfo( String[] info )
	{
		int i, j;
		
		j = Math.min( NUM_CURSOR_ROWS, info.length );
		
		for( i = 0; i < j; i++ ) {
			lbCursorInfo[i].setText( info[i] );
		}
		for( ; i < NUM_CURSOR_ROWS; i++ ) {
			lbCursorInfo[i].setText( "" );
		}
	}

	// attempts shared on DOOR_TIME
	private void updateTimeline()
	{
		if( doc != null ) {

//			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//			try {
				final Span	span	= doc.timeline.getSelectionSpan();
				final double rate	= doc.timeline.getRate();

				timeTrans.setLengthAndRate( doc.timeline.getLength(), rate );
				ggTimelineStart.setValue( new Param( span.getStart(), ParamSpace.spcTimeSmps.unit ));
				ggTimelineStop.setValue( new Param( span.getStop(), ParamSpace.spcTimeSmps.unit ));
				ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
				if( !ggTimelineStart.isEnabled() ) ggTimelineStart.setEnabled( true );
				if( !ggTimelineStop.isEnabled() )  ggTimelineStop.setEnabled( true );
				if( !ggTimelineLen.isEnabled() )  ggTimelineLen.setEnabled( true );
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIME );
//			}
			
		} else {
			
// ZZZ
//			ggTimelineStart.setNumber( new Double( Double.NaN ));
//			ggTimelineStop.setNumber( new Double( Double.NaN ));
			ggTimelineStart.setEnabled( false );
			ggTimelineStop.setEnabled( false );
			ggTimelineLen.setEnabled( false );
		}
	}

// ---------------- ActionListener interface ---------------- 

	public void actionPerformed( ActionEvent e )
	{
		if( e.getSource() == ggLockStop ) {
			if( ggLockStop.isLocked() && ggLockLen.isLocked() ) {
				ggLockLen.setLocked( false );
			}		
		} else if( e.getSource() == ggLockLen ) {
			if( ggLockStop.isLocked() && ggLockLen.isLocked() ) {
				ggLockStop.setLocked( false );
			}		
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc = (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
		if( doc != null ) {
			doc.timeline.addTimelineListener( this );
//			borrowMenuBar( doc.getFrame() );
		}
		updateTimeline();
    }

    public void stopListening()
    {
		if( doc != null ) {
//			borrowMenuBar( null );
			doc.timeline.removeTimelineListener( this );
		}
    }

// ---------------- ParamListener interface ---------------- 

	public void paramValueChanged( ParamField.Event e )
	{
		long	n		= (long) e.getTranslatedValue( ParamSpace.spcTimeSmps ).val;
		long	n2;
		Span	span;
	
		if( (e.getSource() == ggTimelineStart) || (e.getSource() == ggTimelineStop) ||
			(e.getSource() == ggTimelineLen) ) {

			span	= doc.timeline.getSelectionSpan();
			
			// ----- start was adjusted -----
			if( e.getSource() == ggTimelineStart ) {
				if( ggLockLen.isLocked() ) {
					n2	= n + span.getLength();
					if( n2 > doc.timeline.getLength() ) {
						n2	= doc.timeline.getLength();
						n	= n2 - span.getLength();
						ggTimelineStart.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n, n2 );
					ggTimelineStop.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2 = span.getStop();
					if( n > n2 ) {
						n = n2;
						ggTimelineStart.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n, n2 );
					ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
				}
			// ----- stop was adjusted -----
			} else if( e.getSource() == ggTimelineStop ) {
				if( ggLockLen.isLocked() ) {
					n2		= n - span.getLength();
					if( n2 < 0 ) {
						n2	= 0;
						n	= n2 + span.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					if( n > doc.timeline.getLength() ) {
						n	= doc.timeline.getLength();
						n2	= n - span.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n );
					ggTimelineStart.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2		= span.getStart();
					if( n < n2 ) {
						n	= n2;
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					if( n > doc.timeline.getLength() ) {
						n	= doc.timeline.getLength();
						ggTimelineStop.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n );
					ggTimelineLen.setValue( new Param( span.getLength(), ParamSpace.spcTimeSmps.unit ));
				}
			// ----- len was adjusted -----
			} else {
				if( ggLockStop.isLocked() ) {
					n2		= span.getStop() - n;
					if( n2 < 0 ) {
						n2	= 0;
						n	= span.getStop();
						ggTimelineLen.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2, n2 + n );
					ggTimelineStart.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				} else {
					n2		= span.getStart() + n;
					if( n2 > doc.timeline.getLength() ) {
						n2	= doc.timeline.getLength();
						n	= n2 - span.getStart();
						ggTimelineLen.setValue( new Param( n, ParamSpace.spcTimeSmps.unit ));
					}
					span	= new Span( n2 - n, n2 );
					ggTimelineStop.setValue( new Param( n2, ParamSpace.spcTimeSmps.unit ));
				}
			}
			doc.timeline.editSelect( this, span );
//				doc.getUndoManager().addEdit( TimelineVisualEdit.select( this, doc, span ));
		}
	}

	public void paramSpaceChanged( ParamField.Event e )
	{
		ggTimelineStart.setSpace( e.getSpace() );
		ggTimelineStop.setSpace( e.getSpace() );
		ggTimelineLen.setSpace( e.getSpace() );
	}
    
// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		if( e.getSource() != this ) {
//			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//			try {
				showTab( doc.timeline.getSelectionSpan().isEmpty() ? CURSOR_TAB : TIMELINE_TAB );
				updateTimeline();
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIME );
//			}
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
		updateTimeline();
	}
    
	public void timelinePositioned( TimelineEvent e ) { /* ignored */ }
    public void timelineScrolled( TimelineEvent e ) { /* ignored */ }

// ---------------- DocumentListener interface ---------------- 

	public void documentFocussed( de.sciss.app.DocumentEvent e )
	{
		if( doc != null ) doc.timeline.removeTimelineListener( this );
		doc	= (Session) e.getDocument();
		if( doc != null ) {
			doc.timeline.addTimelineListener( this );
//			borrowMenuBar( doc.getFrame() );
//		} else {
//			borrowMenuBar( null );
		}
		updateTimeline();
	}

	public void documentAdded( de.sciss.app.DocumentEvent e ) { /* ignore */ }
	public void documentRemoved( de.sciss.app.DocumentEvent e ) { /* ignore */ }

// ---------------- internal classes ---------------- 

//	private class sessionCollectionListener
//	implements ActionListener, SessionCollection.Listener, DynamicListening
//	{
//		private final LockManager lm;
//		private final int doors;
//		private final int tab;
//		private final SessionCollection scSel, scAll;
//		private final JTextField ggName;
//		private final SessionObjectTable ggTable;
//	
//		private sessionCollectionListener( SessionCollection scSel, SessionCollection scAll,
//										   LockManager lm, int doors, int tab, JTextField ggName,
//										   SessionObjectTable ggTable, JComponent ancestor )
//		{
//			this.scSel	= scSel;
//			this.scAll	= scAll;
//			this.lm		= lm;
//			this.doors	= doors;
//			this.tab	= tab;
//			this.ggName	= ggName;
//			this.ggTable= ggTable;
//			
//			ggName.addActionListener( this );
//			new DynamicAncestorAdapter( this ).addTo( ancestor );
//		}
//	
//		public void sessionCollectionChanged( SessionCollection.Event e )
//		{
//			try {
//				lm.waitShared( doors );
//				showTab( scSel.isEmpty() ? CURSOR_TAB : tab );   // intelligently switch between the tabs
//				ggTable.setObjects( scSel.getAll() );
//				updateFields( e );
//			}
//			finally {
//				lm.releaseShared( doors );
//			}
//		}
//		
//		public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//
//		public void sessionObjectChanged( SessionCollection.Event e )
//		{
//			if( e.getModificationType() == SessionObject.OWNER_RENAMED ) {
//				try {
//					lm.waitShared( doors );
//					updateFields( e );
//				}
//				finally {
//					lm.releaseShared( doors );
//				}
//			}
//		}
//
//		// to be called with shared lock on 'doors'!
//		private void updateFields( SessionCollection.Event e )
//		{
//			SessionObject	so;
//			int				numObj		= scSel.size();
//			String			name;
//
//			if( numObj >= 1 ) {
//				so		= (SessionObject) scSel.get( 0 );
//				name	= numObj == 1 ? so.getName() : Main.getResourceString( "observerMultiSelection" );
//			} else {
//				name	= Main.getResourceString( "observerNoSelection" );
//			}
//			ggName.setText( name );
//		}
//
//		public void actionPerformed( ActionEvent e )
//		{
//			SessionObject		so;
//			int					i, num;
//			CompoundEdit		edit;
//			String				name;
//			java.util.List		coll, coll2;
//			Object[]			args;
//
//			if( e.getSource() == ggName ) {
//				if( !lm.attemptExclusive( doors, 250 )) return;
//				try {
//					coll	= scSel.getAll();
//					coll2	= scAll.getAll();
//					num		= coll.size();
//					if( num == 0 ) return;
//					
//					args	= new Object[ 3 ];
//					
//					coll2.removeAll( coll );
//					edit	= new BasicSyncCompoundEdit( lm, doors );
//					name	= ggName.getText();
//					if( num == 1 ) {
//						if( SessionCollection.findByName( coll2, name ) != null ) {
//							Session.makeNamePattern( name, args );
//							name = SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
//						}
//						edit.addEdit( new EditSetSessionObjectName( this, doc, (SessionObject) coll.get( 0 ),
//																	name, doors ));
//					} else {
//						Session.makeNamePattern( name, args );
//						for( i = 0; i < num; i++ ) {
//							so		= (SessionObject) coll.get( i );
//							name	= SessionCollection.createUniqueName( Session.SO_NAME_PTRN, args, coll2 );
//							edit.addEdit( new EditSetSessionObjectName( this, doc, so, name, doors ));
//							coll2.add( so );
//						}
//					}
//					edit.end();
//					root.undo.addEdit( edit );
//				}
//				finally {
//					lm.releaseExclusive( doors );
//				}
//			}
//		}
//
//		public void startListening()
//		{
//			scSel.addListener( this );
//		}
//
//		public void stopListening()
//		{
//			scSel.removeListener( this );
//		}
//	} // class sessionCollectionListener
}