/*
 *  CurvePanel.java
 *  Eisenkraut
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
 *		15-Jul-05	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.AWTEventMulticaster;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.event.MouseInputAdapter;

/**
 *  This class describes a generic GUI tool
 *  that can be aquired and dismissed by
 *  a <code>Component</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 17-Apr-07
 */
public class CurvePanel
extends JComponent
// implements LaterInvocationManager.Listener
{
	private static final String		KEY_CTRLX1		= "ctrlx1";
	private static final String		KEY_CTRLY1		= "ctrly1";
	private static final String		KEY_CTRLX2		= "ctrlx2";
	private static final String		KEY_CTRLY2		= "ctrly2";
//	private static final String[]	KEYS			= { KEY_CTRLX1, KEY_CTRLY1, KEY_CTRLX2, KEY_CTRLY2 };

	private final CubicCurve2D[]	shpFades;
	private final AffineTransform	at				= new AffineTransform();
	protected static final Insets	insets			= new Insets( 1, 1, 1, 1 );
	private static final Shape		shpCtrlIn		= new Ellipse2D.Double( -2, -2, 5, 5 );
	private static final Area		shpCtrlOut;
	private static final Paint		pntCtrlIn		= new Color( 0x00, 0x00, 0x00, 0x7F );
	private static final Paint		pntCtrlOut		= new Color( 0x00, 0x00, 0x00, 0x3F );
	private static final Paint		pntCtrlOutS		= new Color( 0x00, 0x00, 0xFF, 0x7F );

	private final Shape[]			tShpFades;

	protected final Point2D[]	ctrlPt	= new Point2D[] {
		new Point2D.Double( 0.5, 0.5 ), new Point2D.Double( 0.5, 0.5 )};

	protected int					recentWidth		= -1;
	protected int					recentHeight	= -1;
	protected boolean				recalc			= true;
	protected Point2D				dragPt			= null;
//	private final Preferences		prefs;
	
	private ActionListener			actionListener	= null;
	
	static {
		shpCtrlOut		   = new Area( new Ellipse2D.Double( -7, -7, 15, 15 ));
		shpCtrlOut.subtract( new Area( new Ellipse2D.Double( -4, -4, 9, 9 )));
	}
				
	public CurvePanel( CubicCurve2D[] basicCurves, final Preferences prefs )
	{
		super();
	
		final Dimension d = new Dimension( 64, 64 );
		
		shpFades	= basicCurves;
//		prefs		= prefs;
		tShpFades		= new Shape[ shpFades.length ];
		
		if( prefs != null ) {
			ctrlPt[0].setLocation( prefs.getDouble( KEY_CTRLX1, 0.5 ), prefs.getDouble( KEY_CTRLY1, 0.5 ));
			ctrlPt[1].setLocation( prefs.getDouble( KEY_CTRLX2, 0.5 ), prefs.getDouble( KEY_CTRLY2, 0.5 ));
		}

		setMinimumSize( d );
		setPreferredSize( d );
		setBorder( BorderFactory.createEmptyBorder( insets.left, insets.top, insets.bottom, insets.right ));

//		if( prefs != null ) {
//			new DynamicAncestorAdapter( new DynamicPrefChangeManager( prefs, KEYS, this )).addTo( this );
//		}

		MouseInputAdapter	mia	= new MouseInputAdapter() {
			private boolean didThemDragga = false;
		
			public void mousePressed( MouseEvent e )
			{
				Point2D	mousePt = getVirtualMousePos( e );
			
				if( mousePt.distanceSq( ctrlPt[ 0 ]) <= mousePt.distanceSq( ctrlPt[ 1 ])) {
					dragPt	= ctrlPt[ 0 ];
				} else {
					dragPt	= ctrlPt[ 1 ];
				}
				
				processDrag( mousePt, !e.isControlDown() );
			}

			public void mouseReleased( MouseEvent e )
			{
				dragPt			= null;
				repaint();
				if( didThemDragga ) {
					dispatchAction();
					didThemDragga	= false;
				}
			}
			
			private Point2D getVirtualMousePos( MouseEvent e )
			{
				return new Point2D.Double(
					Math.max( 0.0, Math.min( 1.0, (double) (e.getX() - insets.left) / recentWidth )),
					1.0 - Math.max( 0.0, Math.min( 1.0, (double) (e.getY() - insets.top) / recentHeight )));
			}

			public void mouseMoved( MouseEvent e )
			{
				mouseDragged( e );  // mouseDragged not called with popup dialog!
			}

			public void mouseDragged( MouseEvent e )
			{
				if( dragPt != null ) processDrag( getVirtualMousePos( e ), !e.isControlDown() );
			}
			
			private void processDrag( Point2D mousePt, boolean snap )
			{
				didThemDragga = true;
				if( snap ) {
					if( Math.abs( mousePt.getX() - 0.5 ) < 0.1 ) mousePt.setLocation( 0.5, mousePt.getY() );
					if(	Math.abs( mousePt.getY() - 0.5 ) < 0.1 ) mousePt.setLocation( mousePt.getX(), 0.5 );
				}
				dragPt.setLocation( mousePt );
				if( prefs != null ) {
					if( dragPt == ctrlPt[0] ) {
						prefs.putDouble( KEY_CTRLX1, dragPt.getX() );
						prefs.putDouble( KEY_CTRLY1, dragPt.getY() );
					} else {
						prefs.putDouble( KEY_CTRLX2, dragPt.getX() );
						prefs.putDouble( KEY_CTRLY2, dragPt.getY() );
					}
				}
				recalc = true;
				repaint();
			}
		};
		
		addMouseListener( mia );
		addMouseMotionListener( mia );
	}
	
     public void addActionListener( ActionListener l )
	 {
		synchronized( this ) {
			actionListener = AWTEventMulticaster.add( actionListener, l );
		}
     }
	 
     public void removeActionListener( ActionListener l )
	 {
		synchronized( this ) {
			actionListener = AWTEventMulticaster.remove( actionListener, l );
		}
     }
	 
     protected void dispatchAction()
	 {
         final ActionListener listener = actionListener;
         if( listener != null ) {
             listener.actionPerformed( new ActionEvent( this, ActionEvent.ACTION_PERFORMED, null ));
         }
     }

 	public Point2D[] getControlPoints()
 	{
 		return new Point2D[] { new Point2D.Double( ctrlPt[ 0 ].getX(), ctrlPt[ 0 ].getY() ),
 							   new Point2D.Double( ctrlPt[ 1 ].getX(), ctrlPt[ 1 ].getY() )};
 	}
 	
 	public void setControlPoints( Point2D ctrlPt1, Point2D ctrlPt2 )
 	{
		ctrlPt[0].setLocation( ctrlPt1.getX(), ctrlPt1.getY() );
		ctrlPt[1].setLocation( ctrlPt2.getX(), ctrlPt2.getY() );
		recalc = true;
		repaint();
	}

	// this is static to allow calculations
	// without actually creating GUI elements
	public static Point2D[] getControlPoints( Preferences prefs )
	{
		return new Point2D[] {
			new Point2D.Double( prefs.getDouble( KEY_CTRLX1, 0.5 ), prefs.getDouble( KEY_CTRLY1, 0.5 )),
			new Point2D.Double( prefs.getDouble( KEY_CTRLX2, 0.5 ), prefs.getDouble( KEY_CTRLY2, 0.5 ))
		};
	}

	public static void toPrefs( Point2D[] ctrlPt, Preferences prefs )
	{
		prefs.putDouble( KEY_CTRLX1, ctrlPt[0].getX() );
		prefs.putDouble( KEY_CTRLY1, ctrlPt[1].getY() );
		prefs.putDouble( KEY_CTRLX2, ctrlPt[1].getX() );
		prefs.putDouble( KEY_CTRLY2, ctrlPt[1].getY() );
	}

	public void toPrefs( Preferences prefs )
	{
		toPrefs( ctrlPt, prefs );
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D		g2				= (Graphics2D) g;
		final int				currentWidth	= getWidth() - insets.left - insets.right;
		final int				currentHeight	= getHeight() - insets.top - insets.bottom;
		final AffineTransform	atOrig			= g2.getTransform();

		double trnsX, trnsY;
		
		if( (currentWidth != recentWidth) || (currentHeight != recentHeight) || recalc ) {
			recentWidth		= currentWidth;
			recentHeight	= currentHeight;
			at.setToScale( currentWidth, -currentHeight );
			at.translate( 0, -1.0 );
			recalcTransforms();
		}
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.translate( insets.left, insets.top );
		for( int i = 0; i < tShpFades.length; i++ ) {
			g2.draw( tShpFades[ i ]);
		}
		
		for( int i = 0; i < ctrlPt.length; i++ ) {
			trnsX	= ctrlPt[i].getX() * currentWidth;
			trnsY	= (1.0 - ctrlPt[i].getY()) * currentHeight;
			g2.translate( trnsX, trnsY );
			g2.setPaint( pntCtrlIn );
			g2.fill( shpCtrlIn );
			if( ctrlPt[i] == dragPt ) {
				g2.setPaint( pntCtrlOutS );
			} else {
				g2.setPaint( pntCtrlOut );
			}
			g2.fill( shpCtrlOut );
			g2.translate( -trnsX, -trnsY );
		}

		g2.setTransform( atOrig );
	}

	private void recalcTransforms()
	{
//		if( prefs != null ) {
//			ctrlPt[0].setLocation( prefs.getDouble( KEY_CTRLX1, 0.5 ), prefs.getDouble( KEY_CTRLY1, 0.5 ));
//			ctrlPt[1].setLocation( prefs.getDouble( KEY_CTRLX2, 0.5 ), prefs.getDouble( KEY_CTRLY2, 0.5 ));
//		}
		for( int i = 0; i < shpFades.length; i++ ) {
//			shpFades[ i ].setCurve( shpFades[ i ].getP1(), ctrlPt[ i % 2 ], ctrlPt[ (i+1) % 2 ], shpFades[ i ].getP2() );
			shpFades[ i ].setCurve( shpFades[ i ].getX1(), shpFades[ i ].getY1(),
				ctrlPt[ 0 ].getX(), ctrlPt[ i % 2 ].getY(),
				ctrlPt[ 1 ].getX(), ctrlPt[ (i+1) % 2 ].getY(),
				shpFades[ i ].getX2(), shpFades[ i ].getY2() );
			tShpFades[ i ]	= at.createTransformedShape( shpFades[ i ]);
		}
		recalc	= false;
	}

//	// o instanceof PreferenceChangeEvent
//	public void laterInvocation( Object o )
//	{
//		recalc = true;
//		repaint();
//	}
	
	public static class Icon
	implements javax.swing.Icon
	{
		private final int width;
		private final int height;
		private final AffineTransform at;

		private final CubicCurve2D[]	shpFades;
		private final Shape[]			tShpFades;

		private final Point2D[]	ctrlPt	= new Point2D[] {
			new Point2D.Double( 0.5, 0.5 ), new Point2D.Double( 0.5, 0.5 )};

		public Icon( CubicCurve2D[] basicCurves )
		{
			this( basicCurves, 16, 16 );
		}

		public Icon( CubicCurve2D[] basicCurves, int width, int height )
		{
			this.width	= width;
			this.height	= height;

			at			= AffineTransform.getScaleInstance( width, -height );
			at.translate( 0, -1.0 );
			
			shpFades	= basicCurves;
			tShpFades	= new Shape[ shpFades.length ];
		}
		
		public int getIconWidth() { return width; }
		public int getIconHeight() { return height; }
		
//		public void update( Preferences prefs )
//		{
//			ctrlPt[0].setLocation( prefs.getDouble( KEY_CTRLX1, 0.5 ), prefs.getDouble( KEY_CTRLY1, 0.5 ));
//			ctrlPt[1].setLocation( prefs.getDouble( KEY_CTRLX2, 0.5 ), prefs.getDouble( KEY_CTRLY2, 0.5 ));
//
//			for( int i = 0; i < shpFades.length; i++ ) {
//				shpFades[ i ].setCurve( shpFades[ i ].getX1(), shpFades[ i ].getY1(),
//					ctrlPt[ 0 ].getX(), ctrlPt[ i % 2 ].getY(),
//					ctrlPt[ 1 ].getX(), ctrlPt[ (i+1) % 2 ].getY(),
//					shpFades[ i ].getX2(), shpFades[ i ].getY2() );
//				tShpFades[ i ]	= at.createTransformedShape( shpFades[ i ]);
//			}
//		}
			
		public void update( Point2D ctrl1, Point2D ctrl2 )
		{
			ctrlPt[0].setLocation( ctrl1.getX(), ctrl1.getY() );
			ctrlPt[1].setLocation( ctrl2.getX(), ctrl2.getY() );

			for( int i = 0; i < shpFades.length; i++ ) {
				shpFades[ i ].setCurve( shpFades[ i ].getX1(), shpFades[ i ].getY1(),
					ctrlPt[ 0 ].getX(), ctrlPt[ i % 2 ].getY(),
					ctrlPt[ 1 ].getX(), ctrlPt[ (i+1) % 2 ].getY(),
					shpFades[ i ].getX2(), shpFades[ i ].getY2() );
				tShpFades[ i ]	= at.createTransformedShape( shpFades[ i ]);
			}
		}
			
		public void paintIcon( Component c, Graphics g, int x, int y )
		{
			final Graphics2D		g2				= (Graphics2D) g;
			final AffineTransform	atOrig			= g2.getTransform();
			
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			g2.translate( x, y );
			g2.setColor( Color.black );
			for( int i = 0; i < tShpFades.length; i++ ) {
				g2.draw( tShpFades[ i ]);
			}
			g2.setTransform( atOrig );
		}		
	}
}