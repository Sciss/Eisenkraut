/*
 *  AppWindow.java
 *  (de.sciss.common package)
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
 *		19-Aug-06	created from de.sciss.eisenkraut.gui.BasicFrame
 */

package de.sciss.common;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
//import java.awt.event.FocusEvent;
//import java.awt.event.FocusListener;
//import java.awt.event.WindowAdapter;
//import java.awt.event.WindowEvent;
//import java.awt.event.WindowFocusListener;
import java.awt.geom.Point2D;
import java.beans.PropertyVetoException;
import java.util.prefs.Preferences;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.gui.AquaWindowBar;
import de.sciss.gui.FloatingPaletteHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.InternalFrameListenerWrapper;
import de.sciss.gui.WindowListenerWrapper;

/**
 *  Common functionality for all application windows.
 *  This class provides means for storing and recalling
 *  window bounds in preferences. All subclass windows
 *  will get a copy of the main menubar as well.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 18-Mar-08
 *
 *  @todo   tempFloatingTimer could maybe be eliminated
 *  		in favour of a simple EventQueue.invokeLater
 */
public class AppWindow
implements AbstractWindow
{
	/*
	 *  Value: String representing a Point object
	 *  describing a windows location. Use stringToPoint.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	private static final String KEY_LOCATION		= "location";   // point
	/*
	 *  Value: String representing a Dimension object
	 *  describing a windows size. Use stringToDimension.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	private static final String KEY_SIZE			= "size";		// dimension
	/*
	 *  Value: Boolean stating wheter a window is
	 *  shown or hidden.<br>
	 *  Has default value: no!<br>
	 *  Node: multiple occurences in shared -> (Frame-class)
	 */
	private static final String KEY_VISIBLE		= "visible";	// boolean

	private ComponentListener	cmpListener		= null;
	private Listener			winListener		= null;

	// windows bounds get saved to a sub node inside the shared node
	// the node's name is the class name's last part (omitting the package)
	protected Preferences					classPrefs	= null;
	
	// fucking aliases
	private final Component					c;
	private final Window					w;
	private final Frame						f;
	private final Dialog					d;
	private final JComponent				jc;
	private final JDialog					jd;
	protected final JFrame					jf;
	protected final JInternalFrame			jif;
	
	private final AquaWindowBar				ggTitle;
	
	// menu bar matrix:
	//			screenMenuBar	internalFrames		other
	// regular	own				own (deleg)			own
	// support	own				---					---
	// palette	borrow			borrow (deleg)		---
	
	private boolean							floating;
	protected final boolean					ownMenuBar, borrowMenuBar;
	protected final BasicWindowHandler		wh;
	protected JMenuBar						bar			= null;
	protected AbstractWindow				barBorrower	= null;
	protected boolean						active		= false;
	
//	private final int						flags;
	
	protected boolean						initialized	= false;
	
	private static final int				TEMPFLOAT_TIMEOUT	= 100;
	protected final boolean					tempFloating;
	protected Timer							tempFloatingTimer;

	public AppWindow( int flags )
	{
		super();
		final Application	app		= AbstractApplication.getApplication();
		final int			type	= flags & TYPES_MASK;
		wh = (BasicWindowHandler) app.getWindowHandler();

		switch( type ) {
		case REGULAR:
		case SUPPORT:
			if( wh.usesInternalFrames() ) {
				c = jc = jif	=
					new JInternalFrame( null, true, true, true, true );
				w = f = jf	= null;
				d = jd			= null;
				wh.getDesktop().add( jif );
				ownMenuBar				= type == REGULAR;

			} else {
				c = w = f = jf
					= new JFrame();
				jc = jif			= null;
				d = jd			= null;
				ownMenuBar				= wh.usesScreenMenuBar() || (type == REGULAR);
			}
//			floating			= false;
			tempFloating	= (type == SUPPORT) && wh.usesFloating();
			floating		= tempFloating;
			borrowMenuBar	= false;
			ggTitle		= null;
			break;
			
		case PALETTE:
			floating		= wh.usesFloating();
			tempFloating	= false;
			ownMenuBar		= false;
			
			if( wh.usesInternalFrames() ) {
				c = jc = jif =
					new JInternalFrame( null, true, true, true, true );
				w = f = jf	= null;
				d = jd			= null;
				borrowMenuBar			= true;
				ggTitle				= null;
				
				if( floating ) jif.putClientProperty( "JInternalFrame.isPalette", Boolean.TRUE );
				wh.getDesktop().add( jif, floating ? JLayeredPane.PALETTE_LAYER : JLayeredPane.DEFAULT_LAYER );

			} else {

				c = w = f = jf =
					new JFrame();
				jc = jif			= null;
				d = jd			= null;
//				borrowMenuBar	= wh.usesScreenMenuBar();
				
				if( floating ) {
					ggTitle = new AquaWindowBar( this, true );
					ggTitle.setAlwaysOnTop( true );
					borrowMenuBar = false;
					jf.setUndecorated( true );
					
					final Container cp = jf.getContentPane();
					
//					cp.add( ggTitle, orient == HORIZONTAL ? BorderLayout.NORTH : BorderLayout.WEST );
					cp.add( ggTitle, BorderLayout.NORTH );
					
//					if( resizable ) {
//						final JPanel p = new JPanel( new BorderLayout() );
//						p.add( new AquaResizeGadget(), BorderLayout.EAST );
//						cp.add( p, BorderLayout.SOUTH );
//					}
//				} else {
//					if( prefs.getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
//						getContentPane().add( Box.createVerticalStrut( 16 ), BorderLayout.SOUTH );
//					}
				} else {
					borrowMenuBar	= wh.usesScreenMenuBar();
					ggTitle		= null;
				}
			}
			break;
		
		default:
			throw new IllegalArgumentException( "Unsupported window type : " + (flags & TYPES_MASK) );
		}
		
		initTempFloating();
   	}
	
	protected AppWindow( Dialog wrap )
	{
		super();
		final Application app	= AbstractApplication.getApplication();
		wh = (BasicWindowHandler) app.getWindowHandler();

//wrap.addWindowFocusListener( new WindowFocusListener() {
//	public void windowGainedFocus( WindowEvent e ) {
//		System.out.println( "WINDOWFOCUSGAINED" );
//	}
//	
//	public void windowLostFocus( WindowEvent e ) {
//		System.out.println( "WINDOWFOCUSLOST" );
//	}
//});
//wrap.addWindowListener( new WindowAdapter() {
//	public void windowOpened( WindowEvent e ) {
//		System.out.println( "OPENED" );
//	}
//
//	public void windowActivated( WindowEvent e ) {
//		System.out.println( "ACTIVATED" );
//	}
//
//	public void windowDeactivated( WindowEvent e ) {
//		System.out.println( "DEACTIVATED" );
//	}
//});
//wrap.addFocusListener( new FocusListener() {
//	public void focusGained( FocusEvent e ) {
//		System.out.println( "FOCUSGAINED" );
//	}
//
//	public void focusLost( FocusEvent e ) {
//		System.out.println( "FOCUSLOST" );
//	}
//});
		
		c = w				= wrap;
		f = jf				= null;	// XXX
		jc = jif			= null;
		d = jd				= null;
		ownMenuBar			= false;
// WARNING: modal dialogs must not be permanent floating
// because they would cause the floating palette handler
// to hide and show them, re-creating a modal event queue
// interruption!!
//		permFloating		= !wh.usesInternalFrames() && wh.usesFloating();
//		tempFloating		= false;
//		tempFloating		= !wh.usesInternalFrames() && wh.usesFloating();
//		floating			= false;
//floating = tempFloating;
		tempFloating		= false;
		floating			= false;
		borrowMenuBar		= false;
		ggTitle				= null;
		
//		if( floating ) GUIUtil.setAlwaysOnTop( wrap, true );
//		initTempFloating();
//lala=true;
	}
//boolean lala=false;
//	
//	protected void gaga() { removeListener( winListener ); }
	
	private void initTempFloating()
	{
		if( tempFloating ) {
			tempFloatingTimer = new Timer( TEMPFLOAT_TIMEOUT, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					GUIUtil.setAlwaysOnTop( getWindow(), true );
				}
			});
			tempFloatingTimer.setRepeats( false );
		}
	}
	
	public BasicWindowHandler getWindowHandler()
	{
		return wh;
	}

	protected static Dimension stringToDimension( String value )
	{
		Dimension		dim	= null;
		StringTokenizer tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				dim		= new Dimension( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return dim;
	}

	protected static Point stringToPoint( String value )
	{
		Point			pt	= null;
		StringTokenizer tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				pt		= new Point( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return pt;
	}
	
	protected static String pointToString( Point value )
	{
		return( value != null ? (value.x + " " + value.y) : null );
	}
	
	public static String dimensionToString( Dimension value )
	{
		return( value != null ? (value.width + " " + value.height) : null );
	}

	public boolean isFloating()
	{
		return floating;
	}
	
//	public boolean isTempFloating()
//	{
//		return tempFloating;
//	}
	
	/*
	 *  Restores this frame's bounds and visibility
	 *  from its class preferences.
	 *
	 *  @see	#restoreAllFromPrefs()
	 */
	private void restoreFromPrefs()
	{
		String		sizeVal = classPrefs.get( KEY_SIZE, null );
		String		locVal  = classPrefs.get( KEY_LOCATION, null );
		String		visiVal	= classPrefs.get( KEY_VISIBLE, null );
		Rectangle   r		= c.getBounds();
//		Insets		i		= getInsets();

//System.err.println( "this "+getClass().getName()+ " visi = "+visiVal );

		Dimension dim		= stringToDimension( sizeVal );
		if( (dim == null) || alwaysPackSize() ) {
			pack();
			dim				= c.getSize();
		}

		r.setSize( dim );
		Point p = stringToPoint( locVal );
		if( p != null ) {
			r.setLocation( p );
			c.setBounds( r );
		} else {
			c.setSize( dim );
			final Point2D prefLoc = getPreferredLocation();
			wh.place( this, (float) prefLoc.getX(), (float) prefLoc.getY() );
//			if( shouldBeCentered() ) setLocationRelativeTo( null );
		}
		c.invalidate();
//		if( alwaysPackSize() ) {
//			pack();
//		} else {
		c.validate();
//		}
//		lim.queue( this );
		if( (visiVal != null) && restoreVisibility() ) {
			setVisible( new Boolean( visiVal ).booleanValue() );
		}
	}
	
 	/**
	 *  Updates Swing component tree for all
     *  frames after a look-and-feel change
	 */
	public static void lookAndFeelUpdate()
	{
//      if( springContainer == null ) return;
//        
//		AppWindow		bf;
//		int				i;
//		LayoutComponent springComp;
//	
//		for( i = 0; i < springContainer.getComponentCount(); i++ ) {
//			springComp	= (LayoutComponent) springContainer.getComponent( i );
//			bf			= springComp.getRealOne();
//            SwingUtilities.updateComponentTreeUI( bf );
//      }
	}

	/**
	 *  Queries whether this frame's bounds
	 *  should be packed automatically to the
	 *  preferred size independent of
	 *  concurrent preference settings
	 *
	 *  @return	<code>true</code>, if the frame wishes
	 *			to be packed each time a custom setSize()
	 *			would be applied in the course of a
	 *			preference recall. The default value
	 *			of <code>true</code> can be modified by
	 *			subclasses by overriding this method.
	 *  @see	java.awt.Window#pack()
	 */
	protected boolean alwaysPackSize()
	{
		return true;
	}

	protected boolean restoreVisibility()
	{
		return true;
	}

	protected boolean autoUpdatePrefs()
	{
		return false;
	}
	
	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0.5f, 0.5f );
	}

//	/**
//	 *  Queries whether this frame should
//     *  have a copy of the menu bar. The default
//     *  implementation returns true, basic palettes
//     *  will return false.
//	 *
//	 *  @return	<code>true</code>, if the frame wishes
//	 *			to be given a distinct menu bar
//	 */
//	protected boolean hasMenuBar()
//	{
//		switch( flags & TYPES_MASK ) {
//		case REGULAR:
//			return true;
//		
//		default:
//			return false;
//		}
//	}

	/**
	 *	MenuFactory uses this method to replace dummy
	 *	menu items such as File->Save with real actions
	 *	depending on the concrete frame. By default this
	 *	method just returns <code>dummyAction</code>, indicating
	 *	that there is no replacement for the dummy action.
	 *	Subclasses may check the provided <code>ID</code>
	 *	and return replacement actions instead.
	 *
	 *	@param	ID	an identifier for the menu item, such
	 *				as <code>MenuFactory.MI_FILE_SAVE</code>.
	 *
	 *  @return		the action to use instead of the inactive
	 *				dummy action, or <code>dummyAction</code> if no
	 *				specific action exists (menu item stays ghosted)
	 *
	 *	@see	MenuFactory#MI_FILE_SAVE
	 *	@see	MenuFactory#gimmeSomethingReal( AppWindow )
	 */
//	protected Action replaceDummyAction( int ID, Action dummyAction )
//	{
//		return dummyAction;
//	}

	/**
	 *  Subclasses should call this
	 *  after having constructed their GUI.
	 *  Then this method will attach a copy of the main menu
	 *  from <code>root.menuFactory</code> and
	 *  restore bounds from preferences.
	 *
	 *  @param  root	application root
	 *
	 *  @see	MenuFactory#gimmeSomethingReal( AppWindow )
	 */
	public void init()
	{
		if( initialized ) throw new IllegalStateException( "Window was already initialized." );

//System.out.println( "init " + getClass().getName() );

		if( borrowMenuBar ) {
			borrowMenuBar( wh.getMenuBarBorrower() );
			wh.addBorrowListener( this );
		} else if( ownMenuBar ) {
			setJMenuBar( wh.getMenuBarRoot().createBar( this ));
		}
//		AbstractApplication.getApplication().addComponent( getClass().getName(), this );
		
		winListener = new AbstractWindow.Adapter() {
			public void windowOpened( AbstractWindow.Event e )
			{
//System.err.println( "shown" );
				if( classPrefs != null ) classPrefs.putBoolean( KEY_VISIBLE, true );
				if( !initialized ) System.err.println( "WARNING: window not initialized (" + e.getWindow() + ")" );
			}

//			public void windowClosing( WindowEvent e )
//			{
//				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, false );
//			}

			public void windowClosed( AbstractWindow.Event e )
			{
//System.err.println( "hidden" );
				if( classPrefs != null ) classPrefs.putBoolean( KEY_VISIBLE, false );
			}
			
			public void windowActivated( AbstractWindow.Event e )
			{
				try {
					active = true;
					if( wh.usesInternalFrames() && ownMenuBar ) {
						wh.getMasterFrame().setJMenuBar( bar );
					} else if( borrowMenuBar && (barBorrower != null) ) {
						barBorrower.setJMenuBar( null );
						if( jf != null ) {
							jf.setJMenuBar( bar );
						} else if( jif != null ) {
							wh.getMasterFrame().setJMenuBar( bar );
						} else {
							throw new IllegalStateException();
						}
					}
					if( tempFloating ) {
						if( jif == null ) {
//System.out.println( "activ " + enc_getClass().getName() );
////							wh.removeWindow( AbstractWindow.this, null );
tempFloatingTimer.restart();
//							GUIUtil.setAlwaysOnTop( getWindow(), true );
////							floating = true;
////							wh.addWindow( AbstractWindow.this, null );
						} else {
							jif.setLayer( JLayeredPane.MODAL_LAYER );
						}
//					} else if( wh.usesFloating() ) {
//						// tricky...
//						// we need to do this because if the opposite's
//						// window is tempFloating, it will reset
//						// alwaysOnTop to false too late for the OS,
//						// so this one is not jumping to the front
//						// automatically upon activation...
//						toFront();
					}
				}
				// seems to be a bug ... !
				catch( NullPointerException e1 ) {
					e1.printStackTrace();
				}
			}

			public void windowDeactivated( AbstractWindow.Event e )
			{
//System.out.println( "deac2 " + enc_getClass().getName() );
				try {
					active = false;
					if( wh.usesInternalFrames() && ownMenuBar ) {
						if( wh.getMasterFrame().getJMenuBar() == bar ) wh.getMasterFrame().setJMenuBar( null );
					} else if( borrowMenuBar && (barBorrower != null) ) {
						if( jf != null ) {
							jf.setJMenuBar( null );
						}
						barBorrower.setJMenuBar( bar );
					}
					if( tempFloating ) {
						if( jif == null ) {
//System.out.println( "deact " + enc_getClass().getName() );
//							wh.removeWindow( AbstractWindow.this, null );
							GUIUtil.setAlwaysOnTop( getWindow(), false );
tempFloatingTimer.stop();
//							floating = false;
//							wh.addWindow( AbstractWindow.this, null );

// find the new active window (is valid only after
// the next event cycle) and re-put it in the front\
// coz setAlwaysOnTop came "too late"
EventQueue.invokeLater( new Runnable() {
	public void run()
	{
		final AbstractWindow fw = FloatingPaletteHandler.getInstance().getFocussedWindow();
		if( fw != null ) fw.toFront();
	}
});
						} else {
							jif.setLayer( JLayeredPane.DEFAULT_LAYER );
						}
					}
				}
				// seems to be a bug ... !
				catch( NullPointerException e1 ) {
					e1.printStackTrace();
				}
			}
		};
		addListener( winListener );
		
		if( autoUpdatePrefs() ) {
			getClassPrefs();	// this creates the prefs
			restoreFromPrefs();
			cmpListener = new ComponentAdapter() {
				public void componentResized( ComponentEvent e )
				{
					classPrefs.put( KEY_SIZE, dimensionToString( e.getComponent().getSize() ));
				}

				public void componentMoved( ComponentEvent e )
				{
					classPrefs.put( KEY_LOCATION, pointToString( e.getComponent().getLocation() ));
				}

				public void componentShown( ComponentEvent e )
				{
					classPrefs.putBoolean( KEY_VISIBLE, true );
				}

				public void componentHidden( ComponentEvent e )
				{
					classPrefs.putBoolean( KEY_VISIBLE, false );
	//System.err.println( "hidden" );
				}
			};
			c.addComponentListener( cmpListener );
		} else {
			if( alwaysPackSize() ) {
				pack();
			}
		}
		
		wh.addWindow( this, null );
		initialized = true;
	}
	
	protected Preferences getClassPrefs()
	{
		if( classPrefs == null ) {
			final String className = getClass().getName();
			classPrefs = AbstractApplication.getApplication().getUserPrefs().node(
					className.substring( className.lastIndexOf( '.' ) + 1 ));
		}
		return classPrefs;
	}

	protected void addDynamicListening( DynamicListening l )
	{
		if( c instanceof RootPaneContainer ) {
			new DynamicAncestorAdapter( l ).addTo( ((RootPaneContainer) c).getRootPane() );
		} else if( jc != null ) {
			new DynamicAncestorAdapter( l ).addTo( jc );
		}
	}

	// ---------- AbstractWindow interface ----------
	
	public Component getWindow()
	{
		return c;
	}
	
	public Insets getInsets()
	{
		if( w != null ) {
			return w.getInsets();
		} else if( jif != null ) {
			return jif.getInsets();
		} else {
			throw new IllegalStateException();
		}
	}
    
	/**
	 *  Frees resources, clears references
	 */
	public void dispose()
	{
		if( tempFloatingTimer != null ) {
			tempFloatingTimer.stop();
		}
		
		if( initialized ) {
			if( winListener != null ) removeListener( winListener );
			if( cmpListener != null ) c.removeComponentListener( cmpListener );
			
			wh.removeWindow( this, null );
//			AbstractApplication.getApplication().addComponent( getClass().getName(), null );
			
			if( borrowMenuBar ) {
				borrowMenuBar( null );
				wh.removeBorrowListener( this );
			}
			if( wh.getMenuBarBorrower() == this ) wh.setMenuBarBorrower( null );
			if( ownMenuBar ) {
				setJMenuBar( null );
				wh.getMenuBarRoot().destroy( this );
			}
		}
		
		if( w != null ) {
			w.dispose();
		} else if( jif != null ) {
			jif.dispose();
		}
		
		if( ggTitle != null ) ggTitle.dispose();
		
		classPrefs	= null;
		cmpListener	= null;
		winListener = null;
	}
	
//	public void setSize( int width, int height )
//	{
//		c.setSize( width, height );
//	}

	public void setSize( Dimension d )
	{
		c.setSize( d );
	}
	
	public Dimension getSize()
	{
		return c.getSize();
	}

	public Rectangle getBounds()
	{
		return c.getBounds();
	}
	
	public void setBounds( Rectangle r )
	{
		c.setBounds( r );
	}

	public void setLocation( Point p )
	{
		c.setLocation( p );
	}
	
	public Point getLocation()
	{
		return c.getLocation();
	}

	public void setPreferredSize( Dimension d )
	{
		if( c instanceof RootPaneContainer ) {
			((RootPaneContainer) c).getRootPane().setPreferredSize( d );
		} else if( jc != null ) {
			jc.setPreferredSize( d );
		} else {
			throw new IllegalStateException();
		}
	}
	
//	public boolean hasFocus()
//	{
//		return c.hasFocus();
//	}

//	public boolean isFocused()
//	{
//		if( w != null ) {
//			return w.isFocused();
//		} else {
//			return c.hasFocus();
//		}
//	}
	
//	public void requestFocus()
//	{
//		c.requestFocus();
//	}
	
	public boolean isActive()
	{
		if( w != null ) {
			return w.isActive();
		} else {
			return false;
		}
	}

	public void addListener( Listener l )
	{
		if( w != null ) {
			WindowListenerWrapper.add( l, this );
		} else if( jif != null ) {
			InternalFrameListenerWrapper.add( l, this );
		} else {
			throw new IllegalStateException();
		}
	}
	
	public void removeListener( Listener l )
	{
		if( w != null ) {
			WindowListenerWrapper.remove( l, this );
		} else if( jif != null ) {
			InternalFrameListenerWrapper.remove( l, this );
		} else {
			throw new IllegalStateException();
		}
	}

//	public void addWindowFocusListener( WindowFocusListener l )
//	{
//		if( w != null ) {
//			w.addWindowFocusListener( l );
//		} else if( jif != null ) {
//			throw new IllegalStateException( "InternalFrameListener wrapper not yet implemented" );
//		} else {
//			throw new IllegalStateException();
//		}
//	}
//	
//	public void removeWindowFocusListener( WindowFocusListener l )
//	{
//		if( w != null ) {
//			w.removeWindowFocusListener( l );
//		} else if( jif != null ) {
//			throw new IllegalStateException( "InternalFrameListener wrapper not yet implemented" );
//		} else {
//			throw new IllegalStateException();
//		}
//	}
	
	public void toFront()
	{
//if( lala ) {
//	System.out.println( "toFront" );
//	new Exception().printStackTrace();
//}
//		
		if( w != null ) {
			w.toFront();
		} else if( jif != null ) {
			jif.toFront();
			try {
				jif.setSelected( true );
			} catch( PropertyVetoException e ) { /* ignored */ }
		} else {
			throw new IllegalStateException();
		}
	}
	
	public void setVisible( boolean b )
	{
//if( lala ) {
//	System.out.println( "setVisible( " + b + " )" );
//	new Exception().printStackTrace();
//}
//
		c.setVisible( b );
	}
	
	public boolean isVisible()
	{
		return c.isVisible();
	}
	
	public void setDefaultCloseOperation( int mode )
	{
		if( ggTitle != null ) {
			ggTitle.setDefaultCloseOperation( mode );
		} else if( jf != null ) {
			jf.setDefaultCloseOperation( mode );
		} else if( jd != null ) {
			jd.setDefaultCloseOperation( mode );
		} else if( jif != null ) {
			jif.setDefaultCloseOperation( mode );
		} else {
			throw new IllegalStateException( "setDefaultCloseOperation wrapper not yet implemented" );
		}
	}
	
	public void pack()
	{
		if( w != null ) {
			// circumvention for bug 1924630 : this throws a NullPointerException
			// with the combination Metal-lnf / java 1.5 / screen menu bar / laf window deco
			// / floating palettes. We have to make sure the window is focusable
			// during pack():
			final boolean wasFocusable = w.getFocusableWindowState();
			if( !wasFocusable ) {
				w.setFocusableWindowState( true );
			}
			w.pack();
			if( !wasFocusable ) {
				w.setFocusableWindowState( false );
			}
		} else if( jif != null ) {
			// bug in swing??
			// when using undecorated windows plus metal-lnf plus lnf-window-deco
//			try { jif.pack(); } catch( NullPointerException e ) {}
			jif.pack();
		} else {
			throw new IllegalStateException();
		}
	}
	
	public void setTitle( String title )
	{
		if( ggTitle != null ) {
			ggTitle.setTitle( title );
		} else if( f != null ) {
			f.setTitle( title );
		} else if( d != null ) {
			d.setTitle( title );
		} else if( jif != null ) {
			jif.setTitle( title );
		} else {
			throw new IllegalStateException();
		}
	}
	
	public String getTitle()
	{
		if( f != null ) {
			return f.getTitle();
		} else if( d != null ) {
			return d.getTitle();
		} else if( jif != null ) {
			return jif.getTitle();
		} else {
			return null; // throw new IllegalStateException();
		}
	}
	
	public Container getContentPane()
	{
		if( c instanceof RootPaneContainer ) {
			return ((RootPaneContainer) c).getContentPane();
		} else {
			return w;
		}
	}
	
	public void setContentPane( Container c2 )
	{
		if( c instanceof RootPaneContainer ) {
			((RootPaneContainer) c).setContentPane( c2);
		} else {
			throw new IllegalStateException();
		}
	}
	
	public void setJMenuBar( JMenuBar m )
	{
		try {
			if( jf != null ) {
				bar = m;
				jf.setJMenuBar( m );
			} else if( jif != null ) {
				bar = m;
				if( active && ownMenuBar ) wh.getMasterFrame().setJMenuBar( bar );
	//			jif.setJMenuBar( m );
			} else {
				throw new IllegalStateException();
			}
		}
		// seems to be a bug ... !
		catch( NullPointerException e1 ) {
			e1.printStackTrace();
		}
	}

	public JMenuBar getJMenuBar()
	{
		return bar;
//		if( jf != null ) {
//			return jf.getJMenuBar();
//		} else if( jif != null ) {
//			return bar; // jif.getJMenuBar();
//		} else {
//			return null; // throw new IllegalStateException();
//		}
	}
	
	protected void borrowMenuBar( AbstractWindow aw )
	{
		if( borrowMenuBar && (barBorrower != aw) ) {
			if( (bar != null) && (barBorrower != null) ) {
				barBorrower.setJMenuBar( bar );
				bar = null;
			}
			barBorrower = aw;
			bar			= barBorrower == null ? null : barBorrower.getJMenuBar();
//System.err.println( "setting bar " + bar + " for window " + this + "; active = "+active );
			if( active ) {
				if( barBorrower != null ) barBorrower.setJMenuBar( null );
				if( jf != null ) {
					jf.setJMenuBar( bar );
				} else if( jif != null ) {
					wh.getMasterFrame().setJMenuBar( bar );
				} else {
					throw new IllegalStateException();
				}
			}
		}
	}
	
	public InputMap getInputMap( int condition )
	{
		if( c instanceof RootPaneContainer ) {
			return ((RootPaneContainer) c).getRootPane().getInputMap( condition );
		} else {
			throw new IllegalStateException();
		}
	}

	public ActionMap getActionMap()
	{
		if( c instanceof RootPaneContainer ) {
			return ((RootPaneContainer) c).getRootPane().getActionMap();
		} else {
			throw new IllegalStateException();
		}
	}
	
	public Window[] getOwnedWindows()
	{
		if( w != null ) {
			return w.getOwnedWindows();
		} else {
			return new Window[ 0 ];
		}
	}
	
	public void setFocusTraversalKeysEnabled( boolean enabled )
	{
		c.setFocusTraversalKeysEnabled( enabled );
	}
	
	public void setDirty( boolean dirty )
	{
		if( c instanceof RootPaneContainer ) {
			((RootPaneContainer) c).getRootPane().putClientProperty( "windowModified", new Boolean( dirty ));
		}
	}

	public void setLocationRelativeTo( Component comp )
	{
		if( w != null ) {
			w.setLocationRelativeTo( comp );
		} else {
//			throw new IllegalStateException();
			final Point p;
			if( comp == null ) {
				if( jif == null ) {
					p = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
				} else {
					comp = wh.getMasterFrame().getWindow();
					p = new Point( comp.getWidth() >> 1, comp.getHeight() >> 1 );
				}
 			} else {
				p = comp.getLocation();
				p.translate( comp.getWidth() >> 1, comp.getHeight() >> 1 );
			}
			final Point p2 = SwingUtilities.convertPoint( comp, p, c );
			p2.translate( -(c.getWidth() >> 1), -(c.getHeight() >> 1) );
			c.setLocation( p2 );
		}
	}
	
//	public void setUndecorated( boolean b )
//	{
//		if( d != null ) {
//			d.setUndecorated( b );
//		} else if( f != null ) {
//			f.setUndecorated( b );
//		} else {
////			throw new IllegalStateException();
//System.err.println( "FUCKING HELL setUndecorated NOT POSSIBLE WITH THIS WINDOW TYPE" );
//		}
//	}

	public void setResizable( boolean b )
	{
		if( f != null ) {
			f.setResizable( b );
		} else if( d != null ) {
			d.setResizable( b );
		} else if( jif != null ) {
			jif.setResizable( b );
		} else {
			throw new IllegalStateException();
		}
	}
	
	public boolean isResizable()
	{
		if( f != null ) {
			return f.isResizable();
		} else if( d != null ) {
			return d.isResizable();
		} else if( jif != null ) {
			return jif.isResizable();
		} else {
			throw new IllegalStateException();
		}
	}
	
	public void revalidate()
	{
		if( c instanceof RootPaneContainer ) {
			((RootPaneContainer) c).getRootPane().revalidate();
		} else if( jc != null ) {
			jc.revalidate();
		}
	}
}