/*
 *  PanoramaPanel.java
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
 *		23-Jul-05	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.56, 05-May-06
 */
 public class PanoramaPanel
extends JComponent
{
	private final AffineTransform	at			= new AffineTransform();
	private static final Insets		insets		= new Insets( 1, 1, 1, 1 );
	private static final Shape		shpCtrlIn	= new Ellipse2D.Double( -2, -2, 5, 5 );
	private static final Area		shpCtrlOut;
	private static final Paint		pntCtrlIn	= new Color( 0x00, 0x00, 0x00, 0x7F );
	private static final Paint		pntCtrlOut	= new Color( 0x00, 0x00, 0x00, 0x3F );
	private static final Paint		pntCtrlOutS	= new Color( 0x00, 0x00, 0xFF, 0x7F );

	private final java.util.List	outlines		= new ArrayList();
	private final java.util.List	tOutlines		= new ArrayList();
	private final java.util.List	outlinePaints	= new ArrayList();

	private final java.util.List	areas			= new ArrayList();
	private final java.util.List	tAreas			= new ArrayList();
	private final java.util.List	areaPaints		= new ArrayList();

	private final Point2D	ctrlPt	= new Point2D.Double( 0.0, 0.75 );

	private double recentRadius	= -1;
	private boolean recalc		= true;
	private boolean isDragging	= false;
	
	private final double startAngle, deltaAngle;
	private final int numSpots;
	private double azi, spread;
	
	private static final java.util.List	collListeners	= new ArrayList();
	
	static {
		shpCtrlOut		   = new Area( new Ellipse2D.Double( -7, -7, 15, 15 ));
		shpCtrlOut.subtract( new Area( new Ellipse2D.Double( -4, -4, 9, 9 )));
	}
				
	public PanoramaPanel( final int numSpots, double startAng )
	{
		super();
		
		if( startAng < 0 ) {
			startAng = 360 - ((-startAng) % 360);
		}
		this.startAngle = startAng % 360;
		this.deltaAngle	= 360.0 / numSpots;
		this.numSpots	= numSpots;
			
		outlines.add( new Ellipse2D.Double( -1.0, -1.0, 2.0, 2.0 ));
		outlinePaints.add( Color.black );
		outlines.add( new Ellipse2D.Double( -0.75, -0.75, 1.5, 1.5 ));
		outlinePaints.add( pntCtrlOut );
		
		double			angDeg, angRad, dx, dy;
		double			speakerWidth	= Math.max( 4, Math.min( 10, deltaAngle / 2 ));
		Arc2D			arc1 = new Arc2D.Double( -1.15, -1.15, 2.3, 2.3, 0, speakerWidth, Arc2D.PIE );
		Arc2D			arc2 = new Arc2D.Double( -1.05, -1.05, 2.1, 2.1, 0, speakerWidth, Arc2D.PIE );
//		GeneralPath		gen;
		Area			area;
		
		for( int i = 0; i < numSpots; i++ ) {
			angDeg	= startAngle + i * deltaAngle;
			angRad	= (-angDeg + 90) * Math.PI / 180;
			dx		= Math.cos( angRad );
			dy		= Math.sin( angRad );
			outlines.add( new Line2D.Double( 0.0, 0.0, dx, dy ));
			outlinePaints.add( pntCtrlOut );
			arc1.setAngleStart( angDeg - 90 - speakerWidth/2 );
			arc2.setAngleStart( angDeg - 90 - speakerWidth/2 );
			area	= new Area( arc1 );
			area.subtract( new Area( arc2 ));
			areas.add( area );
			areaPaints.add( pntCtrlIn );
		}
		
		setMinimumSize( new Dimension( 64, 64 ));
		setPreferredSize( new Dimension( 128, 128 ));
		setBorder( BorderFactory.createEmptyBorder( insets.left, insets.top, insets.bottom, insets.right ));
//		setBorder( BorderFactory.createMatteBorder( insets.left, insets.top, insets.bottom, insets.right, Color.green ));

		MouseInputAdapter	mia	= new MouseInputAdapter() {
			public void mousePressed( MouseEvent e )
			{
				final Point2D mousePt	= getVirtualMousePos( e );
				isDragging				= true;
				processDrag( mousePt, !e.isControlDown() );
			}

			public void mouseReleased( MouseEvent e )
			{
				isDragging		= false;
				repaint();
			}
			
			// x ^= radius, y ^= angleRad
			private Point2D getVirtualMousePos( MouseEvent e )
			{
				final double dx = ((e.getX() - insets.left) / recentRadius) - 1.15;
				final double dy = 1.15 - ((e.getY() - insets.top) / recentRadius);
			
				return new Point2D.Double( Math.min( 1.0, Math.sqrt( dx * dx + dy * dy )),
										   Math.atan2( dy, dx ));
			}

			public void mouseDragged( MouseEvent e )
			{
				if( isDragging ) processDrag( getVirtualMousePos( e ), !e.isControlDown() );
			}
			
			public void mouseMoved( MouseEvent e )
			{
				mouseDragged( e );
			}
			
			private void processDrag( Point2D mousePt, boolean snap )
			{
				double angDeg, temp, temp2; // , spreadDegH;
			
				if( snap ) {
					if( mousePt.getX() < 0.1 ) {
						mousePt.setLocation( 0.0, mousePt.getY() );
					} else if( Math.abs( mousePt.getX() - 0.75 ) < 0.1 ) {
						mousePt.setLocation( 0.75, mousePt.getY() );
					} else if( 1.0 - mousePt.getX() < 0.1 ) {
						mousePt.setLocation( 1.0, mousePt.getY() );
					}
					
					angDeg = -(mousePt.getY() * 180 / Math.PI) + 90;
					if( angDeg < 0 ) angDeg += 360;

					for( int i = 0; i < numSpots * 2; i++ ) {
						temp	= (startAngle + (deltaAngle * i) / 2) % 360;
						temp2	= Math.abs( temp - angDeg );
						if( temp2 > 180 ) temp2 = 360 - temp2;
						if( temp2 < 5 ) {
							mousePt.setLocation( mousePt.getX(), (-temp + 90) * Math.PI / 180 );
							break;
						}
					}
				}

				// azi + spread
				if( mousePt.getX() <= 0.75 ) {
					spread	= 1.0 - (mousePt.getX() / 0.75);
				} else {
					spread	= (0.75 - mousePt.getX()) / 0.25;
				}
				angDeg = -(mousePt.getY() * 180 / Math.PI) + 90;

				setAzimuthAndSpread( angDeg, spread );
				dispatchAction();
			}
		};
		
		addMouseListener( mia );
		addMouseMotionListener( mia );

//setCursor( new Cursor( Cursor.CROSSHAIR_CURSOR ));
	}
	
	public void beginDragging()
	{
		isDragging = true;
	}
	
	private void dispatchAction()
	{
		final ActionEvent e = new ActionEvent( this, ActionEvent.ACTION_PERFORMED, null );
	
		synchronized( collListeners ) {
			for( int i = 0; i < collListeners.size(); i++ ) {
				((ActionListener) collListeners.get( i )).actionPerformed( e );
			}
		}
	}
	
	public void addActionListener( ActionListener l )
	{
		synchronized( collListeners ) {
			collListeners.add( l );
		}
	}

	public void removeActionListener( ActionListener l )
	{
		synchronized( collListeners ) {
			collListeners.remove( l );
		}
	}
	
	private void recalcColours()
	{
		double temp, spreadDegH;
	
		if( spread >= 0.0 ) {
			spreadDegH	= spread * (1.0 - 2.0 / numSpots) + 2.0 / numSpots;
		} else {
			spreadDegH	= (2.0 + spread) / numSpots;
		}
		spreadDegH *= 180;
		
		for( int i = 0; i < numSpots; i++ ) {
			temp	= (startAngle + deltaAngle * i) % 360;
			temp	= Math.abs( temp - azi );
			if( temp > 180 ) temp = 360 - temp;
			temp	= Math.max( 0.0, 1.0 - temp / spreadDegH );
			areaPaints.set( i, new Color( (int) (temp * 0xC0), 0, 0, (int) ((temp + 1.0) * 0x7F) ));
		}
	}
	
	public void setAzimuthAndSpread( double azi, double spread )
	{
		this.spread		= Math.max( -1.0, Math.min( 1.0, spread ));
		this.azi		= azi < 0 ? 360 - ((-azi) % 360) : azi % 360;

		ctrlPt.setLocation( PanoramaPanel.aziAndSpreadToCtrlPoint( this.azi, this.spread ));

		recalcColours();
		if( isVisible() ) repaint();
	}
	
	public static Point2D aziAndSpreadToCtrlPoint( double azi, double spread )
	{
		final double r		= spread >= 0.0 ? (1.0 - spread) * 0.75 : 0.75 - spread * 0.25;
		final double angRad	= (-azi + 90) * Math.PI / 180;
	
		return new Point2D.Double( r * Math.cos( angRad ), r * Math.sin( angRad ));
	}
	
	public double getAzimuth()
	{
		return azi;
	}

	public double getSpread()
	{
		return spread;
	}
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D		g2				= (Graphics2D) g;
		final int				currentWidth	= getWidth() - insets.left - insets.right;
		final int				currentHeight	= getHeight() - insets.top - insets.bottom;
		final AffineTransform	oldAT			= g2.getTransform();
		final double			radius			= Math.min( currentWidth, currentHeight ) / 2.3;

		double trnsX, trnsY;
		
		if( (radius != recentRadius) || recalc ) {
			recentRadius	= radius;
			at.setToScale( radius, -radius );
			at.translate( 1.15, -1.15 );
			recalcTransforms();
		}
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.translate( insets.left, insets.top );
		for( int i = 0; i < tOutlines.size(); i++ ) {
			g2.setPaint( (Paint) outlinePaints.get( i ));
			g2.draw( (Shape) tOutlines.get( i ));
		}
		for( int i = 0; i < tAreas.size(); i++ ) {
			g2.setPaint( (Paint) areaPaints.get( i ));
			g2.fill( (Shape) tAreas.get( i ));
		}
				
		trnsX	= (ctrlPt.getX() + 1.15) * radius;
		trnsY	= (1.15 - ctrlPt.getY()) * radius;
		g2.translate( trnsX, trnsY );
		g2.setPaint( pntCtrlIn );
		g2.fill( shpCtrlIn );
		if( isDragging ) {
			g2.setPaint( pntCtrlOutS );
		} else {
			g2.setPaint( pntCtrlOut );
		}
		g2.fill( shpCtrlOut );
		g2.translate( -trnsX, -trnsY );

		g2.setTransform( oldAT );
	}

	private void recalcTransforms()
	{
		tOutlines.clear();
		for( int i = 0; i < outlines.size(); i++ ) {
			tOutlines.add( at.createTransformedShape( (Shape) outlines.get( i )));
		}
		tAreas.clear();
		for( int i = 0; i < areas.size(); i++ ) {
			tAreas.add( at.createTransformedShape( (Shape) areas.get( i )));
		}
		recalc	= false;
	}
}