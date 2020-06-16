/*
 *  RotateChannels.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
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

public class RotateChannels
        extends AbstractRenderPlugIn {
    private static final String KEY_NUM_ROT = "numRotation";

    private static final Param DEFAULT_NUM_ROT = new Param( 1, ParamSpace.ABS);

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
        ggNimRod.setValueAndSpace(DEFAULT_NUM_ROT);
        ggNimRod.setPreferences( prefs, KEY_NUM_ROT);

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

        nimRod = ((int) Param.fromPrefs( prefs, KEY_NUM_ROT, DEFAULT_NUM_ROT).val) * -1;
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
