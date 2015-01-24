/*
 *  BlendingAction.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
//import java.util.ArrayList;
//import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
//import javax.swing.event.ListDataEvent;
//import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import de.sciss.eisenkraut.io.BlendContext;
import de.sciss.eisenkraut.timeline.Timeline;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.GraphicsHandler;
import de.sciss.common.AppWindow;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.DefaultUnitViewFactory;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpButton;
import de.sciss.gui.MultiStateButton;
import de.sciss.gui.ParamField;
import de.sciss.gui.PrefParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *	A class implementing the <code>Action</code> interface
 *	which deals with the blending setting. Each instance
 *	generates a toggle button suitable for attaching to a tool bar;
 *	this button reflects the blending preferences settings and
 *	when alt+pressed will prompt the user to alter the blending settings.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 23-Mar-08
 *
 *	@todo		gui panels should be destroyed / disposed
 *				because otherwise different BlendingAction instances
 *				for different docs all create their own panels
 *
 *	@todo		should not be a subclass of AbstractAction
 *
 *	@todo		close action should be called whenenver popup disappears,
 *				and duplicates should be filtered out!
 */
public class BlendingAction
extends AbstractAction
// implements LaterInvocationManager.Listener
{
	public static final int						MAX_RECENTNUM	= 5;
	
	public static final String					DEFAULT_NODE	= "blending";
	
	private static final String					KEY_ACTIVE		= "active";
	public static final String					KEY_DURATION	= "duration";
	private static final String					NODE_RECENT		= "recent";

	protected static final Param				DEFAULT_DUR		=
		new Param( 100.0, ParamSpace.ABS | ParamSpace.TIME | ParamSpace.MILLI | ParamSpace.SECS );


	private static PrefComboBoxModel			pcbm			= null;
//	private PrefComboBoxModel					pcbm			= null;
	
//	private final JToggleButton					b;
	protected final MultiStateButton			b;
	protected final Preferences					prefs;
	protected PrefParamField					ggBlendTime;
	protected CurvePanel						ggCurvePanel;
	private SpringPanel							ggSettingsPane;
	private JComponent							bottomPanel;
//	private JDialog								dlg				= null;
	private	static final DefaultUnitTranslator	ut				= new DefaultUnitTranslator();
	protected static final DefaultUnitViewFactory uvf			= new DefaultUnitViewFactory();
	
	private final CurvePanel.Icon				curveIcon;

//	private boolean								guiCreated		= false;
	
	private final PopupTriggerMonitor			popMon;
	private JPopupMenu							popup			= null;
	private AppWindow							palette			= null;
//	private boolean								listening		= false;
	
	private boolean								active;
	
	private final Timeline						timeline;
	
//	private List								collListeners	= new ArrayList();
	
	protected final Settings					current;

	/**
	 *	Creates a new instance of an action
	 *	that tracks blending changes
	 *
	 *	@param	prefs	node of preferences tree (usually DEFAULT_NODE)
	 */
	public BlendingAction( Timeline timeline, Preferences prefs )
	{
		super();
		this.prefs		= prefs != null ? prefs : AbstractApplication.getApplication().getUserPrefs().node( DEFAULT_NODE );
		this.timeline	= timeline;

//		b				= new JToggleButton( this );
		b				= new MultiStateButton();
		b.setNumColumns( 8 );
		b.setAutoStep( false );
		b.addActionListener( this );
		b.addItem( "", null, new Color( 0, 0, 0, 0 ));
		b.addItem( "", null, new Color( 0xFF, 0xFA, 0x9D ));
//		uvf				= new DefaultUnitViewFactory();
//		GraphicsUtil.setToolIcons( b, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_BLENDING ));
		curveIcon		= new CurvePanel.Icon( createBasicCurves() );
//		curveIcon.update( this.prefs );
//		b.setIcon( curveIcon );
		b.setItemIcon( 0, curveIcon );
		b.setItemIcon( 1, curveIcon );
		
//		new DynamicAncestorAdapter( new DynamicPrefChangeManager( this.prefs,
//			new String[] { KEY_ACTIVE, KEY_DURATION }, this )).addTo( b );

		popMon = new PopupTriggerMonitor( b );
		popMon.addListener( new PopupTriggerMonitor.Listener() {
			public void componentClicked( PopupTriggerMonitor m ) { /* empty */ }
			
			public void popupTriggered( PopupTriggerMonitor m )
			{
				b.getModel().setArmed( false );
				showPopup( m.getComponent(), 0, m.getComponent().getHeight() );
			}
		});
		
		active				= this.prefs.getBoolean( KEY_ACTIVE, false );
		current				= Settings.fromPrefs( this.prefs );
//		current.duration	= Param.fromPrefs( this.prefs, KEY_DURATION, null );
//		current.ctrlPt		= CurvePanel.getControlPoints( this.prefs );
//		curveIcon.update( current.ctrlPt[ 0 ], current.ctrlPt[ 1 ]);
		updateButton();
	}
	
	public Preferences getPreferences() { return prefs; }
	private Preferences getRecentPreferences() { return prefs.node( NODE_RECENT ); }
	
//	public void addRecentListener( ListDataListener l )
//	{
//		collListeners.add( l );
//	}
//
//	public void removeRecentListener( ListDataListener l )
//	{
//		collListeners.remove( l );
//	}

	private void createBlendPan( boolean popped )
	{
		destroyBlendPan();

//		if( guiCreated ) return;
		createGadgets( 0 );
		final Font fnt = AbstractApplication.getApplication().getGraphicsHandler()
			.getFont( GraphicsHandler.FONT_SYSTEM | GraphicsHandler.FONT_SMALL );
		GUIUtil.setDeepFont( ggSettingsPane, fnt );
		GUIUtil.setDeepFont( bottomPanel, fnt );
		ggBlendTime.setCycling( popped ); // cannot open popup menu in another popup menu!
		if( palette != null ) {
			palette.getContentPane().add( ggSettingsPane, BorderLayout.CENTER );
			palette.getContentPane().add( bottomPanel, BorderLayout.SOUTH );
			palette.revalidate();
		} else {
			popup.add( ggSettingsPane, BorderLayout.CENTER );
			popup.add( bottomPanel, BorderLayout.SOUTH );
			popup.revalidate();
		}
//		guiCreated	= true;
	}

	private void destroyBlendPan()
	{
		if( ggSettingsPane != null ) {
			ggSettingsPane.getParent().remove( ggSettingsPane );
			ggSettingsPane = null;
		}
		if( bottomPanel != null ) {
			bottomPanel.getParent().remove( bottomPanel );
			bottomPanel = null;
		}
	}

	private void createGadgets( int flags )
	{
		bottomPanel		= createBottomPanel( flags );
//		ggSettingsPane	= new JScrollPane( JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
//										   JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		ggSettingsPane	= new SpringPanel( 4, 2, 4, 2 );
//		ggBlendTime		= new PrefNumberField( 0, NumberSpace.genericDoubleSpace, "sec." );
		ut.setLengthAndRate( 0, timeline.getRate() );
		ggBlendTime		= new PrefParamField( ut );
		ggBlendTime.addSpace( ParamSpace.spcTimeMillis );
		ggBlendTime.addSpace( ParamSpace.spcTimeSmps );
		ggBlendTime.setPreferences( prefs, KEY_DURATION );
		ggBlendTime.setReadPrefs( false );
		if( current.duration != null ) {
//System.err.println( "setDuration" );
			ggBlendTime.setValueAndSpace( current.duration );
		}
		ggBlendTime.addListener( new ParamField.Listener() {
			public void paramSpaceChanged( ParamField.Event e )
			{
				paramValueChanged( e );
			}
			
			public void paramValueChanged( ParamField.Event e )
			{
				if( !e.isAdjusting() ) {
					current.duration = ggBlendTime.getValue();
					updateButtonText();
				}
			}
		});

		ggCurvePanel	= new CurvePanel( createBasicCurves(), this.prefs );
//System.err.println( "setControlPoints" );
		ggCurvePanel.setControlPoints( current.ctrlPt[ 0 ], current.ctrlPt[ 1 ]);
		ggCurvePanel.setPreferredSize( new Dimension( 162, 162 ));	
		ggCurvePanel.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final Point2D[] pt = ggCurvePanel.getControlPoints();
				current.ctrlPt[ 0 ].setLocation( pt[ 0 ]);
				current.ctrlPt[ 1 ].setLocation( pt[ 1 ]);
				updateButtonIcon();
			}
		});

		ggSettingsPane.gridAdd( ggBlendTime, 0, 0 );
		ggSettingsPane.gridAdd( ggCurvePanel, 0, 1 );
		ggSettingsPane.makeCompactGrid();
	}

	public void showPopup( Component invoker, int x, int y )
	{
		createPopup();
//		startListening();
		popup.show( invoker, x, y );
		// XXX this is necessary unfortunately
		// coz the DynamicAncestorAdapter doesn't seem
		// to work with the popup menu ...
		ggBlendTime.startListening();
//		if( beginDragging ) pan.beginDragging();
	}

	private void createPopup()
	{
		if( popup != null ) return;
		if( palette != null ) destroyPalette();
		
		popup	= new JPopupMenu();
		createBlendPan( true );
		popup.addPopupMenuListener( new PopupMenuListener() {
			public void popupMenuCanceled( PopupMenuEvent e )
			{
				stopAndDispose();
			}
			
			public void popupMenuWillBecomeInvisible( PopupMenuEvent e ) { /* empty */ }
			public void popupMenuWillBecomeVisible( PopupMenuEvent e ) { /* empty */ }
		});
//popup.setCursor( new java.awt.Cursor( java.awt.Cursor.CROSSHAIR_CURSOR ));
	}
	
	protected void stopAndDispose()
	{
		// XXX this is necessary unfortunately
		// coz the DynamicAncestorAdapter doesn't seem
		// to work with the popup menu ...
		ggBlendTime.stopListening();
		dispose();
	}

	public void showPalette()
	{
		createPalette();
//		startListening();
		palette.setVisible( true );
		palette.toFront();
	}

	private void destroyPalette()
	{
		if( palette == null ) return;
		
		palette.dispose();
		palette = null;
	}

	private void destroyPopup()
	{
		if( popup == null ) return;
		
		popup.setVisible( false );
		popup = null;
	}

	protected static CubicCurve2D[] createBasicCurves()
	{
		return new CubicCurve2D[] {
			new CubicCurve2D.Double( 0.0, 1.0, 0.5, 0.0, 0.5, 0.0, 1.0, 0.0 ),
			new CubicCurve2D.Double( 0.0, 0.0, 0.5, 0.0, 0.5, 0.0, 1.0, 1.0 )
		};
	}
	
	/**
	 *	Returns the toggle button
	 *	which is connected to this action.
	 *
	 *	@return	a toggle button which is suitable for tool bar display
	 */
//	public JToggleButton getButton()
	public AbstractButton getButton()
	{
		return b;
	}
	
	public JComboBox getComboBox()
	{
		final ComboBoxModel		model			= getComboBoxModel();
		final ComboBoxModel		emptyCBM		= new DefaultComboBoxModel();
		final MultiStateButton	button			= b;
		final JComboBox			ggBlend			= new JComboBox(); // ( pcbm );
		final ListCellRenderer	blendRenderer	= getComboBoxRenderer();
		
		ggBlend.setEditable( true );
		ggBlend.setEditor( new ComboBoxEditor() {
			public Component getEditorComponent() { return button; }

			public void setItem( Object o )
			{
//System.err.println( "setItem " + o );
				if( o != null ) {
					current.setFrom( (Settings) o );
					current.toPrefs( prefs );
					updateButton();
				}
			}
			
			public Object getItem() { return current; }
			public void selectAll() { /* ignore */ }
			public void addActionListener(ActionListener l) { /* ignore */ }
			public void removeActionListener(ActionListener l) { /* ignore */ }
		});
		ggBlend.setRenderer( blendRenderer );
		button.setFocusable( false );
		ggBlend.setFocusable( false );
		GUIUtil.constrainSize( ggBlend, 120, 26 );	// 110 XXX (140 for MetalLAF)
		ggBlend.setOpaque( false );
		
		// this is _crucial_ because since pcbm is global
		// and the combobox registers with it, we have a
		// memory leak otherwise!!
		new DynamicAncestorAdapter( new DynamicListening() {
			public void startListening()
			{
				ggBlend.setModel( model );
			}
			
			public void stopListening()
			{
				ggBlend.setModel( emptyCBM );
			}
		}).addTo( ggBlend );
		
		return ggBlend;
	}

	private void updateButtonState()
	{
//		b.setSelected( prefs.getBoolean( KEY_ACTIVE, false ));
//		b.setSelectedIndex( prefs.getBoolean( KEY_ACTIVE, false ) ? 1 : 0 );
		b.setSelectedIndex( active ? 1 : 0 );
	}
	
	protected void updateButton()
	{
		updateButtonState();
		updateButtonText();
		updateButtonIcon();
	}

	protected void updateButtonText()
	{
//		final Param		p;
		final Param		p		= current.duration;
		final Object	view;
		final String	text;
		
//		p = Param.fromPrefs( prefs, KEY_DURATION, null );
		if( p != null ) {
			view = uvf.createView( p.unit );
			
			if( view instanceof Icon ) {
				// XXX hmmm. should use composite icon
			} else {
//				b.setText( String.valueOf( (int) p.val ) + " " + view.toString() );
				text = String.valueOf( (int) p.val ) + " " + view.toString();
				b.setItemText( 0, text );
				b.setItemText( 1, text );
			}
			if( ggBlendTime != null ) ggBlendTime.setValueAndSpace( p );
		}
	}

	protected void updateButtonIcon()
	{
		curveIcon.update( current.ctrlPt[ 0 ], current.ctrlPt[ 1 ]);
		if( ggCurvePanel != null ) ggCurvePanel.setControlPoints( current.ctrlPt[ 0 ], current.ctrlPt[ 1 ]);
		b.repaint();
	}

	private JComponent createBottomPanel( int flags )
	{
		final JPanel	panel;
		final JButton	ggClose;

		panel		= new JPanel( new FlowLayout( FlowLayout.TRAILING, 4, 2 ));
		ggClose		= new JButton( new CloseAction( getResourceString( "buttonClose" )));
		GUIUtil.createKeyAction( ggClose, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ));
		ggClose.setFocusable( false );
		panel.add( ggClose );
		panel.add( new HelpButton( "Blending" ));
		panel.add( CoverGrowBox.create() );

		return panel;
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
	public void actionPerformed( ActionEvent e )
	{
		if( (e.getModifiers() & ActionEvent.ALT_MASK) != 0 ) {
			showPalette();
		} else {
//			prefs.putBoolean( KEY_ACTIVE, b.isSelected() );
			active = b.getSelectedIndex() == 0;
			prefs.putBoolean( KEY_ACTIVE, active );
			updateButtonState();
		}
	}
	
	private void createPalette()
	{
		if( palette != null ) return;
		if( popup != null ) destroyPopup();

		final Application	app			= AbstractApplication.getApplication();
	
		palette = new AppWindow( AbstractWindow.PALETTE );
		palette.setTitle( app.getResourceString( "inputDlgSetBlendSpan" ));
		createBlendPan( false );
   		palette.getContentPane().add( CoverGrowBox.create(), BorderLayout.SOUTH );
		palette.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		palette.addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				dispose();
			}
		});
		palette.init();
	}

	public void dispose()
	{
//		stopListening();
		destroyPalette();
		destroyPopup();
		destroyBlendPan();
	}
	
	private ComboBoxModel getComboBoxModel()
	{
		if( pcbm == null ) {
			pcbm = new PrefComboBoxModel() {
				public Object dataFromNode( Preferences node )
				{
					return Settings.fromPrefs( node );
				}

				public void dataToNode( Object data, Preferences node )
				{
					((Settings) data).toPrefs( node );
				}
			};
			pcbm.setPreferences( getRecentPreferences() );
		}
		return pcbm;
	}
	
	private ListCellRenderer getComboBoxRenderer()
	{
		return new BlendCBRenderer();
	}
	
	protected void storeRecent()
	{
		if( pcbm == null ) return;
		
		pcbm.setSelectedItem( null );
		
		while( pcbm.getSize() >= MAX_RECENTNUM ) {
			try {
				pcbm.remove( pcbm.getSize() - 1 );
			}
			catch( BackingStoreException e1 ) {
				e1.printStackTrace();
				return;
			}
		}
		pcbm.add( 0, current.duplicate() );
	}
	
//	private void storeRecentXXX()
//	{
//		final Preferences rPrefs = getRecentPreferences();
//		Preferences node1, node2;
//		String[] names;
////		final ListDataEvent lde;
//		int i;
//		try {
//			for( i = 0; i < MAX_RECENTNUM; i++ ) {
//				if( !rPrefs.nodeExists( DEFAULT_NODE + (i+1) )) break;
//			}
//			if( i == MAX_RECENTNUM ) {
//				node2 = rPrefs.node( DEFAULT_NODE + "1" );
//				for( int j = 1; j < MAX_RECENTNUM; j++ ) {
//					node1 = node2;
//					node2 = rPrefs.node( DEFAULT_NODE + (j+1)  );
//					names = node2.childrenNames();
//					for( int k = 0; k < names.length; k++ ) {
//						node1.put( names[ k ], node2.get( names[ k ], null ));
//					}
//				}
//				i--;
//System.err.println( "remove " + node2 );
//				node2.removeNode();
//			}
//			node1 = rPrefs.node( DEFAULT_NODE + (i+1)  );
//System.err.println( "added " + node1 );
//			node1.put( KEY_DURATION, current.duration.toString() );
//			ggCurvePanel.toPrefs( node1 );
//			
////			lde = new ListDataEvent( this, ListDataEvent.CONTENTS_CHANGED, 0, i );
////			for( int j = 0; j < collListeners.size(); j++ ) {
////				((ListDataListener) collListeners.get( j )).contentsChanged( lde );
////			}
//		}
//		catch( BackingStoreException e1 ) {
//			System.err.println( e1 );
//		}
//	}

//	private void startListening()
//	{
//		if( !listening ) {
////			tracks.addListener( scListener );
////			audioPrefs.addPreferenceChangeListener( prefListener );
//			ggBlendTime.fromPrefsString( prefs.get( KEY_DURATION, null ));
//			listening = true;
//		}
//	}
//	
//	private void stopListening()
//	{
//		if( listening ) {
////			tracks.removeListener( scListener );
////			audioPrefs.removePreferenceChangeListener( prefListener );
//			listening = false;
//		}
//	}

//	private void openDialog()
//	{
////		new TestDialog();
//		if( dlg == null ) {
//			final Window w = SwingUtilities.windowForComponent( b );
//			dlg = new JDialog( ((w == null) || !(w instanceof Frame)) ? null : (Frame) w,
//				getResourceString( "inputDlgSetBlendSpan" ), true );
//			final Container cp	= dlg.getContentPane();
//			if( !guiCreated ) {
//				createGUI();
//			}
//			cp.add( ggSettingsPane, BorderLayout.CENTER );
//			cp.add( bottomPanel, BorderLayout.SOUTH );
//			dlg.pack();
//			dlg.setLocationRelativeTo( b );
//		}
//		dlg.setVisible( true );
//		// XXX dispose gui
//		prefs.putBoolean( KEY_ACTIVE, ggBlendTime.getValue().val > 0.0 );
//		updateButtonState();	// prefsChangeEvent not guaranteed!!
//	}
	
//	// o instanceof PreferenceChangeEvent
//	public void laterInvocation( Object o )
//	{
//		final PreferenceChangeEvent	pce = (PreferenceChangeEvent) o;
//		final String				key = pce.getKey();
//			
//		if( key.equals( KEY_ACTIVE )) {
//			updateButtonState();
//		} else if( key.equals( KEY_DURATION )) {
//			updateButtonText();
//		}
//	}
	
//	/**
//	 *	@param	maxLength	the maximum blend length or <code>-1</code> if there is no max
//	 *
//	 *	@todo	pre/post position not yet effective (using 0.5 right now)
//	 */
//	public static BlendContext createBlendContext( Preferences prefs, double rate, long maxPre, long maxPost )
//	{
//		long	blendLen	= 0;
//		long	pre = 0, post = 0;
//		Param	p;
//
//		if( prefs.getBoolean( KEY_ACTIVE, false )) {
//			p = Param.fromPrefs( prefs, KEY_DURATION, null );
//			if( p != null ) {
//				synchronized( ut ) {
//					ut.setLengthAndRate( 0, rate );
//					blendLen = (long) (ut.translate( p, ParamSpace.spcTimeSmps ).val + 0.5);
//					pre = blendLen >> 1;
//					post = blendLen - pre;
//					if( (maxPre >= 0) && (pre > maxPre) ) {
//						pre		= maxPre;
//						post	= pre;	// XXX cheesy
//					}
//					if( (maxPost >= 0) && (post > maxPost) ) {
//						post	= maxPost;
//						pre		= post;
//					}
//				}
//			}
//		}
//		return new BlendContext( pre, post, CurvePanel.getControlPoints( prefs ) );
//	}

	/**
	 *	@param	maxLength	the maximum blend length or <code>-1</code> if there is no max
	 *
	 *	@todo	pre/post position not yet effective (using 0.5 right now)
	 */
	public BlendContext createBlendContext( long maxLeft, long maxRight )
	{
		if( !java.awt.EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		if( !active ) return null;
		
		final long	blendLen;
		final Param	p;

		p = current.duration; // Param.fromPrefs( prefs, KEY_DURATION, null );
		if( p != null ) {
			ut.setLengthAndRate( 0, timeline.getRate() );
			blendLen	= (long) (ut.translate( p, ParamSpace.spcTimeSmps ).val + 0.5);
			if( maxLeft + maxRight > blendLen ) {
				maxLeft	= (long) (maxLeft * (double) blendLen / (maxLeft + maxRight) + 0.5 );
				maxRight= blendLen - maxLeft;
			}
		}
		return new BlendContext( maxLeft, maxRight, CurvePanel.getControlPoints( prefs ) );
	}
	
	private static class Settings
	{
		protected Param				duration;
		protected final Point2D[]	ctrlPt	= new Point2D[] { new Point2D.Double(), new Point2D.Double() };
		
		private Settings() { /* empty */ }
		
		private Settings( Settings orig )
		{
			setFrom( orig );
		}
		
		protected void setFrom( Settings orig )
		{
			duration = orig.duration;
			ctrlPt[ 0 ].setLocation( orig.ctrlPt[ 0 ]);
			ctrlPt[ 1 ].setLocation( orig.ctrlPt[ 1 ]);
		}
		
		protected static Settings fromPrefs( Preferences node )
		{
			final Settings s = new Settings();
			s.duration = Param.fromPrefs( node, KEY_DURATION, DEFAULT_DUR );
			final Point2D[] pt = CurvePanel.getControlPoints( node );
			s.ctrlPt[ 0 ].setLocation( pt[ 0 ]);
			s.ctrlPt[ 1 ].setLocation( pt[ 1 ]);
			return s;
		}
		
		protected Settings duplicate()
		{
			return new Settings( this );
		}
		
		protected void toPrefs( Preferences node )
		{
			node.put( KEY_DURATION, duration.toString() );
			CurvePanel.toPrefs( ctrlPt, node );
		}
		
		public String toString()
		{
			return String.valueOf( duration.val );
		}
	}
	
	private static class BlendCBRenderer
	extends JLabel
	implements ListCellRenderer
	{
		private final Color bgNorm, bgSel, fgNorm, fgSel;
		final CurvePanel.Icon curveIcon;
	
		protected BlendCBRenderer()
		{
			super();
			setOpaque( true );
			bgNorm		= UIManager.getColor( "List.background" );
			bgSel		= UIManager.getColor( "List.selectionBackground" );
			fgNorm		= UIManager.getColor( "List.foreground" );
			fgSel		= UIManager.getColor( "List.selectionForeground" );
			curveIcon	= new CurvePanel.Icon( createBasicCurves() );
			setIcon( curveIcon );
			setBorder( BorderFactory.createEmptyBorder( 2, 2, 2, 2 ));
	    }
		
		public Component getListCellRendererComponent(
			JList list, Object value, int index, boolean isSelected, boolean cellHasFocus )
		{
			final Settings	s		= (Settings) value;
			final Object	view	= uvf.createView( s.duration.unit );
			
			curveIcon.update( s.ctrlPt[0], s.ctrlPt[1] );
			if( view instanceof Icon ) {
				// XXX hmmm. should use composite icon
			} else {
				setText( String.valueOf( (int) s.duration.val ) + " " + view.toString() );
			}
			setBackground( isSelected ? bgSel : bgNorm );
			setForeground( isSelected ? fgSel : fgNorm );
			return this;
		}
	} 

	private class CloseAction
	extends AbstractAction
	{
		protected CloseAction( String text )
		{
			super( text );
		}

		public void actionPerformed( ActionEvent e )
		{
			stopAndDispose();
			storeRecent();
		}
	}
	
	////////////////////////////////////////// TEST

//	private class TestDialog
//	{
//		private TestDialog()
//		{
//			JDialog dlg = new JDialog( (Frame) null,
//				getResourceString( "inputDlgSetBlendSpan" ), true );
//			final Container cp	= dlg.getContentPane();
//
//			cp.add( new TestPanel(), BorderLayout.CENTER );
//
//			dlg.pack();
//			dlg.setLocationRelativeTo( b );
//			dlg.setVisible( true );
//			// XXX dispose gui
//		}
//	}
	
//	private class TestPanel
//	extends JComponent
//	{
//Paint	pntMarkStick= new Color( 0x31, 0x50, 0x4D, 0x7F );
//Stroke	strkStick	= new BasicStroke( 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
//		1.0f, new float[] { 4.0f, 4.0f }, 0.0f );
//
//		private TestPanel()
//		{
//			super();
//		}
//		
//		public void paintComponent( Graphics g )
//		{
//			super.paintComponent( g );
//			
//			final Graphics2D		g2				= (Graphics2D) g;
//			final int				currentWidth	= getWidth(); // - insets.left - insets.right;
//			final int				currentHeight	= getHeight(); // - insets.top - insets.bottom;
//			final AffineTransform	atOrig			= g2.getTransform();
//			final Stroke			strkOrig		= g2.getStroke();
//			final Shape				clipOrig		= g2.getClip();
//
////			double trnsX, trnsY;
//			
//			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
////			g2.translate( insets.left, insets.top );
//
//AffineTransform atSchoko = AffineTransform.getScaleInstance( currentWidth / 7f, currentHeight / 6f );
//AffineTransform atSchoko2 = new AffineTransform( atSchoko );
//AffineTransform atSchoko3 = new AffineTransform( atSchoko );
//atSchoko2.translate( 0f, 3f );
//atSchoko3.translate( -3f, 3f );
//
//Area a = new Area( new Rectangle2D.Float( 0f, 0f, 0.95f, 6f ));
//a.add( new Area( new Rectangle2D.Float( 1f, 0f, 5f, 3f )));
//a.add( new Area( new Rectangle2D.Float( 6.05f, 0f, 0.95f, 3f )));
//a.add( new Area( new Rectangle2D.Float( 1f, 3f, 2f, 3f )));
//a.add( new Area( new Rectangle2D.Float( 3.05f, 3f, 3.95f, 3f )));
//a.transform( atSchoko );
//g2.clip( a );
//
//g2.translate( 0, currentHeight / 12f );
//
//GeneralPath gp;
//gp = new GeneralPath();
//gp.moveTo( 0f, 0f );
//gp.lineTo( 1f, 0f );
//gp.curveTo( 2f, 0f, 2f, 2f, 3f, 2f );
//g2.setColor( Color.red );
//g2.setStroke( new BasicStroke( 2f ));
//g2.draw( gp.createTransformedShape( atSchoko ));
//g2.draw( gp.createTransformedShape( atSchoko2 ));
//gp.lineTo( 0f, 2f );
//g2.setColor( new Color( 0xFF, 0x00, 0x00, 0x7F ));
//g2.fill( gp.createTransformedShape( atSchoko ));
//g2.fill( gp.createTransformedShape( atSchoko2 ));
//
//gp = new GeneralPath();
//gp.moveTo( 4f, 2f );
//gp.curveTo( 5f, 2f, 5f, 0f, 6f, 0f );
//gp.lineTo( 7f, 0f );
//g2.setColor( new Color( 0x00, 0xA0, 0x00 ));
//g2.setStroke( new BasicStroke( 2f ));
//g2.draw( gp.createTransformedShape( atSchoko ));
//g2.draw( gp.createTransformedShape( atSchoko3 ));
//gp.lineTo( 7f, 2f );
//g2.setColor( new Color( 0x00, 0xA0, 0x00, 0x7F ));
//g2.fill( gp.createTransformedShape( atSchoko ));
//g2.fill( gp.createTransformedShape( atSchoko3 ));
//
//g2.setColor( GraphicsUtil.colrSelection );
//g2.fill( new GeneralPath( new Rectangle2D.Float( 2f, -0.5f, 3f, 3f )).createTransformedShape( atSchoko ));
//
//Point2D p1 = atSchoko.transform( new Point2D.Float( 2f, -0.5f ), null );
//Point2D p2 = atSchoko.transform( new Point2D.Float( 2f,  5.5f ), null );
//Line2D li = new Line2D.Float( p1, p2 );
//g2.setPaint( pntMarkStick );
//g2.setStroke( strkStick );
//g2.draw( li );
//
//			g2.setTransform( atOrig );
//			g2.setClip( clipOrig );
//			g2.setStroke( strkOrig );
//		}
//	}
}
