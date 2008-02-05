/*
 *  ComponentHost.java
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
 *  Change log:
 *		18-Feb-06	created
 *		16-Jul-07	moved to de.sciss.gui
 */

package de.sciss.gui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import de.sciss.util.Disposable;

public class ComponentHost
extends JComponent
implements Disposable
{
	// --- top painter ---

	private Image					limited			= null;
	private boolean					imageUpdate		= false;
	private boolean					imageUpdateC	= false;
	private int						recentWidth, recentHeight;

	private final List				collTopPainters	= new ArrayList();
//	private final List				collLights		= new ArrayList();
//	private final Map				mapLights		= new HashMap();

	private static Paint			pntBackground	= null;		// initialized with the first instance

	private static final int[]		pntBgAquaPixels = { 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0,
														0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0,
														0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC,
														0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC };

	private final Rectangle			updateRect		= new Rectangle();
	
	private final Object			sync			= new Object();

	public ComponentHost()
	{
		super();

		synchronized( sync )
		{
			if( pntBackground == null ) {
				final BufferedImage img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
				img.setRGB( 0, 0, 4, 4, pntBgAquaPixels, 0, 4 );
				pntBackground = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));
			}
		}
		
		setOpaque( true );
		setDoubleBuffered( false );
	}
	
	public void update( Component c )
	{
		synchronized( sync ) {
			final Rectangle r = c.getBounds();
			if( updateRect.isEmpty() ) {
				updateRect.setBounds( r );
			} else {
				updateRect.setBounds( updateRect.union( r ));
			}
			imageUpdate = true;
//System.err.println( "Repaint "+r.x+", "+r.y+", "+r.width+", "+r.height );
			repaint( r );
		}
	}
	
//	public void update( LightComponent c )
//	{
//		final LightInfo li;
//	
//		synchronized( sync ) {
//			li = (LightInfo) mapLights.get( c );
//		
//			if( updateRect.isEmpty() ) {
//				updateRect.setBounds( li.r );
//			} else {
//				updateRect.setBounds( updateRect.union( li.r ));
//			}
//			imageUpdate = true;
//			repaint( li.r );
//		}
//	}
	
	public void update( Rectangle r )
	{
		synchronized( sync ) {
			if( updateRect.isEmpty() ) {
				updateRect.setBounds( r );
			} else {
				updateRect.setBounds( updateRect.union( r ));
			}
			imageUpdate = true;
			repaint( r );
		}
	}

	public void updateAll()
	{
		synchronized( sync ) {
			updateRect.setBounds( 0, 0, getWidth(), getHeight() );
			imageUpdate = true;
			repaint();
		}
	}

//	public void addLight( LightComponent c )
//	{
//		final LightInfo li = new LightInfo( c );
//	
//		synchronized( sync ) {
//			collLights.add( li );
//			mapLights.put( c, li );
//			c.setBounds( r );
//			add( c );
//		}
//	}

	private void redrawImage()
	{
		if( limited == null ) return;
//System.err.println( "redrawImage" );

		final Graphics2D		g2			= (Graphics2D) limited.getGraphics();
		final Shape				clipOrig	= g2.getClip();
		final AffineTransform	atOrig		= g2.getTransform();
//		LightInfo				li;
		Component				c;
		Rectangle				r;

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		synchronized( sync ) {
			if( imageUpdateC ) {
				updateRect.setBounds( 0, 0, recentWidth, recentHeight );
				imageUpdateC	= false;
			}
//			for( int i = 0; i < collLights.size(); i++ ) {
			for( int i = 0; i < getComponentCount(); i++ ) {
//				li	= (LightInfo) collLights.get( i );
				c	= getComponent( i );
				r	= c.getBounds();
				if( r.intersects( updateRect )) {
					if( !c.isOpaque() ) {
						g2.setPaint( pntBackground );
//						g2.fillRect( li.r.x, li.r.y, li.r.width, li.r.height );
						g2.fillRect( r.x, r.y, r.width, r.height );
					}
//					g2.clipRect( li.r.x, li.r.y, li.r.width, li.r.height );
					g2.clipRect( r.x, r.y, r.width, r.height );
//					g2.translate( li.r.x, li.r.y );
					g2.translate( r.x, r.y );
//					li.c.paint( this, g2 );
					c.paint( g2 );
					g2.setClip( clipOrig );
					g2.setTransform( atOrig );
				}
			}
			updateRect.setBounds( 0, 0, 0, 0 );
			imageUpdate		= false;
		}
		g2.dispose();
	}
	
//	public void clear( Graphics2D g2, int x, int y, int w, int h )
//	{
//		g2.setPaint( pntBackground );
//		g2.fillRect( 0, 0, w, h );	
//	}

	public void dispose()
	{
		Component c;
	
		flushImage();
		synchronized( sync ) {
//			for( int i = 0; i < collLights.size(); i++ ) {
//				((LightInfo) collLights.get( i )).c.dispose();
//			}
//			collLights.clear();
//			mapLights.clear();
			for( int i = 0; i < getComponentCount(); i++ ) {
				c = getComponent( i );
				if( c instanceof Disposable ) {
					((Disposable) c).dispose();
				}
			}
			removeAll();
		}
//		buffer	= null;
	}
	
	private void flushImage()
	{
		if( limited != null ) {
			limited.flush();
			limited = null;
		}
	}
	
	private void recreateImage()
	{
		flushImage();
		limited = createImage( recentWidth, recentHeight );
		imageUpdateC = true;
	}
	
	public void paint( Graphics g )	// no paintChildren() !
	{
		paintComponent( g );
	}
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final int width		= getWidth();
		final int height	= getHeight();

		if( (width != recentWidth) || (height != recentHeight) ) {
			recentWidth		= width;
			recentHeight	= height;
			recreateImage();
		}

		if( imageUpdate || imageUpdateC ) {
			redrawImage();
		}

		if( limited != null ) {
			g.drawImage( limited, 0, 0, this );
		}

		// --- invoke top painters ---
		if( !collTopPainters.isEmpty() ) {
			final Graphics2D		g2			= (Graphics2D) g;
//			final AffineTransform	trnsOrig	= g2.getTransform();

// XXX
//			g2.transform( trnsVirtualToScreen );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			for( int i = 0; i < collTopPainters.size(); i++ ) {
				((TopPainter) collTopPainters.get( i )).paintOnTop( g2 );
			}
//			g2.setTransform( trnsOrig );
		}
	}

	/**
	 *  Registers a new top painter.
	 *  If the top painter wants to paint
	 *  a specific portion of the surface,
	 *  it must make an appropriate repaint call!
	 *
	 *  @param  p   the painter to be added to the paint queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void addTopPainter( TopPainter p )
	{
		if( !collTopPainters.contains( p )) {
			collTopPainters.add( p );
		}
	}

	/**
	 *  Removes a registered top painter.
	 *
	 *  @param  p   the painter to be removed from the paint queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void removeTopPainter( TopPainter p )
	{
		collTopPainters.remove( p );
	}
}
