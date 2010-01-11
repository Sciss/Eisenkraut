/*
 *  PopupTriggerMonitor.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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
 *		24-Aug-06	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

import de.sciss.util.Disposable;

/**
 *	@version	0.70, 22-Mar-08
 *	@author		Hanns Holger Rutz
 */
public class PopupTriggerMonitor
implements ActionListener, MouseListener, Disposable
{
	private static final int	DEFAULT_DELAY	= 300;

	private JPopupMenu			pop				= null;
	private float				relx, rely;
	private List				collListeners	= null;		// lazy creation
	
	private final Component		c;
	private final Timer			timer;
	private final Object		sync			= new Object();

	private boolean				validPress		= false;

	public PopupTriggerMonitor( Component c )
	{
		this.c	= c;
		c.addMouseListener( this );
		timer	= new Timer( DEFAULT_DELAY, this );
		timer.setRepeats( false );
	}
	
	public void addListener( Listener l )
	{
		synchronized( sync ) {
			if( collListeners == null ) collListeners = new ArrayList();
			collListeners.add( l );
		}
	}

	public void removeListener( Listener l )
	{
		synchronized( sync ) {
			collListeners.remove( l );
		}
	}
	
	public Component getComponent()
	{
		return c;
	}
	
	public void setPopupMenu( JPopupMenu pop )
	{
		setPopupMenu( pop, 0f, 1f );
	}

	public void setPopupMenu( JPopupMenu pop, float relx, float rely )
	{
		this.pop	= pop;
		this.relx	= relx;
		this.rely	= rely;
	}
	
	public JPopupMenu getPopupMenu()
	{
		return pop;
	}
	
	public void dispose()
	{
		timer.stop();
//		timer	= null;
		pop		= null;
		c.removeMouseListener( this );
	}
	
	public void setDelay( int millis )
	{
		timer.setDelay( millis );
	}
	
	public int getDelay()
	{
		return timer.getDelay();
	}
	
	public void mousePressed( MouseEvent e )
	{
		if( e.isPopupTrigger() ) {
			dispatchTrigger();
		} else if( e.getButton() == MouseEvent.BUTTON1 ) {
			validPress = true;
			timer.restart();
		}
	}

	public void mouseReleased( MouseEvent e )
	{
		timer.stop();
		if( validPress ) {
			if( e.getComponent().contains( e.getPoint() )) {
				dispatchClick();
			}
			validPress = false;
		}
	}

	public void mouseEntered( MouseEvent e ) { /* ignore */ }
	public void mouseExited( MouseEvent e ) { /* ignore */ }
	public void mouseClicked( MouseEvent e ) { /* ignore */ }
	
	public void actionPerformed( ActionEvent e )
	{
		dispatchTrigger();
	}
	
	private void dispatchTrigger()
	{
		validPress = false;

		if( pop != null ) pop.show( c, (int) (c.getWidth() * relx), (int) (c.getHeight() * rely) );
	
		synchronized( sync ) {
			if( collListeners != null ) {
				for( int i = 0; i < collListeners.size(); i++ ) {
					((Listener) collListeners.get( i )).popupTriggered( this );
				}
			}
		}
	}

	private void dispatchClick()
	{
		synchronized( sync ) {
			if( collListeners != null ) {
				for( int i = 0; i < collListeners.size(); i++ ) {
					((Listener) collListeners.get( i )).componentClicked( this );
				}
			}
		}
	}
	
	public static interface Listener
	{
		public void popupTriggered( PopupTriggerMonitor m );
		public void componentClicked( PopupTriggerMonitor m );
	}
}
