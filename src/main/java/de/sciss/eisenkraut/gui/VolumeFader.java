/*
 *  VolumeFader.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2013 Hanns Holger Rutz. All rights reserved.
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
 *		10-Apr-08	created from ControlRoomFrame
 */
package de.sciss.eisenkraut.gui;

import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Dictionary;
import java.util.Enumeration;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import de.sciss.app.AbstractApplication;
import de.sciss.app.GraphicsHandler;
import de.sciss.eisenkraut.math.MathUtil;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 10-Apr-08
 */
public class VolumeFader
extends JSlider
{
	protected boolean isZero = true;
	
	public VolumeFader()
	{
		super( SwingConstants.VERTICAL, -72, 18, 0 );

		final GraphicsHandler	gh; 
		final Dictionary		dictVolume;
		final Font				fnt;
//		final Font				fntBold;
		final JLabel			lbZero;
		JLabel					lb, lbZeroTmp = null;

		putClientProperty( "JSlider.isFilled", Boolean.TRUE );	// used by Metal-lnf
		dictVolume = createStandardLabels( 12 );
		setMinorTickSpacing( 3 );
		setMajorTickSpacing( 12 );
		gh	= AbstractApplication.getApplication().getGraphicsHandler();
		fnt = gh.getFont( GraphicsHandler.FONT_LABEL | GraphicsHandler.FONT_MINI );
//		fntBold = gh.getFont( GraphicsHandler.FONT_BOLDSYSTEM | GraphicsHandler.FONT_MINI );
		for( Enumeration en = dictVolume.elements(); en.hasMoreElements(); ) {
			lb = (JLabel) en.nextElement();
			if( lb.getText().equals( "-72" )) lb.setText( "-\u221E" );
			if( lb.getText().equals( "0" )) {
//				lb.setFont( fntBold );
				lbZeroTmp = lb;
				lb.setText( "0\u25C0" );
//			} else {
//				lb.setFont( fnt );
			}
			lb.setFont( fnt );
		}
		lbZero = lbZeroTmp;
		setLabelTable( dictVolume );
		setPaintTicks( true );
		setPaintLabels( true );
		setValue( 0 );
		
		addMouseListener( new MouseAdapter() {
//			private boolean alt = false;
			
			public void mouseClicked( MouseEvent e )
			{
//				alt = e.isAltDown();
				if( e.isAltDown() ) resetVolume();
			}
			
//			// required for motif-lnf!
//			public void mouseReleased( MouseEvent e )
//			{
//				if( alt && (getValue() == 0) ) {
//					System.out.println( "BANG!" );
//					resetVolume();
//				}
//			}
		});
		if( lbZero != null ) addChangeListener( new ChangeListener() {
			public void stateChanged( ChangeEvent e )
			{
				if( isZero ) {
					if( getValue() != 0 ) {
						isZero = false;
						lbZero.setText( "0" );
//						lbZero.setFont( fnt );
						repaint();
					}
				} else {
					if( getValue() == 0 ) {
						isZero = true;
//						lbZero.setText( "<HTML><U>0</U></HTML>" );
						lbZero.setText( "0\u25C0" );
//						lbZero.setFont( fntBold );
						repaint();
					}
				}
			}
		});
		
	}

	public void resetVolume()
	{
		setValue( 0 );
	}

	public float getVolumeDecibels()
	{
		final int db = getValue();
		return db == -72 ? Float.NEGATIVE_INFINITY : db;
	}
	
	public float getVolumeLinear()
	{
		final int db = getValue();
		return db == -72 ? 0f : (float) MathUtil.dBToLinear( db );
	}
	
	public void setVolumeDecibels( float db )
	{
		setValue( Math.max( -72, (int) (db + 0.5f) ));
	}

	public void setVolumeLinear( float linear )
	{
		final int db = linear == 0f ? -72 : Math.max( -72, Math.min( 18, (int) (MathUtil.linearToDB( linear ) + 0.5) ));
		setValue( db );
	}
}