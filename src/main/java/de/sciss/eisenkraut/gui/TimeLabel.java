/*
 *  TimeLabel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;

import de.sciss.gui.TimeFormat;
import de.sciss.util.Disposable;

/**
 *	A GUI component showing a time position.
 */
@SuppressWarnings("serial")
public class TimeLabel
		extends JPanel
		implements Disposable {

	private final TimeFormat	frmt;
	protected String			text;

	protected static final Font	fntMono;
	protected boolean			dimsKnown		= false;
	protected int				textWidth, textHeight, textAscent;

	private static final Color		colrTime		= new Color( 0xF1, 0xFA, 0xCA );

	private final Label		lb;

	static {
		final String[] fntNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		if( contains( fntNames, "Monaco" )) {							// Mac OS
			fntMono = new Font( "Monaco", Font.PLAIN, 11 );				// looks bigger than "normal monospaced"
		} else if( contains( fntNames, "Lucida Sans Unicode" )) {		// Windows XP
			fntMono = new Font( "Lucida Sans Unicode", Font.PLAIN, 12 );
		} else {
			fntMono = new Font( "Monospaced", Font.PLAIN, 12 );
		}
	}
	
	public TimeLabel()
	{
		this( colrTime );
	}

	public TimeLabel( Color background )
	{
		super( new BorderLayout() );

		setBorder( new RoundedBorder( background ));
		
		frmt	= new TimeFormat( 0, null, null, 3, Locale.US );
		lb		= new Label();
		add( lb, BorderLayout.CENTER );
		lb.setOpaque( true );
		lb.setBackground( background );
//		lb.setBackground( new Color( background.getRed(), background.getGreen(), background.getBlue(), 0x7F ));
		lb.setForeground( Color.black );
		
		final Dimension d = new Dimension( 106, 22 );	// XXX
		setMinimumSize( d );
		setMaximumSize( d );
		setPreferredSize( d );
		
		text	= frmt.formatTime( new Integer( 0 ));		
	}
	
//	public void blink()
//	{
//		colr		= Color.red;
//		resetWhen   = System.currentTimeMillis() + 150;
//		repaint();
//	}
	
	private static boolean contains( String[] array, String name )
	{
		for( int i = 0; i < array.length; i++ ) {
			if( array[ i ].equals( name )) return true;
		}
		return false;
	}

	public void blue()
	{
		lb.setForeground( Color.blue );
//		colr		= Color.blue;
//		repaint();
	}
	
	public void black()
	{
		lb.setForeground( Color.black );
//		colr		= Color.black;
//		repaint();
	}
	
//	public Dimension getPreferredSize()
//	{
//		return preferredSize;
//	}
	
	public void setTime( Number seconds )
	{
//		int	k, n;
//		
//		// millis
//		n					= millis % 1000;
//		wahrnehmung[ 11 ]	= (byte) ((n % 10) + 0x30);
//		n				   /= 10;
//		wahrnehmung[ 10 ]	= (byte) (n == 0 ? 0x20 : (n % 10) + 0x30);
//		n				   /= 10;
//		wahrnehmung[ 9 ]	= (byte) (n == 0 ? 0x20 : n + 0x30);
//
//		// seconds
//		n					= (millis / 1000) % 60;
//		wahrnehmung[ 7 ]	= (byte) ((n % 10) + 0x30);
//		n				   /= 10;
//		wahrnehmung[ 6 ]	= (byte) (n == 0 ? 0x20 : n + 0x30);
//
//		// minutes
//		n					= (millis / 60000) % 60;
//		wahrnehmung[ 4 ]	= (byte) ((n % 10) + 0x30);
//		n				   /= 10;
//		wahrnehmung[ 3 ]	= (byte) (n == 0 ? 0x20 : n + 0x30);
//
//		// hours
//		n					= (millis / 360000);
//		wahrnehmung[ 1 ]	= (byte) ((n % 10) + 0x30);
//		n				   /= 10;
//		wahrnehmung[ 0 ]	= (byte) (n == 0 ? 0x20 : (n % 10) + 0x30);
//		
		text = frmt.formatTime( seconds );
		lb.repaint();
	}
	
//	public void paint( Graphics g )
//	{
//		paintComponent( g );
//		paintBorder( g );
//		paintChildren( g );
//	}
//
//	public void paintBorder( Graphics g )
//	{
//		System.err.println( "paint border!" );
//		super.paintBorder( g );
//	}

	public void dispose()
	{
		lb.dispose();
	}

	@SuppressWarnings("serial")
	private class Label
			extends JComponent
			implements Disposable {

		private Image img;
	
		protected Label() { /* empty */ }

//		private void createImage( FontMetrics fm )
//		{
//			final int			w2		= fm.stringWidth( "00" ) + 2;
//			img							= createImage( w2, textHeight );
//			final Graphics2D	g2		= (Graphics2D) img.getGraphics();
//			
//			g2.setColor( getBackground() );
//			g2.fillRect( 0, 0, w2, textHeight );
////			g2.setColor( getForeground() );
//			g2.setColor( new Color( 0x00, 0x00, 0x00, 0x2F ));
//			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//			g2.setFont( fntMono );
//			for( int i = 0; i < 10; i++ ) {
//				g2.drawString( String.valueOf( i ) + String.valueOf( i ), 1, textAscent );
//			}
//			g2.dispose();
//		}
		
		public void dispose()
		{
			if( img != null ) {
				img.flush();
				img = null;
			}
		}
		
		public void paintComponent( Graphics g )
		{
			super.paintComponent( g );
			
			final Graphics2D	g2	= (Graphics2D) g;
			g2.setFont( fntMono );
			final FontMetrics	fm	= g2.getFontMetrics();

			if( !dimsKnown ) {
				textWidth				= fm.stringWidth( "00:00:00.000" );
				textAscent				= fm.getAscent();
				textHeight				= fm.getHeight(); // textAscent + fm.getDescent();
				dimsKnown				= true;
				Dimension d				= new Dimension( textWidth, textHeight );
				setPreferredSize( d );
				setMinimumSize( d );
				setMaximumSize( d );
//				if( img == null ) {
//					createImage( fm );
//				}
			}
			
			g2.setColor( getBackground() );
			g2.fillRect( 0, 0, getWidth(), getHeight() );
			g2.setColor( getForeground() );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//			g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF );
			
	//		if( (colr == Color.red) && (System.currentTimeMillis() >= resetWhen) ) {
	//			colr = Color.black;
	//		}
	//		g.setColor( colr );
	//		g.drawBytes( wahrnehmung, 0, 12, 0, 10 );

			g2.drawString( text, (getWidth() - fm.stringWidth( text )) >> 1, ((getHeight() - textHeight) >> 1) + textAscent );
//			g2.drawString( text.substring( 0, Math.max( 0, text.length() - 2 )), (getWidth() - fm.stringWidth( text )) >> 1, ((getHeight() - textHeight) >> 1) + textAscent );
//			g2.drawImage( img, ((getWidth() + fm.stringWidth( text )) >> 1) - img.getWidth( this ) + 1, ((getHeight() - textHeight) >> 1), this );
		}
	}
}

