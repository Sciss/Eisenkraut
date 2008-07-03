/*
 *  PeakMeter.java
 *  (de.sciss.gui package)
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *  Change log:
 *		23-Nov-07	moved from EisK LevelMeter
 */

package de.sciss.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

import de.sciss.util.Disposable;

/**
 *	A level (volume) meter GUI component. The component
 *	is a vertical bar displaying a green-to-reddish bar
 *	for the peak amplitude and a blue bar for RMS value.
 *	<p>
 *	To animate the bar, call <code>setPeakAndRMS</code> at a
 *	regular interval, typically around every 30 milliseconds
 *	for a smooth look.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 03-Jul-08
 *
 *	@todo	allow linear display (now it's hard coded logarithmic)
 *	@todo	add optional horizontal orientation
 */
public class PeakMeter
extends JComponent
implements PeakMeterView, Disposable
{
	public static final int		DEFAULT_HOLD_DUR = 2500;

	private int					holdDuration	= DEFAULT_HOLD_DUR;	// milliseconds peak hold

	private float				peak;
	private float				rms;
	private float				hold;
	private float				peakToPaint;
	private float				rmsToPaint;
	private float				holdToPaint;
	private float				peakNorm;
	private float				rmsNorm;
	private float				holdNorm;
	
	private int					recentHeight	= 0;
	private int					recentWidth		= 0;
	private int					calcedHeight	= -1;			// recentHeight snapshot in recalcPaint()
	private int					calcedWidth		= -1;			// recentWidth snapshot in recalcPaint()
	private long				lastUpdate		= System.currentTimeMillis();
	private long				holdEnd;
	
	private boolean				holdPainted		= true;
	private boolean				rmsPainted		= true;
	
//	private boolean				logarithmic		= true;			// XXX fixed for now
//	private float				fallSpeed		= 0.013333333333333f;		// decibels per millisec
//	private float				holdFallSpeed	= 0.015f;		// decibels per millisec
//	private float				floorWeight		= 1.0f / 40;	// -1 / minimumDecibels

	private static final int[]	bgPixels		= { 0xFF000000, 0xFF343434, 0xFF484848, 0xFF5C5C5C, 0xFF5C5C5C,
													0xFF5C5C5C, 0xFF5C5C5C, 0xFF5C5C5C, 0xFF484848, 0xFF343434,
													0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000,
													0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000 };

	private static final int[]	rmsTopColor		= {	0x000068, 0x5537B9, 0x764EE5, 0x9062E8, 0x8B63E8,
												    0x8360E8, 0x7C60E8, 0x8876EB, 0x594CB4, 0x403A63 };
	private static final int[]	rmsBotColor		= {	0x000068, 0x2F4BB6, 0x4367E2, 0x577FE5, 0x577AE5,
												    0x5874E6, 0x596FE6, 0x6B7AEA, 0x4851B1, 0x393D62 };

	private static final int[]	peakTopColor	= { 0x000000, 0xB72929, 0xFF3C3C, 0xFF6B6B, 0xFF6B6B,
												    0xFF6B6B, 0xFF6B6B, 0xFFA7A7, 0xFF3C3C, 0xB72929 };

	private static final int[]	peakBotColor	= { 0x000000, 0x008E00, 0x00C800, 0x02FF02, 0x02FF02,
												     0x02FF02, 0x02FF02, 0x68FF68, 0x00C800, 0x008E00 };

	private Paint				pntBg; // , pntRMS, pntPeak;
	private BufferedImage		imgBg, imgRMS, imgPeak;
	
	private static final double logPeakCorr		= 20.0 / Math.log( 10 );
	private static final double logRMSCorr		= 10.0 / Math.log( 10 );
	
	private Insets				insets;

	private int					yHold, yPeak, yRMS;
	
	private int					yPeakPainted	= 0;
	private int					yRMSPainted		= 0;
	private int					yHoldPainted	= 0;
	
	private boolean				refreshParent	= false;
	
	private int					ticks			= 0;

	/**
	 *	Creates a new level meter with default
	 *	ballistics and bounds.
	 */
	public PeakMeter()
	{
		super();

		setOpaque( true );
		
		setBorder( BorderFactory.createEmptyBorder( 1, 1, 1, 1 ));
		
		recalcPrefSize();
		
		addPropertyChangeListener( "border", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				recalcPrefSize();
			}
		});
		
		clearMeter();
	}
	
	// ------------- PeakMeterView interface -------------
	
	public int getNumChannels() { return 1; }
	
	public boolean meterUpdate( float[] peakRMSPairs, int offset, long time )
	{
		final int offset2 = offset + 1;
		if( offset2 >= peakRMSPairs.length ) return false;
		return setPeakAndRMS( peakRMSPairs[ offset ], peakRMSPairs[ offset2 ], time );
	}

	/**
	 *	Decides whether the peak indicator should be
	 *	painted or not. By default the indicator is painted.
	 *
	 *	@param	onOff	<code>true</code> to have the indicator painted,
	 *					<code>false</code> to switch it off
	 */
	public void setHoldPainted( boolean onOff )
	{
		if( holdPainted != onOff ) {
			holdPainted	= onOff;
			repaint();
		}
	}
	
	/**
	 *	Decides whether the blue RMS bar should be
	 *	painted or not. By default the bar is painted.
	 *
	 *	@param	onOff	<code>true</code> to have the RMS values painted,
	 *					<code>false</code> to switch them off
	 */
	public void setRMSPainted( boolean onOff )
	{
		if( rmsPainted != onOff ) {
			rmsPainted	= onOff;
			repaint();
		}
	}
	
	/**
	 *	Clears the peak, peak hold and rms values
	 *	immediately (without ballistics). This
	 *	way the component can be reset when the
	 *	metering task is stopped without waiting
	 *	for the bars to fall down.
	 */
	public void clearMeter()
	{
		final int	w1		= getWidth() - (insets.left + insets.right);
		final int	h1		= getHeight() - (insets.top + insets.bottom);
		final int	rh1		= (h1 - 1) & ~1;

		peak		= -160f;
		rms			= -160f;
		hold		= -160f;
		peakToPaint	= -160f;
		rmsToPaint	= -160f;
		holdToPaint	= -160f;
		peakNorm	= -1.0f;
		rmsNorm		= -1.0f;
		holdNorm	= -1.0f;
		holdEnd		= System.currentTimeMillis();
		yHold		= (rh1 - (int) (holdNorm * rh1) + 1) & ~1;
		yPeak		= (rh1 - (int) (peakNorm * rh1) + 1) & ~1;
		yRMS		= Math.max( (rh1 - (int) (rmsNorm  * rh1) + 1) & ~1, yPeak + 4 );
		if( refreshParent ) {
			getParent().repaint( insets.left + getX(), insets.top + getY(), w1, h1 );
		} else {
			repaint( insets.left, insets.top, w1, h1 );
		}
	}
	
	// ----------- public methods ----------- 
	
	public void setTicks( int ticks )
	{
		this.ticks = ticks;
		recalcPrefSize();
	}
	
	public void setRefreshParent( boolean onOff ) {
		refreshParent = onOff;
	}
	
//	public void setSync( Object sync ) {
//		synchronized( this.sync ) {
//			this.sync = sync;
//		}
//	}
	
//	// doesn't work XXX
//	public void setToolTipEnabled( boolean onOff )
//	{
//		if( onOff ) {
//			if( ma == null ) {
//				ma = new MouseAdapter() {
//					public void mouseEntered( MouseEvent e )
//					{
//						ttPeak = Float.NEGATIVE_INFINITY;
//						ttUpdate = true;
//						setToolTipText( String.valueOf( ttPeak ));
//					}
//					
//					public void mouseExited( MouseEvent e )
//					{
//						ttUpdate = false;
//						setToolTipText( null );
//					}
//				};
//			}
////			ttEnabled = true;
//		} else {
//			if( ma != null ) {
//				removeMouseListener( ma );
//				ma = null;
//			}
////			ttEnabled = false;
//			ttUpdate = false;
//		}
//	}
	
	/**
	 *	Sets the peak indicator hold time. Defaults to 1800 milliseconds.
	 *
	 *	@param	millis	new peak hold time in milliseconds. Note that
	 *					the special value <code>-1</code> means infinite
	 *					peak hold. In this case, to clear the indicator,
	 *					call <code>clearHold</code>
	 */
	public void setHoldDuration( int millis )
	{
//		synchronized( sync ) {
			holdDuration	= millis == -1 ? Integer.MAX_VALUE: millis;
			holdEnd			= System.currentTimeMillis();
//		}
	}
	
	/**
	 *	Clears the peak hold
	 *	indicator. Note that you will need
	 *	to call <code>setPeakAndRMS</code> successively
	 *	for the graphics to be updated.
	 */
	public void clearHold()
	{
//		synchronized( sync ) {
			hold		= -160f;
			holdNorm	= 0.0f;
//		}
	}
	
//	/**
//	 *	Adjusts the speed of the peak and RMS bar falling down.
//	 *	Defaults to 50 decibels per second. At the moment,
//	 *	rise (attack) speed is infinite.
//	 *
//	 *	@param	decibelsPerSecond	the amount of decibels by which the bars
//	 *								falls in one second
//	 */
//	public void setFallSpeed( float decibelsPerSecond )
//	{
//		synchronized( sync ) {
//			fallSpeed = decibelsPerSecond / 1000;
//		}
//	}

//	/**
//	 *	Adjusts the speed of the peak hold indicator falling down.
//	 *	Defaults to 15 decibels per second.
//	 *
//	 *	@param	decibelsPerSecond	the amount of decibels by which the peak indicator
//	 *								falls in one second
//	 */
//	public void setHoldFallSpeed( float decibelsPerSecond )
//	{
//		synchronized( sync ) {
//			holdFallSpeed = decibelsPerSecond / 1000;
//		}
//	}

//	/**
//	 *	Adjusts the minimum displayed amplitude, that is the
//	 *	amplitude corresponding to the bottom of the bar.
//	 *	Defaults to -40 decibels. At the moment, the maximum
//	 *	amplitude is fixed to 0 decibels (1.0 linear).
//	 *
//	 *	@param	decibels	the amplitude corresponding to the
//	 *						minimum bar extent
//	 */
//	public void setMinAmplitude( float decibels )
//	{
//		synchronized( sync ) {
//			floorWeight = -1.0f / decibels;
//			setPeakAndRMS( this.peak, this.rms );
//		}
//	}

	protected void recalcPrefSize()
	{
		insets = getInsets();
		final int w = 10 + insets.left + insets.right;
		setMinimumSize(   new Dimension( 4, 2 + insets.top + insets.bottom ));
//		setPreferredSize( new Dimension( w, ticks <= 0 ? getPreferredSize().height : (ticks * 2 + insets.top + insets.bottom) ));
		setPreferredSize( new Dimension( w, ticks <= 0 ? getPreferredSize().height : (ticks * 2 - 1 + insets.top + insets.bottom) ));
//		setMaximumSize(   new Dimension( w, getMaximumSize().height ));
	}
	
	public float getPeakDecibels()
	{
//		synchronized( sync ) {
			return peak <= -160f ? Float.NEGATIVE_INFINITY : peak;
//		}
	}

	public float getHoldDecibels()
	{
//		synchronized( sync ) {
			return hold <= -160f ? Float.NEGATIVE_INFINITY : hold;
//		}
	}
	
	/**
	 *	Updates the meter. This will call the component's paint
	 *	method to visually reflect the new values. Call this method
	 *	regularly for a steady animated meter.
	 *	<p>
	 *	If you have switched off RMS painted, you may want to
	 *	call <code>setPeak</code> alternatively.
	 *	<p>
	 *	When your audio engine is idle, you may want to stop meter updates.
	 *	You can use the following formula to calculate the maximum delay
	 *	of the meter display to be safely at minimum levels after starting
	 *	to send zero amplitudes:
	 *	</p><UL>
	 *	<LI>for peak hold indicator not painted : delay[sec] = abs(minAmplitude[dB]) / fallTime[dB/sec]
	 *	+ updatePeriod[sec]</LI>
	 *	<LI>for painted peak hold : the maximum of the above value and
	 *	delay[sec] = abs(minAmplitude[dB]) / holdFallTime[dB/sec] + holdTime[sec] + updatePeriod[sec]
	 *	</LI>
	 *	</UL><P>
	 *	Therefore, for the default values of 1.8 sec hold time, 15 dB/sec hold fall time and -40 dB
	 *	minimum amplitude, at a display period of 30 milliseconds, this yields a
	 *	delay of around 4.5 seconds. Accounting for jitter due to GUI slowdown, in ths case it should be
	 *	safe to stop meter updates five seconds after the audio engine stopped.
	 *
	 *	@param	peak	peak amplitude (linear) between zero and one.
	 *	@param	rms		mean-square amplitude (linear). note : despite the name,
	 *					this is considered mean-square, not root-mean-square. this
	 *					method does the appropriate conversion on the fly!
	 *
	 *	@synchronization	this method is thread safe
	 */
	public boolean setPeakAndRMS( float peak, float rms )
	{
		return setPeakAndRMS( peak, rms, System.currentTimeMillis() );
	}
	
	private float paintToNorm( float paint )
	{
		if( paint >= -30f ) {
			if( paint >= -20f ) {
				return Math.min( 1f, paint * 0.025f + 1.0f ); // 50 ... 100 %
			} else {
				return paint * 0.02f + 0.9f;  // 30 ... 50 %
			}
		} else if( paint >= -50f ) {
			if( paint >= -40f ) {
//			if( paint >= -45f ) {
//				return 0f;
//			} else {
				return paint * 0.015f + 0.75f;	// 15 ... 30 %
//			}
			} else {
				return paint * 0.01f + 0.55f;	// 5 ... 15%
			}
		} else if( paint >= -60f ) {
			return paint * 0.005f + 0.3f;	// 0 ... 5 %
		} else return -1f;
	}
	
	public boolean setPeakAndRMS( float peak, float rms, long time )
	{
		if( !EventQueue.isDispatchThread() ) throw new IllegalMonitorStateException();
		
		final boolean	result;
		final int		h1;

//		if( logarithmic ) {
			peak		= (float) (Math.log( peak ) * logPeakCorr);
			if( peak >= this.peak ) {
				this.peak	= peak;
			} else {
				// 20 dB in 1500 ms bzw. 40 dB in 2500 ms
				this.peak = Math.max( peak, this.peak - (time - lastUpdate) * (this.peak > -20f ? 0.013333333333333f : 0.016f ));
			}
			peakToPaint	= Math.max( peakToPaint, this.peak );
			peakNorm 	= paintToNorm( peakToPaint );

			if( rmsPainted ) {
				rms			= (float) (Math.log( rms ) * logRMSCorr);
				if( rms > this.rms ) {
					this.rms	= rms;
				} else {
					this.rms = Math.max( rms, this.rms - (time - lastUpdate) * (this.rms > -20f ? 0.013333333333333f : 0.016f ));
				}
				rmsToPaint	= Math.max( rmsToPaint, this.rms );
				rmsNorm		= paintToNorm( rmsToPaint );
			}
		
			if( holdPainted ) {
				if( this.peak >= hold ) {
					hold	= this.peak;
					holdEnd	= time + holdDuration;
				} else if( time > holdEnd ) {
					if( this.peak > hold ) {
						hold	= this.peak;
					} else {
						hold   += (this.hold > -20f ? 0.013333333333333f : 0.016f ) * (lastUpdate - time);
					}
				}
				holdToPaint	= Math.max( holdToPaint, hold );
				holdNorm	= paintToNorm( holdToPaint );
				result		= holdNorm >= 0f;
			} else {
				result		= peakNorm >= 0f;
			}

			lastUpdate		= time;
			h1				= getHeight() - insets.top - insets.bottom;
			final int rh1	= (h1 - 1) & ~1;
			recentHeight	= rh1 + 1;
			
			yHold	= (rh1 - (int) (holdNorm * rh1) + 1) & ~1;
			yPeak	= (rh1 - (int) (peakNorm * rh1) + 1) & ~1;
			yRMS	= Math.max( (rh1 - (int) (rmsNorm  * rh1) + 1) & ~1, yPeak + 2 );

			if( (yPeak != yPeakPainted) || (yRMS != yRMSPainted) || (yHold != yHoldPainted) ) {
				final int minY, maxY;
				
				if( holdPainted ) {
					minY = Math.min( yHold, yHoldPainted );
					if( rmsPainted ) {
						maxY = Math.max( Math.max( yPeak, yPeakPainted ), Math.max( yRMS, yRMSPainted )) + 3;
					} else {
						maxY = Math.max( yPeak, yPeakPainted ) + 1;
					}
				} else {
					if( rmsPainted ) {
						minY = Math.min( Math.min( yPeak, yPeakPainted ), Math.min( yRMS, yRMSPainted ));
						maxY = Math.max( Math.max( yPeak, yPeakPainted ), Math.max( yRMS, yRMSPainted )) + 3;
					} else {
						minY = Math.min( yPeak, yRMSPainted );
						maxY = Math.max( yPeak, yRMSPainted ) + 1;
					}
				}

				if( refreshParent ) {
					getParent().repaint( insets.left + getX(), insets.top + (recentHeight - h1) + minY + getY(), getWidth() - insets.left - insets.right,
										 maxY - minY );
				} else {
					repaint( insets.left, insets.top + (recentHeight - h1) + minY, getWidth() - insets.left - insets.right,
							 maxY - minY );
				}
			} else {
				peakToPaint		= -160f;
				rmsToPaint		= -160f;
				holdToPaint		= -160f;
			}
			
			return result;
//		}
	}
	
	/**
	 *	Updates the meter. This will call the component's paint
	 *	method to visually reflect the peak amplitude. Call this method
	 *	regularly for a steady animated meter. The RMS value is
	 *	not changed, so this method is appropriate when having RMS
	 *	painting turned off.
	 *
	 *	@param	peak	peak amplitude (linear) between zero and one.
	 *
	 *	@synchronization	this method is thread safe
	 */
	public boolean setPeak( float peak )
	{
//		synchronized( sync ) {
			return setPeakAndRMS( peak, this.rms );
//		}
	}
	
	private void recalcPaint()
	{
		final int		picH		= (recentHeight + 1) & ~1;
		final int		picW		= recentWidth;
		int[]			pix;
	
		if( imgPeak != null ) {
			imgPeak.flush();
			imgPeak = null;
		}
		if( imgRMS != null ) {
			imgRMS.flush();
			imgRMS = null;
		}
		if( (imgBg == null) || (imgBg.getWidth() != picW) ) {
			if( imgBg != null ) {
				imgBg.flush();
				imgBg = null;
			}
			if( picW == 10 ) {
				pix	= bgPixels;
			} else {
				pix	= widenPix( bgPixels, 10, picW, 2 );
			}
			imgBg = new BufferedImage( picW, 2, BufferedImage.TYPE_INT_ARGB );
			imgBg.setRGB( 0, 0, picW, 2, pix, 0, picW );
			pntBg = new TexturePaint( imgBg, new Rectangle( 0, 0, picW, 2 ));
		}
		
		pix	= hsbFade( picW, picH, rmsTopColor, rmsBotColor );
		imgRMS = new BufferedImage( picW, picH, BufferedImage.TYPE_INT_ARGB );
		imgRMS.setRGB( 0, 0, picW, picH, pix, 0, picW );
//		pntRMS = new TexturePaint( imgRMS, new Rectangle( 0, 0, 10, recentHeight ));

		pix	= hsbFade( picW, picH, peakTopColor, peakBotColor );
		imgPeak = new BufferedImage( picW, picH, BufferedImage.TYPE_INT_ARGB );
		imgPeak.setRGB( 0, 0, picW, picH, pix, 0, picW );
//		pntRMS = new TexturePaint( imgRMS, new Rectangle( 0, 0, 10, recentHeight ));

		calcedHeight	= recentHeight;
		calcedWidth		= recentWidth;
	}
	
	private int[] widenPix( int[] src, int srcW, int dstW, int h )
	{
		final int	minW		= Math.min( srcW, dstW );
		final int	minWH		= minW >> 1;
		final int	minWH1		= minW - minWH;
		final int 	numWiden	= dstW - srcW;
		final int[]	dst			= new int[ dstW * h ];
		
//System.out.println( "srcW " + srcW + "; dstW " + dstW + "; minW " + minW + "; numWiden " + numWiden );
		
		for( int y = 0, srcOffL = 0, srcOffR = srcW - minWH1, dstOffL = 0, dstOffR = dstW - minWH1;
			 y < h;
			 y++, srcOffL += srcW, srcOffR += srcW, dstOffL += dstW, dstOffR += dstW ) {

			System.arraycopy( src, srcOffL, dst, dstOffL, minWH );
			System.arraycopy( src, srcOffR, dst, dstOffR, minWH1 );
		}
		if( numWiden > 0 ) {
			int p;
			for( int y = 0, srcOff = minWH, dstOff = minWH; y < h; y++, srcOff += srcW, dstOff += srcW ) {
				p = src[ srcOff ];
				for( int stop = dstOff + numWiden; dstOff < stop; dstOff++ ) {
					dst[ dstOff ] = p;
				}
			}
		}
		return dst;
	}
	
	private int[] hsbFade( int picW, int picH, int[] topColr, int[] botColr )
	{
		final int[] 	pix 		= new int[ picW * picH ];
		final int[] 	sTopColr, sBotColr;
		final float[]	hsbTop		= new float[ 3 ];
		final float[]	hsbBot		= new float[ 3 ];
		final float		w3			= 1.0f / (picH - 2);
		int				rgb;
		float			w1, w2;
		
		if( picW == 10 ) {
			sTopColr	= topColr;
			sBotColr	= botColr;
		} else {
			sTopColr	= widenPix( topColr, 10, picW, 1 );
			sBotColr	= widenPix( botColr, 10, picW, 1 );
		}
		
		for( int x = 0; x < picW; x++ ) {
			rgb = sTopColr[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbTop );
			rgb = sBotColr[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbBot );
			for( int y = 0, off = x; y < picH; y += 2, off += (picW << 1) ) {
				w2				= y * w3;
				w1				= 1.0f - w2;
				rgb				= Color.HSBtoRGB( hsbTop[0] * w1 + hsbBot[0] * w2,
												  hsbTop[1] * w1 + hsbBot[1] * w2,
												  hsbTop[2] * w1 + hsbBot[2] * w2 );
				pix[ off ]		= rgb | 0xFF000000;
				pix[ off+picW ]	= 0xFF000000;
			}
		}
		
		return pix;
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		final Graphics2D		g2;
		final AffineTransform	atOrig;
		final int				h1		= getHeight() - (insets.top + insets.bottom);
//		final int				h		= (getHeight() - insets.top - insets.bottom + 1) & ~1;
//		final int				h		= (getHeight() - insets.top - insets.bottom) & ~1;
		final int				rh1		= (h1 - 1) & ~1;
		final int				h		= rh1 + 1;
		
		recentWidth = getWidth() - (insets.left + insets.right);
		
		g.setColor( Color.black );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		if( h > 0 ) {
//			synchronized( sync ) {	
				if( h != recentHeight ) {
//					yPeak		= ((int) ((1.0f - peakNorm) * h) + 1) & ~1;
//					yRMS		= ((int) ((1.0f - rmsNorm)  * h) + 1) & ~1;
//					yHold		= ((int) ((1.0f - holdNorm) * h) + 1) & ~1;
					yHold		= (rh1 - (int) (holdNorm * rh1) + 1) & ~1;
					yPeak		= (rh1 - (int) (peakNorm * rh1) + 1) & ~1;
					yRMS		= Math.max( (rh1 - (int) (rmsNorm  * rh1) + 1) & ~1, yPeak + 4 );
					recentHeight= h;
				}
				if( (calcedHeight != recentHeight) || (calcedWidth != recentWidth) ) {
					recentHeight = h;
//					System.out.println( "recalc h " + getHeight() + "; top " + insets.top + "; bottom " + insets.bottom + "; recentHeight " + recentHeight + "; h1 " + h1 );
					recalcPaint();
				}
				
				g2		= (Graphics2D) g;
				atOrig	= g2.getTransform();
				g2.translate( insets.left, insets.top + (h1 - h) );
	
				g2.setPaint( pntBg );
//g2.setPaint( gaga ? Color.black : Color.white ); gaga = !gaga;
		//		g2.fillRect( 1, 0, 10, yPeak );
				if( rmsPainted ) {
//					g2.fillRect( 0, 0, 10, yRMS + 1 );
					g2.fillRect( 0, 0, recentWidth, yRMS + 1 );
					if( holdPainted ) g2.drawImage( imgPeak, 0, yHold, recentWidth, yHold + 1, 0, yHold, recentWidth, yHold + 1, this );
					final int yClipped = Math.min( h, yRMS );
					g2.drawImage( imgPeak, 0, yPeak, recentWidth, yClipped, 0, yPeak, recentWidth, yClipped, this );
					g2.drawImage( imgRMS, 0, yRMS + 2, recentWidth, h, 0, yRMS + 2, recentWidth, h, this );
				} else {
					g2.fillRect( 0, 0, recentWidth, yPeak );
					if( holdPainted ) g2.drawImage( imgPeak, 0, yHold, recentWidth, yHold + 1, 0, yHold, recentWidth, yHold + 1, this );
					g2.drawImage( imgPeak, 0, yPeak, recentWidth, h, 0, yPeak, recentWidth, h, this );
				}
				
//System.err.println( "__ " + this.hashCode() + " : " + yPeak + ", " + yRMS + ", " + yHold + " ;; " + lastUpdate );
// System.err.println( "__ " + yPeak + ", " + yRMS + ", " + yHold + " ;; " + this.peak + ", " + peakToPaint + ", " + peakNorm );
				peakToPaint		= -160f;
				rmsToPaint		= -160f;
				holdToPaint		= -160f;
				yPeakPainted	= yPeak;
				yRMSPainted		= yRMS;
				yHoldPainted	= yHold;
				
				g2.setTransform( atOrig );
//			}
		}
	}
	
	// --------------- Disposable interface ---------------
	
	public void dispose()
	{
//		synchronized( sync ) {
			if( imgPeak != null ) {
				imgPeak.flush();
				imgPeak = null;
			}
			if( imgRMS != null ) {
				imgRMS.flush();
				imgRMS = null;
			}
			if( imgBg != null ) {
				imgBg.flush();
				imgBg	= null;
				pntBg	= null;
			}
			calcedHeight = -1;
//		}
	}
}