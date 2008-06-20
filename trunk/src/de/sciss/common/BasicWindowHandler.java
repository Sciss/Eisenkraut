/*
 *  BasicWindowHandler.java
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
 *		21-May-05	created
 *		09-Jul-06	hosts a FloatingPaletteHandler
 */

package de.sciss.common;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.roydesign.mac.MRJAdapter;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.WindowHandler;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.FloatingPaletteHandler;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuRoot;
import de.sciss.gui.WindowListenerWrapper;

//import de.sciss.eisenkraut.Main;
//import de.sciss.eisenkraut.util.PrefsUtil;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.71, 10-Jun-08
 */
public class BasicWindowHandler
extends AbstractWindowHandler
{
	/**
	 *  Value: Boolean stating whether internal frames within one
     *  big app frame are used. Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_INTERNALFRAMES = "internalframes";

	/**
	 *  Value: Boolean stating whether palette windows should
     *  be floating on top and have palette decoration. Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_FLOATINGPALETTES = "floatingpalettes";

	/**
	 *  Value: Boolean stating whether to use the look-and-feel (true)
     *  or native (false) decoration for frame borders. Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_LAFDECORATION = "lafdecoration";

	/**
	 *  Value: Rectangle describing the usable screen space last time
     *  the application was launched. Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_SCREENSPACE = "screenspace";

	private final FloatingPaletteHandler	fph;
	private final boolean					internalFrames, floating;
	protected final JDesktopPane			desktop;
	private final MasterFrame				masterFrame;
//	private final Window					hiddenTopWindow;
	
	private final List						collBorrowListeners		= new ArrayList();
	private AbstractWindow					borrower				= null;
	private AbstractWindow					defaultBorrower			= null;		// when no doc frame active (usually the main log window)

	private final Action					actionCollect;
	private final boolean					autoCollect;
	
	private final BasicApplication			root;

	public BasicWindowHandler( BasicApplication root )
	{
		super();
		
		final Preferences	prefs	= root.getUserPrefs();
		final boolean		lafDeco = prefs.getBoolean( KEY_LAFDECORATION, false );
		final Rectangle		oScreen	= stringToRectangle( prefs.get( KEY_SCREENSPACE, null ));
		final Rectangle		nScreen;
		JFrame.setDefaultLookAndFeelDecorated( lafDeco );
		
		this.root		= root;
		internalFrames	= prefs.getBoolean( KEY_INTERNALFRAMES, false );
		floating		= prefs.getBoolean( BasicWindowHandler.KEY_FLOATINGPALETTES, false );
		fph				= FloatingPaletteHandler.getInstance();

		if( internalFrames ) {
			masterFrame = new MasterFrame();
			masterFrame.setTitle( root.getName() );
//			masterFrame.setSize( 400, 400 ); // XXX
//			masterFrame.setVisible( true );
			desktop		= new JDesktopPane();
			masterFrame.getContentPane().add( desktop );
//			hiddenTopWindow = null;
		} else {
			desktop		= null;
			masterFrame	= null;
			fph.setListening( true );
//			if( floating ) {
//				hiddenTopWindow = new Frame();
//				GUIUtil.setAlwaysOnTop( hiddenTopWindow, true );
//			} else {
//				hiddenTopWindow = null;
//			}
		}
		
		nScreen		= calcOuterBounds();
		autoCollect	= !nScreen.equals( oScreen );
		prefs.put( KEY_SCREENSPACE, rectangleToString( nScreen ));
		actionCollect = new ActionCollect( root.getResourceString( "menuCollectWindows" ));
//System.out.println( "autoCollect = " + autoCollect );
		
//		MenuGroup	mg;
//
//		mg	= (MenuGroup) root.getMenuFactory().get( "window" );
//		mg.add( new MenuItem( "collect", new actionCollectClass( root.getResourceString( "menuCollectWindows" ))));
//		mg.addSeparator();
	}
	
	public MenuRoot getMenuBarRoot()
	{
		return root.getMenuBarRoot();
	}
	
	public Action getCollectAction()
	{
		return actionCollect;
	}
	
	public static Component getWindowAncestor( Component c )
	{
		final WindowHandler wh = AbstractApplication.getApplication().getWindowHandler();
		
		return SwingUtilities.getAncestorOfClass( (wh instanceof BasicWindowHandler) && ((BasicWindowHandler) wh).internalFrames ?
												  JInternalFrame.class : Window.class, c );
	}
	
	// make sure the menuFactory is ready when calling this
	public void init()
	{
		if( masterFrame != null ) {
			masterFrame.setDefaultMenuBar( root.getMenuBarRoot().createBar( masterFrame ));
			masterFrame.setVisible( true );
		}
	}
	
	public void setMenuBarBorrower( AbstractWindow w )
	{
		borrower = w;
		for( Iterator iter = collBorrowListeners.iterator(); iter.hasNext(); ) {
			((AppWindow) iter.next()).borrowMenuBar( borrower == null ? defaultBorrower : borrower );
		}
	}
	
	public void setDefaultBorrower( AbstractWindow w )
	{
		if( !internalFrames ) {
			defaultBorrower = w;
		}
	}
	
	public void addBorrowListener( AppWindow w )
	{
		collBorrowListeners.add( w );
	}
	
	public void removeBorrowListener( AppWindow w )
	{
		collBorrowListeners.remove( w );
	}
	
	public AbstractWindow getMenuBarBorrower()
	{
		return( borrower == null ? defaultBorrower : borrower );
	}
	
//	public MenuRoot getMenuBarRoot()
//	{
//		return ((BasicApplication) AbstractApplication.getApplication()).getMenuBarRoot();
//	}
//
//	public Font getDefaultFont()
//	{
//		return GraphicsUtil.smallGUIFont;
//	}
//
//	public void addWindow( Window w, Map options )
//	{
//		super.addWindow( w, options );
//		if( (w instanceof FloatingPalette) && ((FloatingPalette) w).isFloating() ) {
//			fph.addPalette( w );
//		} else {
//			fph.addFrame( w );
//		}
//	}
	
	public void addWindow( AbstractWindow w, Map options )
	{
		super.addWindow( w, options );
//		if( (w instanceof FloatingPalette) && ((FloatingPalette) w).isFloating() ) {
		if( fph != null ) fph.add( w );
//System.out.println( "checkin " + w );
		if( autoCollect ) {
			collect( w, calcOuterBounds() );
		}
//		if( w.isFloating() ) {
//			fph.addPalette( w );
//		} else {
//			fph.addFrame( w );
//		}
	}
	
//	public void removeWindow( Window w, Map options )
//	{
//		super.removeWindow( w, options );
//		if( (w instanceof FloatingPalette) && ((FloatingPalette) w).isFloating() ) {
//			fph.removePalette( w );
//		} else {
//			fph.removeFrame( w );
//		}
//	}

	public void removeWindow( AbstractWindow w, Map options )
	{
		super.removeWindow( w, options );
		if( fph != null ) fph.remove( w );
//		if( w.isFloating() ) {
//			fph.removePalette( w );
//		} else {
//			fph.removeFrame( w );
//		}
	}
	
	public AbstractWindow createWindow( int flags )
	{
//		final BasicFrame f = new BasicFrame();
//		f.init( root );
//		return f;
		return new AppWindow( flags );
	}
	
	public boolean usesInternalFrames()
	{
		return internalFrames;
	}

	public boolean usesFloating()
	{
		return floating;
	}

	public boolean usesScreenMenuBar()
	{
		return MRJAdapter.isSwingUsingScreenMenuBar();
	}

	public JDesktopPane getDesktop()
	{
		return desktop;
	}
	
	public AbstractWindow getMasterFrame()
	{
		return masterFrame;
	}
	
//	public Window getHiddenTopWindow()
//	{
//		return hiddenTopWindow;
//	}
	
	public Rectangle getWindowSpace()
	{
		if( masterFrame != null ) {
			return new Rectangle( 0, 0, masterFrame.getWidth(), masterFrame.getHeight() );
		} else {
			return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		}
	}
	
	public void place( AbstractWindow w, float halign, float valign )
	{
		final Rectangle sr = getWindowSpace();
		final Dimension wd = w.getSize();
		w.setLocation( new Point(
			(int) (sr.x + halign * (sr.width - wd.width)),
		   (int) (sr.y + valign * (sr.height - wd.height)) ));
	}
	
//	public Action getCollectAction()
//	{
//		return actionCollect;
//	}
	
	private static Rectangle stringToRectangle( String value )
	{
		Rectangle				rect	= null;
		final StringTokenizer	tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				rect	= new Rectangle( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ),
										 Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return rect;
	}

	private static String rectangleToString( Rectangle value )
	{
		return( value != null ? (value.x + " " + value.y + " " + value.width + " " + value.height) : null );
	}

	protected Rectangle calcOuterBounds()
	{
		final Rectangle	outerBounds;
		final boolean	isMacOS = System.getProperty( "os.name" ).indexOf( "Mac OS" ) >= 0;
		
		if( desktop == null ) {
			outerBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
			if( isMacOS ) {
				outerBounds.y     += 22;
				outerBounds.width -= 80;
				outerBounds.height-= 22;
			}
		} else {
			outerBounds = new Rectangle( 0, 0, desktop.getWidth(), desktop.getHeight() );
		}
		return outerBounds;
	}

	protected void collect( AbstractWindow w, Rectangle outerBounds )
	{
		final boolean	adjustLeft, adjustTop;
		final Rectangle	winBounds;
		boolean			adjustRight, adjustBottom;
		
		winBounds	= w.getBounds();
		adjustLeft	= winBounds.x < outerBounds.x;
		adjustTop	= winBounds.y < outerBounds.y;
		adjustRight	= (winBounds.x + winBounds.width) > (outerBounds.x + outerBounds.width);
		adjustBottom= (winBounds.y + winBounds.height) > (outerBounds.y + outerBounds.height);
		
		if( !(adjustLeft || adjustTop || adjustRight || adjustBottom) ) return;

		if( adjustLeft ) {
			winBounds.x = outerBounds.x;
			adjustRight	= (winBounds.x + winBounds.width) > (outerBounds.x + outerBounds.width);
		}
		if( adjustTop ) {
			winBounds.y = outerBounds.y;
			adjustBottom= (winBounds.y + winBounds.height) > (outerBounds.y + outerBounds.height);
		}
		if( adjustRight ) {
			winBounds.x = Math.max( outerBounds.x, outerBounds.x + outerBounds.width - winBounds.width );
			adjustRight	= (winBounds.x + winBounds.width) > (outerBounds.x + outerBounds.width);
			if( adjustRight && w.isResizable() ) winBounds.width = outerBounds.width;
		}
		if( adjustBottom ) {
			winBounds.y = Math.max( outerBounds.y, outerBounds.y + outerBounds.height - winBounds.height );
			adjustBottom= (winBounds.y + winBounds.height) > (outerBounds.y + outerBounds.height);
			if( adjustBottom && w.isResizable() ) winBounds.height = outerBounds.height;
		}
		w.setBounds( winBounds );
	}

	public static void showDialog( Dialog dlg )
	{
		final BasicWindowHandler wh = (BasicWindowHandler) AbstractApplication.getApplication().getWindowHandler();
		wh.instShowDialog( dlg );
	}
	
	public static int showDialog( JOptionPane op, Component parent, String title )
	{
		final JDialog	dlg;
		final Object	value;
		final int		result;
		
		dlg = op.createDialog( parent, title );
		showDialog( dlg );
		value = op.getValue();
		if( value == null ) {
			result = JOptionPane.CLOSED_OPTION;
		} else {
			final Object[] options = op.getOptions();
			if( options == null ) {
				if( value instanceof Integer ) {
					result = ((Integer) value).intValue();
				} else {
					result = JOptionPane.CLOSED_OPTION;
		       	}
			} else {
				int i;
				for( i = 0; i < options.length; i++ ) {
			        if( options[ i ].equals( value )) break;
				}
				result = i < options.length ? i : JOptionPane.CLOSED_OPTION;
			}
		}
		return result;
	}

	public static void showErrorDialog( Component component, Exception exception, String title )
	{
		final StringBuffer	strBuf  = new StringBuffer( GUIUtil.getResourceString( "errException" ));
		final JOptionPane	op;
		String				message = exception.getClass().getName() + " - " + exception.getLocalizedMessage();
		StringTokenizer		tok;
		int					lineLen = 0;
		String				word;
		String[]			options = { GUIUtil.getResourceString( "buttonOk" ),
										GUIUtil.getResourceString( "optionDlgStack" )};
	
		if( message == null ) message = exception.getClass().getName();
		tok = new StringTokenizer( message );
		strBuf.append( ":\n" );
		while( tok.hasMoreTokens() ) {
			word = tok.nextToken();
			if( lineLen > 0 && lineLen + word.length() > 40 ) {
				strBuf.append( "\n" );
				lineLen = 0;
			}
			strBuf.append( word );
			strBuf.append( ' ' );
			lineLen += word.length() + 1;
		}
		op = new JOptionPane( strBuf.toString(), JOptionPane.ERROR_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, options[ 0 ]);
		if( showDialog( op, component, title ) == 1 ) {
			exception.printStackTrace();
		}
	}

	private void instShowDialog( Dialog dlg )
	{
//		System.out.println( "instShowDialog" );
		
		final AbstractWindow	w;
		final List				wasOnTop	= new ArrayList();
		final boolean			modal		= dlg.isModal() && (fph != null);
		AbstractWindow			w2;
//boolean gaga = false;
		
		// temporarily disable alwaysOnTop
		if( !internalFrames && floating ) {
			for( Iterator iter = getWindows(); iter.hasNext(); ) {
				w2 = (AbstractWindow) iter.next();
				if( GUIUtil.isAlwaysOnTop( w2.getWindow() )) {
//gaga = true;
//break;
					wasOnTop.add( w2 );
					GUIUtil.setAlwaysOnTop( w2.getWindow(), false );
				}
			}
		}
		try {
			w = new AppWindow( dlg );
			w.init();  // calls addWindow
//			((AppWindow) w).gaga();
				
			// --- modal interruption ---
			if( modal ) fph.addModalDialog(); // this shit is necessary because java.awt.FileDialog doesn't fire windowActivated ...
//			if( gaga ) GUIUtil.setAlwaysOnTop( dlg, true );
			w.setVisible( true );
			if( modal ) fph.removeModalDialog();
			
	//		wh.removeWindow( w, null );
			w.dispose();	// calls removeWindow

		} finally { // make sure to restore original state
			for( int i = 0; i < wasOnTop.size(); i++ ) {
				w2 = (AbstractWindow) wasOnTop.get( i );
//				System.out.println( "wasOnTop " + i + " : " + w2.getClass().getName() );
				GUIUtil.setAlwaysOnTop( w2.getWindow(), true );
			}
		}
	}

	// -------------------- internal classes --------------------
	private static class MasterFrame
	extends JFrame
	implements AbstractWindow
	{
		private JMenuBar bar = null;
		
		protected MasterFrame()
		{
			super();
			
			setBounds( GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() );
		}
		
		public void init() { /* empty */ }
		
		protected void setDefaultMenuBar( JMenuBar m )
		{
			bar = m;
			setJMenuBar( null );
		}
		
		public void setJMenuBar( JMenuBar m )
		{
			super.setJMenuBar( m == null ? bar : m );
		}
		
		public void revalidate()
		{
			getRootPane().revalidate();
		}
		
		public void setDirty( boolean b ) { /* empty */ }
		
		public ActionMap getActionMap()
		{
			return getRootPane().getActionMap();
		}

		public InputMap getInputMap( int mode )
		{
			return getRootPane().getInputMap( mode );
		}

		public void addListener( Listener l )
		{
			WindowListenerWrapper.add( l, this );
		}
		
		public void removeListener( Listener l )
		{
			WindowListenerWrapper.remove( l, this );
		}
		
		public boolean isFloating()
		{
			return false;
		}
		
		public Component getWindow()
		{
			return this;
		}
	}
	
	private class ActionCollect
	extends AbstractAction
	{
		protected ActionCollect( String text )
		{
			super( text );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			final Rectangle	outerBounds = calcOuterBounds();

			for( Iterator iter = getWindows(); iter.hasNext(); ) {
				collect( (AbstractWindow) iter.next(), outerBounds );
			}
		}
	}
}