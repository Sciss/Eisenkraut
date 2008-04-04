/*
 *  CrossfadePanel.java
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
 *		05-Mar-06	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.OverlayLayout;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 05-Mar-06
 */
public class CrossfadePanel
extends JComponent
implements ActionListener
{
	private JComponent				a, b;
	private float					fade			= 0f;
	private float					sourceFade;
	private float					targetFade;
	private long					fadeStart;		// abs millis
	private float					fadeWeight;
	private final javax.swing.Timer	timer;

//	private static final int				NUM_ALPHAS	= 16;
//	private static final AlphaComposite[]	alphas		= new AlphaComposite[ NUM_ALPHAS ];
//
//	static {
//		for( int i = 0; i < NUM_ALPHAS; i++ ) {
//			alphas[ i ] = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, (float) i / (NUM_ALPHAS - 1) );
//		}
//	}

	public CrossfadePanel()
	{
		super();
		
		setLayout( new OverlayLayout( this ));
		
		timer = new javax.swing.Timer( 50, this );
	}
	
	public void setComponentA( JComponent c )
	{
		if( a != null ) {
			remove( a );
		}
		a = c;
		checkVisibility();
		add( a, 0 );
	}

	public void setComponentB( JComponent c )
	{
		if( b != null ) {
			remove( b );
		}
		b = c;
		checkVisibility();
		add( b, -1 );
	}
	
	/**
	 *	Instantly update crossfade.
	 *
	 *	@param	fade	value between 0 (component A only) and 1 (component B only)
	 *	@throws	IllegalArgumentException	if fade < 0 or fade > 1
	 */
	public void setFade( float fade )
	{
		timer.stop();

		if( (fade < 0f) || (fade > 1f) ) {
			throw new IllegalArgumentException( String.valueOf( fade ));
		}
		if( fade != this.fade ) {
			this.fade	= fade;
			checkVisibility();
			repaint();
		}
	}
	
	private void checkVisibility()
	{
		if( (a != null) && (a.isVisible() != fade < 1f) ) {
			a.setVisible( fade < 1f );
		}
		if( (b != null) && (b.isVisible() != fade > 0f) ) {
			b.setVisible( fade > 0f );
		}
	}
	
	public float getFade()
	{
		return fade;
	}
	
	/**
	 *	Performs an animated crossfade.
	 *
	 *	@param	target						target value between 0 (component A only) and 1 (component B only)
	 *	@param	initialDelay				milliseconds to wait before the fade starts
	 *	@param	fadeTime					fade duration in milliseconds
	 *	@throws	IllegalArgumentException	if fade < 0 or fade > 1 or initialDelay < 0 or fadeTime < 0
	 */
	public void performFade( float target, int initialDelay, int fadeTime )
	{
		timer.stop();
		
		if( (target < 0f) || (target > 1f) || (initialDelay < 0) || (fadeTime < 0) ) {
			throw new IllegalArgumentException();
		}
		
		sourceFade		= fade;
		targetFade		= target;
		fadeStart		= System.currentTimeMillis() + initialDelay;
		fadeWeight		= 1.0f / (Math.max( 1, fadeTime ));
		
		if( sourceFade != targetFade ) {
			timer.setInitialDelay( initialDelay );
			timer.start();
		}

		checkVisibility();
	}
	
	protected void paintChildren( Graphics g )
	{
		final Graphics2D	g2		= (Graphics2D) g;
		final Composite		origCmp	= g2.getComposite();
		
		if( (a != null) && (fade < 1f) ) {
			g2.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, 1.0f - fade ));
			a.paint( g );
		}
		if( (b != null) && (fade > 0f) ) {
			g2.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, fade ));
			b.paint( g );
		}
		
		g2.setComposite( origCmp );
	}
	
	// --------------- ActionListener interface ---------------
	
	public void actionPerformed( ActionEvent e )
	{
		final long	delta	= System.currentTimeMillis() - fadeStart;
		final float fadePos	= Math.max( 0f, Math.min( 1f, delta * fadeWeight ));
		final float newFade	= sourceFade * (1f - fadePos) + targetFade * fadePos;
		
		if( newFade != fade ) {
			fade	= newFade;
			repaint();
		}
		
		if( fadePos == 1f ) {
			timer.stop();
			checkVisibility();
		}
	}
}
