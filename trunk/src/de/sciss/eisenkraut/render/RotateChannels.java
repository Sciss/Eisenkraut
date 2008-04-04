/*
 *  RotateChannels.java
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
 *		15-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.awt.BorderLayout;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.gui.PrefParamField;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class RotateChannels
extends AbstractRenderPlugIn
{
	private static final String KEY_NUMROT	= "numRotation";
//	private int nimRod;

	private static final Param DEFAULT_NUMROT = new Param( 1, ParamSpace.NONE | ParamSpace.ABS );

	private RenderConsumer	prConsumer;
	private int[]			prMap;

	public boolean hasUserParameters()
	{
		return true;
	}
	
	public boolean shouldDisplayParameters()
	{
		return true;
	}

	public JComponent getSettingsView( RenderContext context )
	{
		final JPanel			p			= new JPanel( new BorderLayout() );
		final PrefParamField	ggNimRod	= new PrefParamField();
		final Application		app			= AbstractApplication.getApplication();
		
		ggNimRod.addSpace( new ParamSpace( Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1, 0, 0, 1 ));
		ggNimRod.setValueAndSpace( DEFAULT_NUMROT );
		ggNimRod.setPreferences( prefs, KEY_NUMROT );

		p.add( new JLabel( app.getResourceString( "plugInChanShift" ), SwingConstants.RIGHT ), BorderLayout.WEST );
		p.add( ggNimRod, BorderLayout.CENTER );
		return p;
	}

	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		int toGo, ch2, nimRod, nimRodAbs, step, numSelected;
		
		prConsumer	= source.context.getConsumer();
		prMap		= new int[ source.numAudioChannels ];
	
		nimRod = ((int) Param.fromPrefs( prefs, KEY_NUMROT, DEFAULT_NUMROT ).val) * -1;
		nimRodAbs = Math.abs( nimRod );
		step	= nimRod / Math.max( 1, nimRodAbs );
		numSelected = 0;
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			if( source.audioTrackMap[ ch ]) numSelected++;
		}
		nimRodAbs = Math.abs( nimRod ) % numSelected;
		nimRod = nimRodAbs * step;
//		while( nimRod < 0 ) nimRod += source.numAudioChannels;	// modulus fails with negative numbers
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			if( source.audioTrackMap[ ch ]) {
				toGo	= nimRodAbs;
				ch2		= ch;
				while( toGo > 0 ) {
// modulo cannot handle negative numbers
//					ch2 = (ch2 + step) % source.numAudioChannels;
					ch2 += step;
					if( ch2 == -1 ) ch2 = source.numAudioChannels - 1;
					else if( ch2 == source.numAudioChannels ) ch2 = 0;
					if( ch2 == ch ) break;
					if( source.audioTrackMap[ ch2 ]) toGo--;
				}
				prMap[ ch ] = ch2;
			} else {
				prMap[ ch ] = ch;
			}
		}
		return prConsumer.consumerBegin( source );
	}

	public boolean producerRender( RenderSource source )
	throws IOException
	{
		// XXX WARNING this may be invalid when FilterDialog uses copies of the audioBlock channels
		float[][] rotated = new float[ source.numAudioChannels ][];
	
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			rotated[ ch ] = source.audioBlockBuf[ prMap[ ch ]];
		}
		source.audioBlockBuf = rotated;
		
		return prConsumer.consumerRender( source );
	}

	public String getName()
	{
		return getResourceString( "plugInRotateChannels" );
	}
}
