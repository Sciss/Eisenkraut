//
//  PlugInWindow.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 14.10.06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package de.sciss.eisenkraut.net;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;

import de.sciss.app.AbstractApplication;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.ShowWindowAction;

public class PlugInWindow
extends AppWindow
{
	private final ShowWindowAction	actionShowWindow;
	private final BasicMenuFactory	mf;
	
	private final Map				winL		= new HashMap();
//	private final Map				winFocusL	= new HashMap();

	public PlugInWindow()
	{
		super( SUPPORT );
		actionShowWindow	= new ShowWindowAction( this );
		mf					= ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory();
		mf.addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!
		init();
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