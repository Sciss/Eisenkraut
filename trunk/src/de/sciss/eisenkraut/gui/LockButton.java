/*
 *  LockButton.java
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
 *		06-Nov-05	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import de.sciss.gui.TiledImage;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 04-May-06
 */
public class LockButton
extends JComponent
{
	private static TiledImage	imgLockIcons	= null;

	private final Icon			icnLocked, icnLockedD, icnUnlocked, icnUnlockedD;
	
	private boolean				locked			= false;
	private	ActionListener		al				= null;

	public LockButton()
	{
		this( false );
	}

	public LockButton( boolean grayscaleUnlock )
	{
		super();

		if( imgLockIcons == null ) {
			imgLockIcons = new TiledImage( getClass().getResource( "lock.png" ), 16, 16 );
		}
		icnLocked	= imgLockIcons.createIcon( 0, 0 );
		icnLockedD	= imgLockIcons.createIcon( 0, 1 );
		icnUnlocked = imgLockIcons.createIcon( grayscaleUnlock ? 2 : 1, 0 );
		icnUnlockedD= imgLockIcons.createIcon( grayscaleUnlock ? 2 : 1, 1 );

		final Dimension d		= new Dimension( 16, 16 );

		setPreferredSize( d );
		setMinimumSize( d );
		setMaximumSize( d );
		
		addMouseListener( new MouseAdapter() {
			public void mousePressed( MouseEvent e )
			{
				if( isEnabled() ) {
					setLocked( !isLocked() );
					fireActionPerformed();
				}
			}
		});
	}
	
	public boolean isLocked()
	{
		return locked;
	}
	
	public void setLocked( boolean locked )
	{
		this.locked = locked;
		repaint();
	}
	
	/**
	 *	Registers a new listener to be informed
	 *	about lock switches. Whenever the user
	 *	switches the lock state, an <code>ActionEvent</code> is fired
	 *	and delivered to all registered listeners.
	 *
	 *	@param	l	the listener to register
	 */
	public synchronized void addActionListener( ActionListener l )
	{
		al = AWTEventMulticaster.add( al, l);
	}
	 
	/**
	 *	Unregisters a new listener from being informed
	 *	about lock switches.
	 *
	 *	@param	l	the listener to unregister
	 */
    public synchronized void removeActionListener( ActionListener l )
	{
		al = AWTEventMulticaster.remove( al, l);
	}
	
	private void fireActionPerformed()
	{
        final ActionListener l = al;
		if( l != null ) {
			l.actionPerformed( new ActionEvent( this, ActionEvent.ACTION_PERFORMED, null ));
		}
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
	
		final Icon	icn;
	
		if( isEnabled() ) {
			icn = locked ? icnLocked : icnUnlocked;
		} else {
			icn = locked ? icnLockedD : icnUnlockedD;
		}
		icn.paintIcon( this, g, (getWidth() - 16) >> 1, (getHeight() - 16) >> 1 );
	}
}
