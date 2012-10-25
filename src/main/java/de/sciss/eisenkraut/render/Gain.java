/*
 *  Gain.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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
 *		16-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.awt.FlowLayout;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefParamField;
import de.sciss.gui.StringItem;
import de.sciss.io.Span;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class Gain
extends AbstractRenderPlugIn
implements RandomAccessRequester
{
	private static final String KEY_GAIN		= "gain";
	private static final String KEY_GAINTYPE	= "gaintype";

	private static final String GAIN_ABSOLUTE	= "abs";
	private static final String GAIN_NORMALIZED	= "norm";

	private static final Param DEFAULT_GAIN		= new Param( 0.0, ParamSpace.AMP | ParamSpace.REL | ParamSpace.DECIBEL );

	private	final DefaultUnitTranslator ut		= new DefaultUnitTranslator();

	private float			prGain;
	private boolean			prNormalize;
	private float			prMaxAmp;
	private boolean			prPeakKnown;
	private long			prFramesWritten;
	private long			prRenderLength;
	private float			prProgWeight;

	private static final int BLOCKSIZE	= 1024;
	private Span			prNextSpan;
	private Span			prTotalSpan;
	private RenderConsumer	prConsumer;
	private RenderHost		prHost;

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
		final JPanel				p			= new JPanel( new FlowLayout() );
		final PrefParamField		ggGain		= new PrefParamField( ut );
		final PrefComboBox			ggGainType	= new PrefComboBox();
//		final Application			app			= AbstractApplication.getApplication();
		
		ggGain.addSpace( ParamSpace.spcAmpDecibels );
		ggGain.addSpace( ParamSpace.spcAmpPercentF );
		ggGainType.addItem( new StringItem( GAIN_ABSOLUTE, getResourceString( "plugInGainAbsolute" )));
		ggGainType.addItem( new StringItem( GAIN_NORMALIZED, getResourceString( "plugInGainNormalized" )));
		
		p.add( new JLabel( getResourceString( "plugInGain" ), SwingConstants.RIGHT ));
		p.add( ggGain );
		p.add( ggGainType );
		ggGain.setValueAndSpace( DEFAULT_GAIN );
		ggGain.setPreferences( prefs, KEY_GAIN );
		ggGainType.setSelectedIndex( 1 );
		ggGainType.setPreferences( prefs, KEY_GAINTYPE );

		return p;
	}

	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		prGain			= (float) ut.translate( Param.fromPrefs( prefs, KEY_GAIN, DEFAULT_GAIN ), ParamSpace.spcAmpRel ).val;
		prNormalize		= prefs.get( KEY_GAINTYPE, "" ).equals( GAIN_NORMALIZED );
		prFramesWritten	= 0;
		prTotalSpan		= source.context.getTimeSpan();
		prConsumer		= source.context.getConsumer();
		prHost			= source.context.getHost();
		
		if( prNormalize ) {	// needs to scan twice, hence random access
			source.context.setOption( RenderContext.KEY_PREFBLOCKSIZE, new Integer( BLOCKSIZE ));
			source.context.setOption( RenderContext.KEY_RANDOMACCESS, this );
			// request first block
			prRenderLength	= prTotalSpan.getLength();
			prNextSpan		= new Span( prTotalSpan.start, Math.min( prTotalSpan.start + BLOCKSIZE, prTotalSpan.stop ));
			prPeakKnown	= false;
			prMaxAmp		= 0.0f;
			prProgWeight	= 1.0f / (3 * Math.max( 1, prRenderLength ));
			return true;
		} else {
			return prConsumer.consumerBegin( source );
		}
	}

	public boolean producerRender( RenderSource source )
	throws IOException
	{
		if( prNormalize && !prPeakKnown ) {
			float f1;
			for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
				if( !source.audioTrackMap[ ch ]) continue;
				for( int i = source.audioBlockBufOff, j = i + source.audioBlockBufLen; i < j; i++ ) {
					f1 = Math.abs( source.audioBlockBuf[ ch ][ i ]);
					if( f1 > prMaxAmp ) prMaxAmp = f1;
				}
			}

			prFramesWritten += source.audioBlockBufLen;

			prHost.setProgression( prFramesWritten * prProgWeight );

			// request next block
			prNextSpan = new Span( prNextSpan.stop, Math.min( prTotalSpan.stop, prNextSpan.stop + BLOCKSIZE ));
			if( prFramesWritten >= prRenderLength ) {
				assert prNextSpan.isEmpty();
				prPeakKnown	= true;
				if( prMaxAmp > 0.0f ) {
					prGain /= prMaxAmp;
				}
				// start second pass
				prNextSpan	= new Span( prTotalSpan.start, Math.min( prTotalSpan.start + BLOCKSIZE, prTotalSpan.stop ));
				return prConsumer.consumerBegin( source );
			} else {
				return true;
			}
		} else {
			for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
				if( !source.audioTrackMap[ ch ]) continue;
				for( int i = source.audioBlockBufOff, j = i + source.audioBlockBufLen; i < j; i++ ) {
					source.audioBlockBuf[ ch ][ i ] *= prGain;
				}
			}
			if( prNormalize ) {
				// request next block
				prNextSpan = new Span( prNextSpan.stop, Math.min( prTotalSpan.stop, prNextSpan.stop + BLOCKSIZE ));
			}
			return prConsumer.consumerRender( source );
		}
	}

	public String getName()
	{
		return getResourceString( "plugInGain" );
	}

	// ---------- RandomAccessRequester interface ----------
	
	public Span getNextSpan()
	{
		return prNextSpan;
	}
}
