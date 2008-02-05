/*
 *  PeakMeterCaption.java
 *  (de.sciss.gui package)
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
 *  Change log:
 *		23-Nov-07	created (moved from EisK LevelMeterCaption)
 *		21-Dec-07	added horizontalAlignment, paintLabels
 */

package de.sciss.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
//import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 *	@version	0.11, 21-Dec-07
 *	@author		Hanns Holger Rutz
 */

public class PeakMeterCaption
extends JComponent
implements SwingConstants, PropertyChangeListener
{
	private static final float[]	MAJOR_TICKS 	= { 0.0f, 0.05f, 0.15f, 0.225f, 0.3f, 0.4f,  0.5f, 0.625f, 0.75f, 0.875f, 1f };
	private static final String[]	LABELS	 		= { "60", "50",  "40",  "35",   "30", "25", "20",  "15",   "10", "5",     "0" };
	private static final Stroke		strkMajorTicks	= new BasicStroke( 1f ); // 0.012f
	private static final Stroke		strkMinorTicks	= new BasicStroke( 0.5f ); // 0.004f
//	private static final Color		DEFAULT_FG		= new Color( 0x00, 0x00, 0x00, 0x7F );
	private static final  float 	MAJOR_W			= 5.25f;
	private static final  float 	MINOR_W			= 3.5f;
	
	private int						hAlign			= RIGHT;
	private boolean					paintLabels		= true;
	
	private int 					recentWidth		= -1;
	private int 					recentHeight	= -1;

//	private static final GeneralPath SHP_MAJOR_TICKS, SHP_MINOR_TICKS;
	private Shape shpMajorTicks, shpMinorTicks, shpLabels;
	
	private int						ascent, descent, labW;
	
	private int						ticks			= 0;

	//	static {
//		SHP_MAJOR_TICKS = new GeneralPath();
//		for( int i = 0; i < MAJOR_TICKS.length; i++ ) {
//			SHP_MAJOR_TICKS.moveTo( 0f, 1f - MAJOR_TICKS[ i ]);
//			SHP_MAJOR_TICKS.lineTo( 1f, 1f - MAJOR_TICKS[ i ]);
//		}
//		SHP_MINOR_TICKS = new GeneralPath();
//		for( int i = 0; i < 20; i++ ) {
//			if( (i % 5) == 0 ) continue;
//			SHP_MINOR_TICKS.moveTo( 0.3f, i * 0.025f );
//			SHP_MINOR_TICKS.lineTo( 1f, i * 0.025f );
//		}
//	}
	
	public PeakMeterCaption()
	{
		super();
		setPreferredSize( new Dimension( 20, 20 ));
//		setForeground( DEFAULT_FG );
		setOpaque( true );
		setFont( new Font( "SansSerif", Font.PLAIN, 12 ));
		recalcPrefSize();

		addPropertyChangeListener( "border", this );
		addPropertyChangeListener( "font", this );
	}
	
	public void setTicks( int ticks )
	{
		this.ticks = ticks;
		recalcPrefSize();
	}

	public int getAscent()
	{
		return ascent;
	}
	
	public int getDescent()
	{
		return descent;
	}
	
	private void recalcPrefSize()
	{
		final Insets	insets = getInsets();
		final Dimension	d;
		
		if( paintLabels ) {
			final Font					fnt	= getFont();
//			final FontMetrics			fm	= getFontMetrics( fnt );
			final Window				w	= SwingUtilities.getWindowAncestor( this );
			final GraphicsConfiguration	gc;
			final FontRenderContext		frc;
			Rectangle2D					b;
			float						labH;
			
			if( w != null ) {
				gc	= w.getGraphicsConfiguration();
			} else {
				gc	= GraphicsEnvironment.getLocalGraphicsEnvironment().
                		getDefaultScreenDevice().getDefaultConfiguration();
			}
			frc		= new FontRenderContext( gc.getNormalizingTransform(), true, true );
			labW	= 0;
			labH	= 0f;
			for( int i = 0; i < LABELS.length; i++ ) {
				b		= fnt.createGlyphVector( frc, LABELS[ i ]).getLogicalBounds();
				labW	= Math.max( labW, (int) (b.getWidth() + 0.5) );
				labH	= Math.max( labH, (float) b.getHeight() );
			}
			labW   += 2;
			ascent	= (int) (labH / 2); // Math.ceil( labH / 2 );
			descent	= ascent;
		} else {
			labW	= 0;
			ascent	= 0;
			descent	= 0;
		}
		
		d = new Dimension( labW + 5 + insets.left + insets.right, ticks <= 0 ? getPreferredSize().height : (ticks * 2 - 1 + insets.top + insets.bottom) );
		setPreferredSize( d );
		setMinimumSize( new Dimension( d.width, 2 + insets.top + insets.bottom ));
		setMaximumSize( new Dimension( d.width, getMaximumSize().height ));
	}

	public void setHorizontalAlignment( int alignment )
	{
		if( hAlign == alignment ) return;
		
		switch( alignment ) {
		case LEFT:
		case RIGHT:
		case CENTER:
			break;
		default:
			throw new IllegalArgumentException( String.valueOf( alignment ));
		}

		hAlign			= alignment;
System.out.println( "align : " + hAlign );
		recentHeight	= -1;
		repaint();
	}
	
	public void setPaintLabels( boolean b )
	{
		if( paintLabels != b ) {
			paintLabels 	= b;
			recentHeight	= -1;
			repaint();
		}
	}
	
	public void propertyChange( PropertyChangeEvent e )
	{
		recentHeight = -1;
		recalcPrefSize();
		repaint();
	}

	public void paintComponent( Graphics g )
	{
		g.setColor( Color.black );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		g.setColor( Color.white );
		
		final Graphics2D 		g2		= (Graphics2D) g;
		final int				w		= getWidth();
		final int				h		= getHeight();
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

recalc:	if( (w != recentWidth) || (h != recentHeight) ) {
			recentWidth		= w;
			recentHeight	= h;
			
			final float xw;
			final int	labX;
			
			switch( hAlign ) {
			case LEFT:
			case CENTER:
				xw		= 0f;
				labX	= (int) (MAJOR_W + 3);
				break;
			case RIGHT:
				xw		= 1f;
				labX	= 0;
				break;
			default:
				assert false : hAlign;
				xw		= 0f;
				labX	= 0;
				break;
			}
				
			// ------------ recalculate ticks ------------
			
			final AffineTransform	at		= new AffineTransform();
			final Insets			insets	= getInsets();
			final int				wi		= w - (insets.left + insets.right);
			final int				hi		= h - (insets.top + insets.bottom + ascent + descent);
			final int				him		= hi - 1;
			final GeneralPath		gpMajT	= new GeneralPath();
			final GeneralPath		gpMinT	= new GeneralPath();
			final float				minX	= (MAJOR_W - MINOR_W) * xw;
			at.translate( (wi - MAJOR_W) * xw + insets.left, insets.top + ascent );
//			System.out.println( "h " + h + ", top " + insets.top + ", bottom " + insets.bottom + " hi " + hi );
//			at.scale( majW, hi - 1 );
			for( int i = 0; i < MAJOR_TICKS.length; i++ ) {
				gpMajT.moveTo(   0f, (1f - MAJOR_TICKS[ i ]) * him );
				gpMajT.lineTo( MAJOR_W, (1f - MAJOR_TICKS[ i ]) * him );
			}
			for( int i = 0; i < 20; i++ ) {
				if( (i % 5) == 0 ) continue;
				gpMinT.moveTo( minX, i * 0.025f * him);
				gpMinT.lineTo( minX + MINOR_W, i * 0.025f * him );
			}
			shpMajorTicks	= at.createTransformedShape( gpMajT );
			shpMinorTicks	= at.createTransformedShape( gpMinT );
			
			// ------------ recalculate labels ------------
			if( !paintLabels ) break recalc;
			
			final FontRenderContext	frc 		= g2.getFontRenderContext();
			final GeneralPath		gp			= new GeneralPath();
			final double			lbScale 	= (hi - 1) * 0.004;
			final GlyphVector[]		gv			= new GlyphVector[ LABELS.length ];
			final Rectangle2D[]		gvb 		= new Rectangle2D[ LABELS.length ];
			final Font				fnt			= getFont();
			float					maxWidth	= 0f;
			for( int i = 0; i < LABELS.length; i++ ) {
				gv[ i ]		= fnt.createGlyphVector( frc, LABELS[ i ]);
				gvb[ i ]	= gv[ i ].getLogicalBounds(); // .getVisualBounds();
				maxWidth	= Math.max( maxWidth, (float) gvb[ i ].getWidth() );
			}
			for( int i = 0; i < gv.length; i++ ) {
				gp.append( gv[ i ].getOutline(
					(maxWidth - (float) gvb[ i ].getWidth()) * xw + 1.5f,
					(1f - MAJOR_TICKS[ i ]) * 250 - (float) gvb[ i ].getCenterY() ), false );
			}
			at.setToTranslation( insets.left + labX, insets.top + ascent );
			at.scale( lbScale, lbScale );
			shpLabels = at.createTransformedShape( gp );
		}
//		g2.scale( getWidth() - 1, getHeight() - 4 );
		g2.setStroke( strkMajorTicks );
		g2.draw( shpMajorTicks );
		g2.setStroke( strkMinorTicks );
		g2.draw( shpMinorTicks );
		if( paintLabels ) g2.fill( shpLabels );
	}
}
