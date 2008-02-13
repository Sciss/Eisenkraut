//
//  PlugInWindow.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 14.10.06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package de.sciss.eisenkraut.net;

import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JComponent;
//import javax.swing.JScrollPane;
//import javax.swing.JComponent;
import javax.swing.JPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.ShowWindowAction;

//import de.sciss.swingosc.ContentPane;
//import de.sciss.swingosc.ScrollPane;

public class PlugInWindow
extends AppWindow
{
	public static final int	FLAG_UNDECORATED	= 0x01;
	public static final int	FLAG_SCROLLPANE		= 0x02;
	public static final int	FLAG_NORESIZE		= 0x04;

	private final ShowWindowAction	actionShowWindow;
	private final BasicMenuFactory	mf;
	
	private final Map				winL		= new HashMap();
//	private final Map				winFocusL	= new HashMap();

	private final JComponent		topView;

	public PlugInWindow( String title, Rectangle cocoaBounds, int flags )
	{
		super( SUPPORT );
		actionShowWindow	= new ShowWindowAction( this );
		mf					= ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory();
		mf.addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!
		init();
		setTitle( title );	// needs to be after init. WHY?

//		if( (flags & FLAG_UNDECORATED) != 0 ) {
//			setUndecorated( true );
//		}
		if( (flags & FLAG_NORESIZE) != 0 ) {
			setResizable( false );
		}
		try {
			topView		= (JComponent) Class.forName( "de.sciss.swingosc.ContentPane" ).getConstructor( new Class[] { Boolean.TYPE }).newInstance( new Object[] { new Boolean( (flags & FLAG_SCROLLPANE) == 0) });
			if( (flags & FLAG_SCROLLPANE) != 0 ) {
//				topView		= new ContentPane( false );
//				final JScrollPane scrollPane = new ScrollPane( topView ); // ...SCROLLBAR_AS_NEEDED
				final JComponent scrollPane = (JComponent) Class.forName( "de.sciss.swingosc.ScrollPane" ).getConstructor( new Class[] { Component.class }).newInstance( new Object[] { topView });
//				scrollPane.setViewportBorder( null );
//				scrollPane.setBorder( null );
				setContentPane( scrollPane );
			} else {
//				topView		= new ContentPane( true );
				setContentPane( topView );
			}
		}
		catch( Exception e ) {
			mf.removeFromWindowMenu( actionShowWindow );
			e.printStackTrace();
			throw new IllegalStateException();
		}

		final JPanel topView = new JPanel();
		final Container c = getContentPane();
		c.add( topView );
		topView.setPreferredSize( cocoaBounds.getSize() );
		pack();	// frame is made displayable
		c.remove( topView );
		final Rectangle screenBounds = getWindow().getGraphicsConfiguration().getBounds();
		final Insets insets = getInsets();
		setLocation( new Point(
		    screenBounds.x + cocoaBounds.x - insets.left,
		    (screenBounds.y + screenBounds.height) - (cocoaBounds.y + cocoaBounds.height) - insets.top ));
	}
	
//	public JComponent getTopView()
//	{
//		return topView;
//	}

	public void setCocoaBounds( Rectangle r )
	{
		final Rectangle	screenBounds	= getWindow().getGraphicsConfiguration().getBounds();
		final Insets	insets			= getInsets();
		
		setBounds( new Rectangle(
		    screenBounds.x + r.x - insets.left,
		    (screenBounds.y + screenBounds.height) - (r.y + r.height) - insets.top,
		    r.width + (insets.left + insets.right),
		    r.height + (insets.top + insets.bottom) ));
	}
	
	public void addComponentListener( ComponentListener l )
	{
		getWindow().addComponentListener( l );
	}
	
	public void removeComponentListener( ComponentListener l )
	{
		getWindow().removeComponentListener( l );
	}

	protected boolean alwaysPackSize()
	{
		return false;
	}
	
	public void setTitle( String title )
	{
		super.setTitle( title );
		actionShowWindow.putValue( Action.NAME, title );
	}

	public void dispose()
	{
		mf.removeFromWindowMenu( actionShowWindow );
		actionShowWindow.dispose();
		super.dispose();
	}
	
	private WindowEvent windowEvent( Event e )
	{
//		return new WindowEvent( e.getWindow(), e.getID() );
// THROWS NULL SOURCE:
//		return new WindowEvent( null, e.getID() );	// dirty

		return null;	// extra cheesy
	}
	
	public void addWindowListener( final WindowListener wl )
	{
		final Listener l;
		
		l = new Adapter() {
			public void windowOpened( Event e )
			{
				wl.windowOpened( windowEvent( e ));
			}

			public void windowClosing( Event e )
			{
				wl.windowClosing( windowEvent( e ));
			}

			public void windowClosed( Event e )
			{
				wl.windowClosed( windowEvent( e ));
			}

			public void windowIconified( Event e )
			{
				wl.windowIconified( windowEvent( e ));
			}
			
			public void windowDeiconified( Event e )
			{
				wl.windowIconified( windowEvent( e ));
			}

			public void windowActivated( Event e )
			{
				wl.windowIconified( windowEvent( e ));
			}

			public void windowDeactivated( Event e )
			{
				wl.windowIconified( windowEvent( e ));
			}
		};
		
		addListener( l );
		winL.put( wl, l );
	}
	
	public void removeWindowListener( WindowListener wl )
	{
		final Listener l = (Listener) winL.remove( wl );
		removeListener( l );
	}
	
	public void addWindowFocusListener( WindowFocusListener l )
	{
		// XXX nothing
	}

	public void removeWindowFocusListener( WindowFocusListener l )
	{
		// XXX nothing
	}
}