/*
 *  WaveformView.java
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
 *		18-Feb-05	created
 */
 
package de.sciss.eisenkraut.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;

import javax.swing.JComponent;

import de.sciss.eisenkraut.io.AudioTrail;
import de.sciss.eisenkraut.io.DecimatedSonaTrail;
import de.sciss.eisenkraut.io.DecimatedWaveTrail;
import de.sciss.eisenkraut.io.DecimationInfo;
import de.sciss.eisenkraut.session.Session;
import de.sciss.gui.ComponentHost;
import de.sciss.io.Span;
import de.sciss.util.Disposable;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class WaveformView
extends JComponent
implements Disposable
{
//	private boolean				doRecalc		= true;
	
	private int					fullChannels;
	private int[]				channelMap;
	
	private Insets				insets			= new Insets( 0, 0, 0, 0 );
	private int					vGap			= 1;
		
	private Rectangle			r				= new Rectangle();
	
//	private static final Stroke	strkLine		= new BasicStroke( 0.5f );
//	private static final Paint	pntArea			= new Color( 0x00, 0x00, 0x00, 0x7F );
//	private static final Paint	pntLine			= Color.black;
	private static final Paint	pntNull			= new Color( 0x7F, 0x7F, 0x00, 0xC0 );
	private static final Stroke	strkNull		= new BasicStroke( 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
													1.0f, new float[] { 4.0f, 4.0f }, 0.0f );

	private boolean				logarithmic		= false;
	private float				linearMin		= -1.0f;	// minimum vector value
	private float				linearMax		= 1.0f;		// maximum vector value
	private float				logMin			= -60f;
	private float				logMax			= 0f;
	private boolean				nullLinie		= false;

	private final Session		doc;
	private Span				viewSpan		= new Span();
	
	private DecimationInfo		info			= null; // most recent one!

	private final ComponentHost	host;

	public WaveformView( Session doc )
	{
		this( doc, null );
	}

	public WaveformView( Session doc, ComponentHost host )
	{
		super();
		
		this.host			= host;
				
		final AudioTrail at	= doc.getAudioTrail();
		fullChannels		= at.getChannelNum();
		channelMap			= new int[ fullChannels ];
		for( int i = 0; i < fullChannels; i++ ) {
			channelMap[ i ]	= i;
		}
		
		this.doc			= doc;
	}
	
	public void setNullLinie( boolean onOff )
	{
		if( nullLinie != onOff ) {
			nullLinie = onOff;
//			if( !logarithmic )
				triggerRedisplay();
		}
	}

	public boolean getNullLinie()
	{
		return nullLinie;
	}
	
	public void setLogarithmic( boolean onOff )
	{
		if( logarithmic != onOff ) {
			logarithmic = onOff;
			triggerRedisplay();
		}
	}

	public boolean isLogarithmic()
	{
		return logarithmic;
	}

	/**
	 *  Gets the minimum allowed y value
	 *
	 *  @return		the minimum specified function value
	 */
	public float getLinearMin()
	{
		return linearMin;
	}

	/**
	 *  Gets the maximum allowed y value
	 *
	 *  @return		the maximum specified function value
	 */
	public float getLinearMax()
	{
		return linearMax;
	}

	public float getLogMin()
	{
		return logMin;
	}


	public float getLogMax()
	{
		return logMax;
	}
	
	/**
	 *  Changes the allowed range for vector values.
	 *  Influences the graphics display such that
	 *  the top margin of the panel corresponds to max
	 *  and the bottom margin corresponds to min. Also
	 *  user drawings are limited to these values unless
	 *  wrapY is set to true (not yet implemented).
	 *
	 *  @param  min		new minimum y value
	 *  @param  max		new maximum y value
	 *
	 *  @warning	the current vector is left untouched,
	 *				even if values lie outside the new
	 *				allowed range.
	 */
	public void setLinearMinMax( float min, float max )
	{
		if( (this.linearMin != min) || (this.linearMax != max) ) {
			this.linearMin	= min;
			this.linearMax	= max;

			if( !logarithmic ) triggerRedisplay();
		}
	}
	
	public void setLogMinMax( float min, float max )
	{
		if( (this.logMin != min) || (this.logMax != max) ) {
			this.logMin	= min;
			this.logMax	= max;

			if( logarithmic ) triggerRedisplay();
		}
	}

	//	public float[][] gimmeBaffa( int numCh, int len )
//	{
//		if( (buffer != null) && (buffer.length >= numCh) && (bufLen >= len) ) return buffer;
//		
//		buffer	= new float[ numCh ][ len ];
//		bufLen	= len;
//		return buffer;
//	}

	/**
	 *	@synchronization	use in swing thread only
	 */
	public void update( Span s )
	{
		viewSpan	= s;
		triggerRedisplay();
	}

	public int getNumChannels()
	{
		return fullChannels;
	}
	
	/**
	 *	@synchronization	this uses and alters one internal rectangle object,
	 *						be sure to not use this rectangle outside the swing thread,
	 *						otherwise make a copy. do not modify the returned rectangle
	 */
	public Rectangle rectForChannel( int ch )
	{
		final int ht	= getHeight();
		final int temp	= ht * ch / fullChannels;
		final int y		= insets.top + temp;
		final int h		= (ht * (ch + 1) / fullChannels) - temp - vGap;
		
		r.setBounds( insets.left, y, getWidth(), h );
		
		return r;
	}
	
	public int channelForPoint( Point p )
	{
		final int	py	= p.y - insets.top;
		final int	ht	= getHeight();
		int			y1	= 0;
		int			y2;
		
		for( int ch = 0; ch < fullChannels; ch++ ) {
			y2 = ht * (ch + 1) / fullChannels;
			if( (py >= y1) && (py < (y2 - vGap)) ) return ch;
			y1 = y2;
		}
		return -1;
	}
	
	public DecimationInfo getDecimationInfo() { return info; }

	public void paintComponentGAGA( Graphics g )
	{
		super.paintComponent( g );

//		doRecalc = false;	// not used now
	
		if( viewSpan.isEmpty() ) return;

		final Graphics2D			g2		= (Graphics2D) g;
		final DecimatedSonaTrail	dt		= doc.getDecimatedSonaTrail();
		if( dt == null ) return;
		
		final int				w		= getWidth();
//		final int				h		= getHeight();
		Rectangle				cr;
		int						y;
		
		info	= dt.getBestSubsample( new Span( viewSpan.start, viewSpan.stop + 1 ), w );
		dt.drawWaveform( info, this, g2 );

		if( nullLinie ) {
			g2.setPaint( pntNull );
			g2.setStroke( strkNull );
			for( int ch = 0; ch < fullChannels; ch++ ) {
				cr = rectForChannel( ch );
				y = cr.y + (cr.height >> 1);
				g2.drawLine( cr.x, y, cr.x + cr.width, y );
			}
		}
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );

//		doRecalc = false;	// not used now
	
		if( viewSpan.isEmpty() ) return;

		final Graphics2D			g2		= (Graphics2D) g;
		final DecimatedWaveTrail	dt		= doc.getDecimatedWaveTrail();
		if( dt == null ) return;
		
		final int				w		= getWidth();
//		final int				h		= getHeight();
		Rectangle				cr;
		int						y;
		
		info	= dt.getBestSubsample( new Span( viewSpan.start, viewSpan.stop + 1 ), w );
		dt.drawWaveform( info, this, g2 );

		if( nullLinie ) {
			g2.setPaint( pntNull );
			g2.setStroke( strkNull );
			for( int ch = 0; ch < fullChannels; ch++ ) {
				cr = rectForChannel( ch );
				y = cr.y + (logarithmic ? cr.height - 1 : (cr.height >> 1));
				g2.drawLine( cr.x, y, cr.x + cr.width, y );
			}
		}
	}

	private void triggerRedisplay()
	{
//		doRecalc	= true;
		if( host != null ) {
			host.update( this );
		} else if( isVisible() ) {
			repaint();
		}
	}
  
	// -------------- Disposable interface --------------
	
	public void dispose() { /* empty */ }
}
