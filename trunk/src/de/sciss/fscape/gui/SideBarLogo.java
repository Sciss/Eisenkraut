//
//  SideBarLogo.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 25.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

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