/*
 *  RoundedBorder.java
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
 *  Changelog:
 *		03-Dec-05	extract from TransportToolBar ; again uses TimeFormat
 */

package de.sciss.eisenkraut.gui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.border.*;

/**
 *	A border looking like aqua's search-field border
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.63, 03-Dec-05
 */
public class RoundedBorder
extends AbstractBorder
{
	private final int		radius		= 11;
	private final int		diameter	= radius << 1;
	private final Insets	insets		= new Insets( 3, radius, 1, radius );

	private static final Color colrDark		= new Color( 0x00, 0x00, 0x00, 0x88 );
	private static final Color colrLight	= new Color( 0xFF, 0xFF, 0xFF, 0xD8 );
//	private static final Color colrLight	= new Color( 0xFF, 0x00, 0x00, 0xFF );
	private static final Color colrDark2	= new Color( 0x00, 0x00, 0x00, 0x38 );
	private static final Color colrClearD	= new Color( 0x00, 0x00, 0x00, 0x00 );
	private static final Color colrClearL	= new Color( 0xFF, 0xFF, 0xFF, 0x00 );
	private static final Stroke	strkOutline	= new BasicStroke( 1.0f );
	private static final Stroke	strkInline	= new BasicStroke( 2.0f );
	
	private Color	colrBg		= Color.white;
	private Paint	pntInline, pntOutlineT, pntOutlineB;

	private Shape	shpBg, shpInline, shpOutline;

	private int		recentWidth		= -1;
	private int		recentHeight	= -1;

	public RoundedBorder()
	{
		super();
	}
	
	public RoundedBorder( Color c )
	{
		this();
		setColor( c );
	}
	
	public void setColor( Color c )
	{
		this.colrBg	= c;
	}
	
	public Insets getBorderInsets( Component c )
	{
		return new Insets( insets.top, insets.left, insets.bottom, insets.right );
	}
	
	public Insets getBorderInsets( Component c, Insets insets )
	{
		insets.top		= this.insets.top;
		insets.left		= this.insets.left;
		insets.bottom	= this.insets.bottom;
		insets.right	= this.insets.right;
		return insets;
	}
	
	public void paintBorder( Component c, Graphics g, int x, int y, int width, int height )
	{
		final Graphics2D		g2			= (Graphics2D) g;
		final AffineTransform	atOrig		= g2.getTransform();
	
		g2.translate( x, y );
	
		if( (width != recentWidth) || (height != recentHeight) ) {
			if( height != recentHeight ) {
				final int hh	= height >> 1;
				pntOutlineT		= new GradientPaint( 0, 0, colrDark, 0, hh, colrClearD );
				pntOutlineB		= new GradientPaint( 0, hh, colrClearL, 0, height - 2, colrLight );
				pntInline		= new GradientPaint( 0, 0, colrDark2, 0, hh, colrClearD );
			}
			
			final RectangularShape	r	= new RoundRectangle2D.Float( 0.5f, 0, width - 1, height, diameter, diameter );
			final RectangularShape	r2	= new RoundRectangle2D.Float( 0.5f, 0, width - 2, height - 1, diameter, diameter );
			final Area				a	= new Area( r );
			a.subtract( new Area( new Rectangle2D.Float( insets.left, insets.top,
				width - insets.left - insets.right, height - insets.top - insets.bottom )));

			shpOutline		= strkOutline.createStrokedShape( r2 );
			shpInline		= strkInline.createStrokedShape( r2 );
			shpBg			= a;

			recentWidth		= width;
			recentHeight	= height;
		}
							
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.setPaint( colrBg );
		g2.fill( shpBg );
		g2.setPaint( pntOutlineT );
		g2.fill( shpOutline );
		g2.setPaint( pntOutlineB );
		g2.fill( shpOutline );
		g2.translate( 0, 1 );
		g2.setPaint( pntInline );
		g2.fill( shpInline );

		g2.setTransform( atOrig );
	}
}