//
//  ShowWindowAction.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 14.10.06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package de.sciss.common;

import java.awt.event.ActionEvent;
import javax.swing.Action;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.gui.MenuAction;
import de.sciss.util.Disposable;

public class ShowWindowAction
extends MenuAction
implements Disposable
{
	private final AbstractWindow			w;
	private final AbstractWindow.Listener	l;
	private boolean							disposed	= false;

	public ShowWindowAction( AbstractWindow w )
	{
		super( null, null );
		this.w	= w;
		
		final Action enc_this = this;
		
		l = new AbstractWindow.Adapter() {
			public void windowActivated( AbstractWindow.Event e )
			{
				if( !disposed ) ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory().setSelectedWindow( enc_this );
			}
		};
		w.addListener( l );
	}

	public void actionPerformed( ActionEvent e )
	{
		w.setVisible( true );
		w.toFront();
	}
	
	public void dispose()
	{
		disposed = true;	// the listener might still be called!
		w.removeListener( l );
	}
}