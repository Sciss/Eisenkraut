/*
 *  PeakMeterCaption.java
 *  (de.sciss.gui package)
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
 *		23-Nov-07	created (moved from EisK LevelMeterCaption)
 */

package de.sciss.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

public class PeakMeterCaption
extends JComponent
{
	private static final float[]	MAJOR_TICKS 	= { 0.0f, 0.05f, 0.15f, 0.225f, 0.3f, 0.4f,  0.5f, 0.625f, 0.75f, 0.875f, 1f };
	private static final String[]	LABELS	 		= { "60", "50",  "40",  "35",   "30", "25", "20",  "15",   "10", "5",     "0" };
	private static final Stroke		strkMajorTicks	= new BasicStroke( 1f ); // 0.012f
	private static final Stroke		strkMinorTicks	= new BasicStroke( 0.5f ); // 0.004f
//	private static final Color		DEFAULT_FG		= new Color( 0x00, 0x00, 0x00, 0x7F );
	
	private int 					recentWidth		= -1;
	private int 					recentHeight	= -1;

	private static final GeneralPath SHP_MAJOR_TICKS, SHP_MINOR_TICKS;
	private Shape shpMajorTicks, shpMinorTicks, shpLabels;
	
	static {
		SHP_MAJOR_TICKS = new GeneralPath();
		for( int i = 0; i < MAJOR_TICKS.length; i++ ) {
			SHP_MAJOR_TICKS.moveTo( 0f, 1f - MAJOR_TICKS[ i ]);
			SHP_MAJOR_TICKS.lineTo( 1f, 1f - MAJOR_TICKS[ i ]);
		}
		SHP_MINOR_TICKS = new GeneralPath();
		for( int i = 0; i < 20; i++ ) {
			if( (i % 5) == 0 ) continue;
			SHP_MINOR_TICKS.moveTo( 0.3f, i * 0.025f );
			SHP_MINOR_TICKS.lineTo( 1f, i * 0.025f );
		}
	}
	
	public PeakMeterCaption()
	{
		super();
		setPreferredSize( new Dimension( 20, 20 ));
//		setForeground( DEFAULT_FG );
		setOpaque( true );
		setFont( new Font( "SansSerif", Font.PLAIN, 12 ));

		addPropertyChangeListener( "border", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				recentHeight = -1;
				repaint();
			}
		});
	}
	
	public void paintComponent( Graphics g )
	{
//		super.paintComponent( g );
		g.setColor( Color.black );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		g.setColor( Color.white );
		
		final Graphics2D 		g2		= (Graphics2D) g;
//		final AffineTransform	atOrig	= g2.getTransform();
		final int				w		= getWidth();
		final int				h		= getHeight();
//		final int				wm		= getWidth() - 1;
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//		g2.setRenderingHint( RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE );
//		g2.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR );
//		g2.setRenderingHint( RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF );

//		g2.translate( 0, 2 );
		if( (w != recentWidth) || (h != recentHeight) ) {
			recentWidth		= w;
			recentHeight	= h;
			final AffineTransform at =new AffineTransform();
			final Insets insets = getInsets();
			final int wi	= w - (insets.left + insets.right);
			final int hi	= h - (insets.top + insets.bottom);
			final float sw	= wi * 0.3f;
			final Font fnt = getFont();
			at.translate( wi - sw + insets.left, insets.top );
//			System.out.println( "h " + h + ", top " + insets.top + ", bottom " + insets.bottom + " hi " + hi );
			at.scale( sw, hi - 1 );
			shpMajorTicks	= at.createTransformedShape( SHP_MAJOR_TICKS );
			shpMinorTicks	= at.createTransformedShape( SHP_MINOR_TICKS );
			final FontRenderContext frc = g2.getFontRenderContext();
			final GeneralPath gp = new GeneralPath();
			final double lbScale = (hi - 1) * 0.004;
			final GlyphVector[] gv = new GlyphVector[ LABELS.length ];
			final Rectangle2D[] gvb = new Rectangle2D[ LABELS.length ];
			float maxWidth = 0f;
			for( int i = 0; i < LABELS.length; i++ ) {
				gv[ i ]		= fnt.createGlyphVector( frc, LABELS[ i ]);
				gvb[ i ]	= gv[ i ].getLogicalBounds(); // .getVisualBounds();
				maxWidth = Math.max( maxWidth, (float) gvb[ i ].getWidth() );
			}
			for( int i = 0; i < gv.length; i++ ) {
				gp.append( gv[ i ].getOutline(
					maxWidth - (float) gvb[ i ].getWidth(),
					(1f - MAJOR_TICKS[ i ]) * 250 - (float) gvb[ i ].getCenterY() ), false );
			}
			at.setToTranslation( insets.left, insets.top );
			at.scale( lbScale, lbScale );
			shpLabels = at.createTransformedShape( gp );
		}
//		g2.scale( getWidth() - 1, getHeight() - 4 );
		g2.setStroke( strkMajorTicks );
		g2.draw( shpMajorTicks );
		g2.setStroke( strkMinorTicks );
		g2.draw( shpMinorTicks );
		g2.fill( shpLabels );
//		for( int i = 0; i < MAJOR_TICKS.length; i++ ) {
//			g2.drawLine( 0, MAJOR_TICKS[ i ], wm, MAJOR_TICKS[ i ]);
//		}
//		g2.setTransform( atOrig );
	}
}
