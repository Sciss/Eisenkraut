/*
 *  PeakMeterGroup.java
 *  Eisenkraut
 *
 *  Copyright (c) 2005-2008 Hanns Holger Rutz. All rights reserved.
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
 *		20-Dec-07	created
 *		05-Feb-08	copied from SwingOSC
 */
package de.sciss.eisenkraut.gui;

//import java.awt.Color;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import de.sciss.gui.PeakMeter;
import de.sciss.gui.PeakMeterCaption;

/**
 *	@version	0.58, 20-Mar-08
 *	@author		Hanns Holger Rutz
 */
public class PeakMeterGroup
extends JPanel
implements SwingConstants
{
	protected PeakMeter[]		meters			= new PeakMeter[ 0 ];
	protected PeakMeterCaption	caption;
	private int					captionPosition	= LEFT;
	private int					captionAlign	= RIGHT;
	private boolean				captionVisible	= true;
	private boolean				captionLabels	= true;
	private int					numChannels		= 0;
//	private Object				sync			= new Object();
	private boolean				border			= false;
	
	private boolean				rmsPainted		= true;
	private boolean				holdPainted		= true;
	
	public PeakMeterGroup()
	{
		super();
		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ));
//		setBackground( Color.black );

		setFont( new Font( "SansSerif", Font.PLAIN, 12 ));
		addPropertyChangeListener( "font", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				if( caption != null ) {
					caption.setFont( getFont() );
					final Border b = BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 1 );
					for( int ch = 0; ch < meters.length; ch++ ) {
						meters[ ch ].setBorder( b );
					}
				}
			}
		});
	}

	// XXX UGLY
	public PeakMeter[] getMeters()
	{
		return meters;
	}

	public void setBorder( boolean onOff )
	{
		if( border == onOff ) return;
		
		border = onOff;
		setBorder( onOff ? new RecessedBorder() : null );
		updateBorders();
	}
	
	public void setCaption( boolean onOff )
	{
		if( onOff == (caption != null) ) return;
		
		if( onOff ) {
			caption	= new PeakMeterCaption();
			caption.setFont( getFont() );
//			caption.setBorder( BorderFactory.createEmptyBorder( 0, 1, 0, 0 ));
			caption.setVisible( captionVisible );
			caption.setHorizontalAlignment( captionAlign );
			caption.setPaintLabels( captionLabels );
		} else {
			caption	= null;
		}
		rebuildMeters();
	}
	
	public void setCaptionPosition( int pos )
	{
		if( captionPosition == pos ) return;
		
		switch( pos ) {
		case LEFT:
			captionAlign	= RIGHT;
			break;
		case RIGHT:
			captionAlign	= LEFT;
			break;
		case CENTER:
			captionAlign	= CENTER;
			break;
		default:
			throw new IllegalArgumentException( String.valueOf( pos ));
		}
		captionPosition		= pos;
		
		if( caption != null ) {
//System.out.println( "caption.setHorizontalAlignment( " + captionAlign + " )" );
			caption.setHorizontalAlignment( captionAlign );
			rebuildMeters();
		}
	}
	
	public void setCaptionLabels( boolean onOff )
	{
		if( captionLabels == onOff ) return;
		
		captionLabels = onOff;
		if( caption != null ) {
			caption.setPaintLabels( captionLabels );
//			revalidate();
		}
	}
	
	public void setCaptionVisible( boolean visible )
	{
		if( captionVisible == visible ) return;
		
		captionVisible = visible;
		if( caption != null ) {
			caption.setVisible( captionVisible );
			updateBorders();
//			revalidate();
		}
	}
	
	public void setNumChannels( int numChannels )
	{
		if( numChannels != this.numChannels ) {
			this.numChannels = numChannels;
			rebuildMeters();
		}
	}
	
	public int getNumChannels()
	{
		return numChannels;
	}
	
	public void setRMSPainted( boolean onOff )
	{
		if( rmsPainted == onOff ) return;
		
		rmsPainted = onOff;
//		synchronized( sync ) {
			for( int i = 0; i < meters.length; i++ ) {
				meters[ i ].setRMSPainted( onOff );
			}
//		}
	}

	public void setHoldPainted( boolean onOff )
	{
		if( holdPainted == onOff ) return;
		
		holdPainted = onOff;
//		synchronized( sync ) {
			for( int i = 0; i < meters.length; i++ ) {
				meters[ i ].setHoldPainted( onOff );
			}
//		}
	}
	
//	public void setSync( Object sync )
//	{
//		this.sync	= sync;
//		for( int i = 0; i < meters.length; i++ ) {
//			meters[ i ].setSync( sync );
//		}
//	}

	public boolean meterUpdate( float[] peakRMSPairs )
	{
		final PeakMeter[]	meters		= this.meters;	// = easy synchronization
		final int			numMeters	= Math.min( meters.length, peakRMSPairs.length >> 1 );
		final long			now			= System.currentTimeMillis();
		int					dirty		= 0;

//		System.out.println( "meterUpdate " + numMeters );
		
//		synchronized( sync ) {
			for( int i = 0, j = 0; i < numMeters; i++ ) {
//				System.out.println( "  " + peakRMSPairs[ j ]);
				if( meters[ i ].setPeakAndRMS( peakRMSPairs[ j++ ], peakRMSPairs[ j++ ], now )) dirty++;
			}
//		}
		
		return( dirty > 0 );
//		return( !(task && (dirty == 0)) );
//		if( !task && (dirty == 0) ) {
//			EventQueue.invokeLater( runStopTasking );
//		}
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );

		final Insets insets = getInsets();

		g.setColor( Color.black );
		g.fillRect( insets.left, insets.top,
		            getWidth() - (insets.left + insets.right),
		            getHeight() - (insets.top + insets.bottom) );
	}
	
	// -------------- private methods --------------

	private void rebuildMeters()
	{
		removeAll();
		
		final PeakMeter[]	meters;
		final Border		b1		= caption == null ? null : BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 1 );
		final Border		b2		= caption == null ? BorderFactory.createEmptyBorder( 1, 1, 1, 0 ) : BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 0 );
		final int			schnuck1, schnuck2;
		
		schnuck1 = (!border || (captionVisible && (captionPosition == RIGHT))) ? numChannels - 1 : -1;
		schnuck2 = (captionVisible && (captionPosition == CENTER)) ? (numChannels >> 1) : -1;
		
		meters	= new PeakMeter[ numChannels ];
		for( int ch = 0; ch < numChannels; ch++ ) {
			meters[ ch ] = new PeakMeter();
//			meters[ ch ].setSync( sync );
			meters[ ch ].setRefreshParent( true );
			meters[ ch ].setRMSPainted( rmsPainted );
			meters[ ch ].setHoldPainted( holdPainted );
			if( (ch == schnuck1) || (ch == schnuck2) ) {
				if( b1 != null ) meters[ ch ].setBorder( b1 );
			} else {
				meters[ ch ].setBorder( b2 );
			}
			meters[ ch ].setTicks( 101 );
			add( meters[ ch ]);
		}
		if( caption != null ) {
			switch( captionPosition ) {
			case LEFT:
				add( caption, 0 );
				break;
			case RIGHT:
				add( caption );
				break;
			case CENTER:
				add( caption, getComponentCount() >> 1 );
				break;
			default:
				assert false : captionPosition;
			}
		}
		this.meters = meters;
//		lmm.setMeters( masterMeters );
		revalidate();
		repaint();
	}
	
	private void updateBorders()
	{
		final Border		b1		= caption == null ? BorderFactory.createEmptyBorder( 1, 1, 1, 1 ) : BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 1 );
		final Border		b2		= caption == null ? BorderFactory.createEmptyBorder( 1, 1, 1, 0 ) : BorderFactory.createEmptyBorder( caption.getAscent(), 1, caption.getDescent(), 0 );
		final int			schnuck1, schnuck2;
		
		schnuck1 = (!border || (captionVisible && (captionPosition == RIGHT))) ? (numChannels - 1) : -1;
		schnuck2 = (captionVisible && (captionPosition == CENTER)) ? (numChannels >> 1) : -1;
		
		for( int ch = 0; ch < numChannels; ch++ ) {
			if( (ch == schnuck1) || (ch == schnuck2) ) {
				meters[ ch ].setBorder( b1 );
			} else {
				meters[ ch ].setBorder( b2 );
			}
			meters[ ch ].setTicks( 101 );
		}
	}
}
