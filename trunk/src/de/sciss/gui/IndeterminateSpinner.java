/*
 *  IndeterminateSpinner.java
 *  de.sciss.gui package
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
 *		26-Mar-07	created
 */
package de.sciss.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
//import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
//import javax.swing.Icon;
import javax.swing.JComponent;
//import javax.swing.JLabel;
import javax.swing.Timer;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;

public class IndeterminateSpinner
extends JComponent
implements DynamicListening, ActionListener //, Icon
{
	// Array.fill( 12, { arg val; val = ((12 - val)/12).squared * 0xA8 + 0x20; val = "0x" ++ val.round.asInteger.asHexString.copyToEnd( 6 ); "new Color( 0x00, 0x00, 0x00, "++val++" )" })
	private static final Color[] colours = {
		new Color( 0x00, 0x00, 0x00, 0xC8 ), new Color( 0x00, 0x00, 0x00, 0xAD ),
		new Color( 0x00, 0x00, 0x00, 0x95 ), new Color( 0x00, 0x00, 0x00, 0x7F ),
		new Color( 0x00, 0x00, 0x00, 0x6B ), new Color( 0x00, 0x00, 0x00, 0x59 ),
		new Color( 0x00, 0x00, 0x00, 0x4A ), new Color( 0x00, 0x00, 0x00, 0x3D ),
		new Color( 0x00, 0x00, 0x00, 0x33 ), new Color( 0x00, 0x00, 0x00, 0x2B ),
		new Color( 0x00, 0x00, 0x00, 0x25 ), new Color( 0x00, 0x00, 0x00, 0x21 )
	};
	private static final double		angle		= -Math.PI / 6;
	private static final int		delay		= 50;
	
	private final int				size;
	private final int				scale;
	private final RoundRectangle2D	r; //			= new RoundRectangle2D.Double( 0.39, -0.085, 0.48, 0.17, 0.15, 0.15 );
	private final Timer				timer;
	
	private int						phase		= 0;
	private boolean					active		= false;
	private boolean					listening	= false;

	public IndeterminateSpinner()
	{
		this( 32 );
	}
	
	public IndeterminateSpinner( int size )
	{
		super();
		this.size	= size;
		scale		= size >> 1; // 0.5 * size;
//		setIcon( this );
		timer		= new Timer( delay, this );
		r			= new RoundRectangle2D.Double( 0.39 * scale, -0.085 * scale, 0.48 * scale, 0.17 * scale, 0.15 * scale, 0.15 * scale );
		new DynamicAncestorAdapter( this ).addTo( this );
		setPreferredSize( new Dimension( size, size ));
	}
	
//	public int getIconHeight() { return size; }
//	public int getIconWidth()  { return size; }
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
//System.err.println( "w = " + getWidth() + "; h = " + getHeight() );
		if( active ) paintIcon( this, g, (getWidth() - size) >> 1, (getHeight() - size) >> 1 );
	}
	
	private void paintIcon( Component c, Graphics g, int x, int y )
	{
		final Graphics2D g2 = (Graphics2D) g;
		final AffineTransform atOrig = g2.getTransform();

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		
//		g2.translate( x + (size >> 1), y + (size >> 1) );
		g2.translate( x + scale + 0.5, y + scale + 0.5 );
//		g2.scale( scale, scale );
		for( int i = 0; i < 12; i++ ) {
			g2.setColor( colours[ (i + phase) % 12 ]);
//			r.setRect();
			g2.fill( r );
			g2.rotate( angle );
		}
		
		g2.setTransform( atOrig );
		
		phase = (phase + 1 ) % 12;
	}
	
	public void setActive( boolean onOff )
	{
		active = onOff;
		if( listening ) {
			if( active ) {
				timer.restart();
			} else {
				timer.stop();
				repaint();
			}
		}
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public void startListening()
	{
		listening = true;
		if( active ) timer.restart();
	}
	
	public void stopListening()
	{
		listening = false;
		timer.stop();
	}
	
	public void actionPerformed( ActionEvent e )
	{
		repaint();
	}
}
