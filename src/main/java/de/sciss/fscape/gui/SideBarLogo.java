/*
 *  SideBarLogo.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2013 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.fscape.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 *	@version	0.1, 07-Dec-07
 */
public class SideBarLogo
extends JComponent
{
	private static int			width;
	private static int			height;
	private static final Color	colrBg	= new Color( 0x2C, 0x14, 0x21 );
	private static ImageIcon	icon	= null;
	
	public SideBarLogo()
	{
		super();
		
		if( icon == null ) {
			icon	= new ImageIcon( getClass().getResource( "fscape.png" ));
			width	= icon.getIconWidth();
			height	= icon.getIconHeight();
		}

		setMinimumSize( new Dimension( width, height ));
		setPreferredSize( new Dimension( width, getPreferredSize().height ));
		setMaximumSize( new Dimension( width, getMaximumSize().height ));
		setOpaque( true );
		
	}
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		g.setColor( colrBg );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		icon.paintIcon( this, g, 0, 0 );
	}
}