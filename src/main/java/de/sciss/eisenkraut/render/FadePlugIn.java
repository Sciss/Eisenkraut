/*
 *  FadePlugIn.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *		14-Jul-05	created
 */

package de.sciss.eisenkraut.render;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.geom.CubicCurve2D;
import java.io.IOException;
import javax.swing.JComponent;
import javax.swing.JPanel;

import de.sciss.eisenkraut.gui.CurvePanel;
import de.sciss.eisenkraut.io.BlendContext;

import de.sciss.io.Span;

// @version	0.70, 26-Sep-07
public abstract class FadePlugIn
extends AbstractRenderPlugIn
{
	private CurvePanel		curvePanel;
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
	
	public JComponent getSettingsView( RenderContext context )
	{
		final CubicCurve2D basicShape = new CubicCurve2D.Double(
			0.0, isFadeIn ? 0.0 : 1.0, 0.5, 0.5, 0.5, 0.5, 1.0, isFadeIn ? 1.0 : 0.0 );
		final JPanel p = new JPanel( new BorderLayout() );
	
		curvePanel = new CurvePanel( new CubicCurve2D[] { basicShape }, prefs );
		curvePanel.setPreferredSize( new Dimension( 162, 162 ));
		p.add( curvePanel, BorderLayout.CENTER );
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
