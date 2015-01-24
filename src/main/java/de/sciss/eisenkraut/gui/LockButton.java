/*
 *  LockButton.java
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

import java.awt.AWTEventMulticaster;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JComponent;

import de.sciss.gui.TiledImage;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
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
	
	protected void fireActionPerformed()
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
