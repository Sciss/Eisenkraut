/*
 *  TimeLabel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
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
		if (contains(fntNames, "Monaco")) {                            // Mac OS
			fntMono = new Font("Monaco", Font.PLAIN, 11);                // looks bigger than "normal monospaced"
		} else if (contains(fntNames, "Lucida Sans Unicode")) {        // Windows XP
			fntMono = new Font("Lucida Sans Unicode", Font.PLAIN, 12);
		} else {
			fntMono = new Font("Monospaced", Font.PLAIN, 12);
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
		lb.setForeground( Color.black );
		
		final Dimension d = new Dimension( 106, 22 );	// XXX
		setMinimumSize( d );
		setMaximumSize( d );
		setPreferredSize( d );
		
		text	= frmt.formatTime(0);
	}

	private static boolean contains(String[] array, String name) {
		for (String anArray : array) {
			if (anArray.equals(name)) return true;
		}
		return false;
	}

	public void blue() {
		lb.setForeground(Color.blue);
	}

	public void black() {
		lb.setForeground(Color.black);
	}

	public void setTime(Number seconds) {
		text = frmt.formatTime(seconds);
		lb.repaint();
	}

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
			}
			
			g2.setColor( getBackground() );
			g2.fillRect( 0, 0, getWidth(), getHeight() );
			g2.setColor( getForeground() );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

			g2.drawString( text, (getWidth() - fm.stringWidth( text )) >> 1, ((getHeight() - textHeight) >> 1) + textAscent );
		}
	}
}

