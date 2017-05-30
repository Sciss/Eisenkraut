/*
 *  FadePlugIn.java
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

package de.sciss.eisenkraut.render;

import de.sciss.eisenkraut.gui.CurvePanel;
import de.sciss.eisenkraut.io.BlendContext;
import de.sciss.io.Span;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.io.IOException;

public abstract class FadePlugIn
		extends AbstractRenderPlugIn {

	private final boolean	isFadeIn;	// true for fade-in, false for fade-out

	protected static final	String KEY_BLENDING	= "blend";

	private RenderConsumer	prConsumer;
	private Span			prTotalSpan;
	private BlendContext	prBlend;
	private float[][]		prBuf;

	protected FadePlugIn( boolean isFadeIn )
	{
		super();
		this.isFadeIn	= isFadeIn;
	}

	public boolean hasUserParameters()
	{
		return true;
	}

	public JComponent getSettingsView(RenderContext context) {
		final CubicCurve2D basicShape = new CubicCurve2D.Double(
				0.0, isFadeIn ? 0.0 : 1.0, 0.5, 0.5, 0.5, 0.5, 1.0, isFadeIn ? 1.0 : 0.0);
		final JPanel p = new JPanel(new BorderLayout());

		CurvePanel curvePanel = new CurvePanel(new CubicCurve2D[]{basicShape}, prefs);
		curvePanel.setPreferredSize(new Dimension(162, 162));
		p.add(curvePanel, BorderLayout.CENTER);
		return p;
	}
	
	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		prConsumer		= source.context.getConsumer();
		prTotalSpan		= source.context.getTimeSpan();
		
//		prBlend			= new BlendContext( prTotalSpan.getLength(), CurvePanel.getControlPoints( prefs ));
		prBlend			= new BlendContext( prTotalSpan.getLength(), 0, CurvePanel.getControlPoints( prefs ));
		prBuf			= new float[ source.numAudioChannels ][];

		return prConsumer.consumerBegin( source );
	}
	
	public boolean producerRender( RenderSource source )
	throws IOException
	{
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			if( source.audioTrackMap[ ch ]) prBuf[ ch ] = source.audioBlockBuf[ ch ];
		}
		
		if( isFadeIn ) {
			prBlend.fadeIn( source.blockSpan.start - prTotalSpan.start, prBuf,
			                     source.audioBlockBufOff, prBuf, source.audioBlockBufOff, source.audioBlockBufLen );
		} else {
			prBlend.fadeOut( source.blockSpan.start - prTotalSpan.start, prBuf,
			                      source.audioBlockBufOff, prBuf, source.audioBlockBufOff, source.audioBlockBufLen );
		}
		return prConsumer.consumerRender( source );
	}
}
