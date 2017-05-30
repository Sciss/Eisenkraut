/*
 *  PrefConstQ.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */
package de.sciss.eisenkraut.math;

import java.util.prefs.Preferences;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

import de.sciss.app.AbstractApplication;
import de.sciss.app.PreferenceNodeSync;
import de.sciss.gui.ParamField;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StringItem;
import de.sciss.util.ParamSpace;

@SuppressWarnings("serial")
public class ConstQPane
		extends SpringPanel
		implements PreferenceNodeSync, SwingConstants {

	protected PrefParamField	ggMinFreq, ggMaxFreq;
	private PrefParamField		ggBandsPerOct, ggMaxTimeRes;
	private PrefComboBox		ggMaxFFTSize;
	private Preferences			prefs	= null;

	public ConstQPane() {
		super(2, 1, 4, 2);

		final ParamSpace spcFreq = ParamSpace.spcFreqHertz.reshape( 1, 96000, 27.5 );
		final ParamSpace spcTime = ParamSpace.spcTimeMillis.reshape( 1, 2000, 5 );
		
		ggMinFreq		= new PrefParamField();
		ggMinFreq.addSpace( spcFreq );
		ggMinFreq.setPreferences( prefs, ConstQ.KEY_MINFREQ );
		ggMinFreq.addListener( new ParamField.Listener() {
			public void paramSpaceChanged( ParamField.Event e ) { /* ignore */ }
			public void paramValueChanged( ParamField.Event e )
			{
				if( ggMaxFreq.getValue().val < e.getValue().val ) {
					ggMaxFreq.setValue( e.getValue() );
				}
			}
		});
		ggMaxFreq		= new PrefParamField();
		ggMaxFreq.addSpace( ParamSpace.spcFreqHertz );
		ggMaxFreq.setPreferences( prefs, ConstQ.KEY_MAXFREQ );
		ggMaxFreq.addListener( new ParamField.Listener() {
			public void paramSpaceChanged( ParamField.Event e ) { /* ignore */ }
			public void paramValueChanged( ParamField.Event e )
			{
				if( ggMinFreq.getValue().val > e.getValue().val ) {
					ggMinFreq.setValue( e.getValue() );
				}
			}
		});
		ggBandsPerOct	= new PrefParamField();
//		ggBandsPerOct.setSpace( NumberSpace.createIntSpace( 1, 96 ));
		ggBandsPerOct.addSpace( new ParamSpace( 1, 96, 1, 0, 0, 24 ));
		ggBandsPerOct.setPreferences( prefs, ConstQ.KEY_BANDSPEROCT );
		ggMaxTimeRes	= new PrefParamField();
		ggMaxTimeRes.addSpace( spcTime );
		ggMaxTimeRes.setPreferences( prefs, ConstQ.KEY_MAXTIMERES );
		ggMaxFFTSize	= new PrefComboBox();
		for( int i = 256; i <= 32768; i <<= 1 ) {
			final String s = String.valueOf( i );
			ggMaxFFTSize.addItem( new StringItem( s, s ));
		}
		ggMaxFFTSize.setPreferences( prefs, ConstQ.KEY_MAXFFTSIZE );
		
		gridAdd( new JLabel( getResourceString( "labelMinFreq" ), TRAILING ), 0, 0 );
		gridAdd( ggMinFreq, 1, 0, -1, 1 );
		gridAdd( new JLabel( getResourceString( "labelMaxFreq" ), TRAILING ), 0, 1 );
		gridAdd( ggMaxFreq, 1, 1, -1, 1 );
		gridAdd( new JLabel( getResourceString( "labelBandsPerOct" ), TRAILING ), 0, 2 );
		gridAdd( ggBandsPerOct, 1, 2, -1, 1 );
		gridAdd( new JLabel( getResourceString( "labelMaxTimeRes" ), TRAILING ), 0, 3 );
		gridAdd( ggMaxTimeRes, 1, 3, -1, 1 );
		gridAdd( new JLabel( getResourceString( "labelMaxFFTSize" ), TRAILING ), 0, 4 );
		gridAdd( ggMaxFFTSize, 1, 4, -1, 1 );
		
		makeCompactGrid();
	}
	
	private static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	public void setPreferences( Preferences prefs )
	{
		ggMinFreq.setPreferenceNode( prefs );
		ggMaxFreq.setPreferenceNode( prefs );
		ggBandsPerOct.setPreferenceNode( prefs );
		ggMaxTimeRes.setPreferenceNode( prefs );
		ggMaxFFTSize.setPreferenceNode( prefs );
	}
}
