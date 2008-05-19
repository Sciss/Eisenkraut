/*
 *  FloatingPaletteHandler.java
 *  (de.sciss.gui package)
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
 *		09-Jul-06	created
 */

package de.sciss.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.swing.Timer;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.AbstractWindow;

/**
 *	Regular windows and floating palettes register themselves with a
 *	a <code>FloatingPaletteHandler</code>. The handler then takes care
 *	of hiding and showing the palettes when the application becomes
 *	active or inactive (i.e. one of the regular windows has focus or none of them).
 *
 *	The class is closely based on <code>FloatingPaletteHandler14</code> v2.0 by
 *	Werner Randelshofer (Quaqua project).
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.13, 19-May-08
 *
 *	@synchronization	all methods must be called in the Swing thread
 *
 *	@todo				selecting an item from the right menu bar
 *						(e.g. clock) causes a palette flicker
 */
public class FloatingPaletteHandler
implements ActionListener
{
	private static final int		TIMEOUT			= 100;

    private final Set				palettes		= new HashSet();
    protected final Set				frames			= new HashSet();
    private final Set				hiddenPalettes	= new HashSet();
    protected final Timer			timer;	// when executed, no frame has gotten focus since current window lost focus
    private AbstractWindow			focusedWindow	= null;
	
	private static final Map		instances		= new HashMap();	// apps to instances
	
	private final AbstractWindow.Listener	winListener;
	
	private boolean					listen			= false;
//	private boolean					hidden			= false;
//	private AbstractWindow			borrower		= null;
	  
	private static final boolean	DEBUG			= false;
	
	private int						modalDlgs		= 0;
	
    public static FloatingPaletteHandler getInstance()
	{
		return getInstance( AbstractApplication.getApplication() );
    }
	
    public static FloatingPaletteHandler getInstance( Application app )
	{
		FloatingPaletteHandler fph = (FloatingPaletteHandler) instances.get( app );
		if( fph == null ) {
			fph = new FloatingPaletteHandler();
			instances.put( app, fph );
		}
		return fph;
    }
    
    public FloatingPaletteHandler()
	{
        timer = new Timer( TIMEOUT, this );
        timer.setRepeats( false );

		winListener = new AbstractWindow.Adapter() {
//			public void windowGainedFocus( AbstractWindow.Event e )
			public void windowActivated( AbstractWindow.Event e )
			{
				final AbstractWindow w = e.getWindow();

				timer.stop();
				
				if( frames.contains( w )) {
					setFocusedWindow( w );
//				} else if( DEBUG ) {
//					System.out.println( "windowActivated : not registered : " + w );
				}
			}
			
//			public void windowLostFocus( AbstractWindow.Event e )
			public void windowDeactivated( AbstractWindow.Event e )
			{
				// since a focus traversal always contains
				// a focus loss and a focus gain, we have to
				// wait a little to make sure that no new
				// window was focussed. the timer is executed
				// after 100ms.
				timer.restart();
			}
		};
    }
	
	public void setListening( boolean b )
	{
		listen = b;
		if( !palettes.isEmpty() || !frames.isEmpty() ) throw new IllegalStateException( "Must only be called initially" );
	}
	
//	public void setMenuBarBorrower( AbstractWindow w )
//	{
//		if( (w != null) && !frames.contains( w )) throw new IllegalArgumentException( "Borrower was not registered" );
//	
//		for( Iterator iter = palettes.iterator(); iter.hasNext(); ) {
//			((AbstractWindow) iter.next()).borrowMenuBar( w );
//		}
//		
//		borrower = w;
//	}
//	
//	public AbstractWindow getMenuBarBorrower()
//	{
//		return borrower;
//	}
    
    /**
     * Registers a project window with the FloatingPaletteHandler.
     * <p>
     * When none of the registered windows has focus, the FloatingPaletteHandler
     * hides all registered palette windows.
     * When at least one of the registered windows has focus, the
     * FloatingPaletteHandler shows all registered palette windows.
     */
	public void add( AbstractWindow w )
	{
    	if( DEBUG ) System.out.println( "add : " + w.getClass().getName() );

    	if( w.isFloating() ) {
			if( !palettes.add( w )) {
				throw new IllegalArgumentException( "Palette was already registered" );
			}
//			if( (borrower != null) && (w instanceof AppWindow) ) w.setMenuBarBorrower( borrower );
			if( listen ) w.addListener( winListener );
		} else {
			if( !frames.add( w )) {
				throw new IllegalArgumentException( "Frame was already registered" );
			}
			
			if( listen ) {
				w.addListener( winListener );
				if( isFocused( w )) {
//					setFocusedWindow( w );
focusedWindow = w;
if( DEBUG ) System.out.println( "focusedWindow = " + w.getClass().getName() );
				}
			}
		}
	}
	
//	public void switchFloating( AbstractWindow w )
//	{
//		if( w.isFloating() ) {
//			if( !frames.remove( w )) {
//				throw new IllegalArgumentException( "Frame was not registered" );
//			}
//			
//			if( listen ) {
//				w.removeListener( winListener );
//				if(	isFocused( w )) {
//focusedWindow = null;
//if( DEBUG ) System.out.println( "focusedWindow = null" );
//timer.restart();
//				}
//			}
//		} else {
//			
//		}
//	}
	
	public void addModalDialog()
	{
		modalDlgs++;
	}
	
	public void removeModalDialog()
	{
		modalDlgs--;
	}
    
    /**
     * Unregisters a project window with the FloatingPaletteHandler.
     */
    public void remove( AbstractWindow w )
	{
    	if( DEBUG ) System.out.println( "remove : " + w.getClass().getName() );

    	if( w.isFloating() ) {
			if( !palettes.remove( w )) {
				throw new IllegalArgumentException( "Palette was not registered" );
			}
			if( listen ) w.removeListener( winListener );
			hiddenPalettes.remove( w );
			
		} else {
			if( !frames.remove( w )) {
				throw new IllegalArgumentException( "Frame was not registered" );
			}
			
			if( listen ) {
				w.removeListener( winListener );
				if(	isFocused( w )) {
//					setFocusedWindow( null );
focusedWindow = null;
if( DEBUG ) System.out.println( "focusedWindow = null" );
//System.out.println( "got null" );
timer.restart();
				}
			}
		}
    }
    
    /**
     * Registers a palette window with the FloatingPaletteHandler.
     * <p>
     * The FloatingPaletteHandler shows and hides the palette windows depending
     * on the focused state of the registered project windows.
     */
//    public void addPalette( Window p ) {
//		if( !palettes.add( p )) {
//			throw new IllegalArgumentException( "Palette was already registered" );
//		}
//
//        p.addWindowFocusListener( this );
//    }
    
    /**
     * Removes a project window from the FloatingPaletteHandler.
     */
//    public void removePalette( Window p )
//	{
//		if( !palettes.remove( p )) {
//			throw new IllegalArgumentException( "Palette was not registered" );
//		}
//        p.removeWindowFocusListener( this );
//    }
    
    /**
     * Returns the current applicatin window (the window which was the last to
     * gain focus). Floating palettes may use this method to determine on which
     * window they need to act on.
     */
    public AbstractWindow getFocussedWindow() {
        return focusedWindow;
    }
    
//    public AbstractWindow findAFloatingPalette()
//    {
//    	AbstractWindow w;
//        for( Iterator iter = palettes.iterator(); iter.hasNext(); ) {
//            w = (AbstractWindow) iter.next();
//            if( w.isVisible() ) return w;
//        }
//        return null;
//    }
    
	// --------------- ActionListener interface ---------------

	public void actionPerformed( ActionEvent e )
	{
		setFocusedWindow( null );
	}
    
	// --------------- private methods ---------------

    private static boolean isFocused( AbstractWindow w )
	{
//        if( w.isFocused() ) return true;
        if( w.isActive() ) return true;
		
        final Window[] owned = w.getOwnedWindows();
		
        for( int i = 0; i < owned.length; i++ ) {
            if( isFocused( owned[ i ])) return true;
        }
        return false;
    }

    private static boolean isFocused( Window w )
	{
        if( w.isFocused() ) return true;
		
        final Window[] owned = w.getOwnedWindows();
		
        for( int i = 0; i < owned.length; i++ ) {
            if( isFocused( owned[ i ])) return true;
        }
        return false;
    }
	
//    boolean DEBUG_ENTERED = false;
    
    protected void setFocusedWindow( AbstractWindow w )
	{
//if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
//    	System.err.println( "setFocusedWindow " + w );
//    	if( DEBUG_ENTERED ) throw new IllegalStateException( "DEADLOCK" );
//    	DEBUG_ENTERED = true;
    	
        focusedWindow = w;
		if( DEBUG ) System.out.println( "focusedWindow : " + (w == null ? null : w.getClass().getName()) );
		
		if( modalDlgs == 0 ) {
			if( w != null ) {
	            showPalettes();
			} else {
				hidePalettes();
			}
		}
		
//    	System.err.println( "...done" );
//    	DEBUG_ENTERED = false;
	}
	
    private void showPalettes()
	{
//    	hidden = false;
//System.out.println( "in the beginning " + (focusedWindow != null) );
		if( !hiddenPalettes.isEmpty() ) {
			// in metal look-n-feel, seems that
			// setVisible might dispatch instanenously\
			// somehow setting focusedWindow to null (weird?!!!)
//			final AbstractWindow w = focusedWindow;
			AbstractWindow w;
			for( Iterator iter = hiddenPalettes.iterator(); iter.hasNext(); ) {
				w = (AbstractWindow) iter.next();
				if( DEBUG ) System.out.println( "setVisible( true ) : " + w.getClass().getName() );
				w.setVisible( true );
			}
			if( DEBUG ) System.out.println( "hiddenPalettes.clear" );
			hiddenPalettes.clear();
			//		focusedWindow.requestFocus();
			// trick to not have the originally focussed window loose its focus due to palettes showing up
//System.out.println( "entonces " + (focusedWindow != null) );
			
			// for some reason focusedWindow can be null, although
			// showPalettes is only called when focusedWindow is not null
			// (bug 1953444). the only explanation is that setVisible will
			// immediately invoke one of windowActivated or windowDeactivated
			// without deferal to the event-queue... anyway, we're safe
			// by checking again against null...
			if( focusedWindow != null ) {
				if( DEBUG ) System.out.println( "setVisible( true ) : " + focusedWindow.getClass().getName() );
				focusedWindow.setVisible( true );
//				if( DEBUG ) System.out.println( "toFront : " + focusedWindow.getClass().getName() );
//				focusedWindow.toFront();
			}
		}
    }
    
    private void hidePalettes()
	{
//    	hidden = true;
		AbstractWindow w;

		if( !frames.isEmpty() ) {
			for( Iterator iter = frames.iterator(); iter.hasNext(); ) {
				w = (AbstractWindow) iter.next();
				if( isFocused( w )) return;
			}
		}
		if( !palettes.isEmpty() ) {
            for( Iterator iter = palettes.iterator(); iter.hasNext(); ) {
                w = (AbstractWindow) iter.next();
                if( isFocused( w )) return;
            }
        }

		for( Iterator iter = palettes.iterator(); iter.hasNext(); ) {
            w = (AbstractWindow) iter.next();
			if( w.isVisible() ) {
				if( DEBUG ) {
					System.out.println( "hiddenPalettes.add : " + w.getClass().getName() );
					System.out.println( "setVisible( false ) : " + w.getClass().getName() );
				}
				hiddenPalettes.add( w );
				w.setVisible( false );
            }
        }
		
//		// XXX fuer test
//		for( Iterator iter = hiddenPalettes.iterator(); iter.hasNext(); ) {
//            w = (AbstractWindow) iter.next();
//			w.setVisible( false );
//			if( DEBUG ) System.out.println( "setVisible( false ) : " + w.getClass().getName() );
//		}
    }
}