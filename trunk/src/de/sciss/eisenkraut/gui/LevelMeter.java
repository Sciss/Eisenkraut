/*
 *  LevelMeter.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2006 Hanns Holger Rutz. All rights reserved.
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
 *		01-Oct-05	created
 *		22-Apr-06	fixed minimum height
 *		13-Jul-06	added dispose()
 *		21-Sep-06	setPeakAndRMS() returns whether fallen peak value > 0; added getPeakDecibles() and getHoldDecibels()
 */

package de.sciss.eisenkraut.gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.*;
import java.beans.*;
import javax.swing.*;

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
 *  @version	0.70, 17-Oct-06
 *
 *	@todo	allow linear display (now it's hard coded logarithmic)
 *	@todo	add optional horizontal orientation
 *	@todo	add optional labels
 *	@todo	allow to change the bar width (now hard coded to 12 pixels)
 *	@todo	allow grouping of meters for synchronized paint updates
 */
public class LevelMeter
extends JComponent
implements Disposable
{
	public static final int		DEFAULT_HOLD_DUR = 1800;

	private int					holdDuration	= DEFAULT_HOLD_DUR;	// milliseconds peak hold

	private float				peak			= -160f;
	private float				rms				= -160f;
	private float				hold			= -160f;
	private float				peakToPaint		= -160f;
	private float				rmsToPaint		= -160f;
	private float				holdToPaint		= -160f;
	private float				peakNorm		= 0.0f;
	private float				rmsNorm			= 0.0f;
	private float				holdNorm		= 0.0f;
	
	private int					recentHeight	= 0;
	private int					calcedHeight	= -1;			// recentHeight snapshot in recalcPaint()
	private long				lastUpdate		= System.currentTimeMillis();
	private long				holdEnd			= lastUpdate;
	
	private boolean				holdPainted		= true;
	private boolean				rmsPainted		= true;
	
//	private boolean				logarithmic		= true;			// XXX fixed for now
	private float				fallSpeed		= 0.05f;		// decibels per millisec
	private float				holdFallSpeed	= 0.015f;		// decibels per millisec
	private float				floorWeight		= 1.0f / 40;	// -1 / minimumDecibels

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
	
//	private boolean				ttEnabled		= false;
	private float				ttPeak			= Float.NEGATIVE_INFINITY;
	private boolean				ttUpdate		= false;
	private MouseAdapter		ma				= null;
	
	private Object				sync			= new Object();
	
	private int					yPeakPainted	= 0;
	private int					yRMSPainted		= 0;
	private int					yHoldPainted	= 0;
	
	private boolean				refreshParent		= false;

	/**
	 *	Creates a new level meter with default
	 *	ballistics and bounds.
	 */
	public LevelMeter()
	{
		super();

		setOpaque( true );
		
		setBorder( BorderFactory.createEmptyBorder( 2, 1, 1, 1 ));
		
		updateInsets();
		
		addPropertyChangeListener( "border", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				updateInsets();
			}
		});
	}
	
	public void setRefreshParent( boolean onOff ) {
		refreshParent = onOff;
	}
	
	public void setSync( Object sync ) {
		synchronized( this.sync ) {
			this.sync = sync;
		}
	}
	
	// doesn't work XXX
	public void setToolTipEnabled( boolean onOff )
	{
		if( onOff ) {
			if( ma == null ) {
				ma = new MouseAdapter() {
					public void mouseEntered( MouseEvent e )
					{
						ttPeak = Float.NEGATIVE_INFINITY;
						ttUpdate = true;
						setToolTipText( String.valueOf( ttPeak ));
					}
					
					public void mouseExited( MouseEvent e )
					{
						ttUpdate = false;
						setToolTipText( null );
					}
				};
			}
//			ttEnabled = true;
		} else {
			if( ma != null ) {
				removeMouseListener( ma );
				ma = null;
			}
//			ttEnabled = false;
			ttUpdate = false;
		}
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
		synchronized( sync ) {
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
		synchronized( sync ) {
			rmsPainted	= onOff;
			repaint();
		}
	}
	
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
		synchronized( sync ) {
			holdDuration	= millis == -1 ? Integer.MAX_VALUE: millis;
			holdEnd			= System.currentTimeMillis();
		}
	}
	
	/**
	 *	Clears the peak hold
	 *	indicator. Note that you will need
	 *	to call <code>setPeakAndRMS</code> successively
	 *	for the graphics to be updated.
	 */
	public void clearHold()
	{
		synchronized( sync ) {
			hold		= -160f;
			holdNorm	= 0.0f;
		}
	}
	
	/**
	 *	Adjusts the speed of the peak and RMS bar falling down.
	 *	Defaults to 50 decibels per second. At the moment,
	 *	rise (attack) speed is infinite.
	 *
	 *	@param	decibelsPerSecond	the amount of decibels by which the bars
	 *								falls in one second
	 */
	public void setFallSpeed( float decibelsPerSecond )
	{
		synchronized( sync ) {
			fallSpeed = decibelsPerSecond / 1000;
		}
	}

	/**
	 *	Adjusts the speed of the peak hold indicator falling down.
	 *	Defaults to 15 decibels per second.
	 *
	 *	@param	decibelsPerSecond	the amount of decibels by which the peak indicator
	 *								falls in one second
	 */
	public void setHoldFallSpeed( float decibelsPerSecond )
	{
		synchronized( sync ) {
			holdFallSpeed = decibelsPerSecond / 1000;
		}
	}

	/**
	 *	Adjusts the minimum displayed amplitude, that is the
	 *	amplitude corresponding to the bottom of the bar.
	 *	Defaults to -40 decibels. At the moment, the maximum
	 *	amplitude is fixed to 0 decibels (1.0 linear).
	 *
	 *	@param	decibels	the amplitude corresponding to the
	 *						minimum bar extent
	 */
	public void setMinAmplitude( float decibels )
	{
		synchronized( sync ) {
			floorWeight = -1.0f / decibels;
			setPeakAndRMS( this.peak, this.rms );
		}
	}

	private void updateInsets()
	{
		insets = getInsets();
		final int w = 10 + insets.left + insets.right;
		setMinimumSize(   new Dimension( w, 2 + insets.top + insets.bottom ));
		setPreferredSize( new Dimension( w, getPreferredSize().height ));
		setMaximumSize(   new Dimension( w, getMaximumSize().height ));
	}
	
	public float getPeakDecibels()
	{
		synchronized( sync ) {
			return peak <= -160f ? Float.NEGATIVE_INFINITY : peak;
		}
	}

	public float getHoldDecibels()
	{
		synchronized( sync ) {
			return hold <= -160f ? Float.NEGATIVE_INFINITY : hold;
		}
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
	private boolean setPeakAndRMS( float peak, float rms )
	{
		return setPeakAndRMS( peak, rms, System.currentTimeMillis() );
	}

//	public void setPeakAndRMS( float peak, float rms )
	public boolean setPeakAndRMS( float peak, float rms, long now )
	{
		synchronized( sync ) {
	//		final long		now			= System.currentTimeMillis();
			final float		maxFall		= fallSpeed * (lastUpdate - now);	// a negative value
//			final int		oldYHold	= yHold;
//			final int		oldYPeak	= yPeak;
//			final int		oldYRMS		= yRMS;
			final boolean	result;

	//		if( logarithmic ) {
				peak		= (float) (Math.log( peak ) * logPeakCorr);
				this.peak  += Math.max( maxFall, peak - this.peak );
				peakToPaint	= Math.max( peakToPaint, this.peak );
				peakNorm	= Math.max( 0.0f, Math.min( 1.0f, this.peakToPaint * floorWeight + 1 ));

				if( rmsPainted ) {
					rms			= (float) (Math.log( rms ) * logRMSCorr);
					this.rms   += Math.max( maxFall, rms  - this.rms );
					rmsToPaint	= Math.max( rmsToPaint, this.rms );
					rmsNorm		= Math.max( 0.0f, Math.min( 1.0f, this.rmsToPaint * floorWeight + 1 ));
				}
				
				if( holdPainted ) {
					if( this.peak >= hold ) {
						hold	= this.peak;
						holdEnd	= now + holdDuration;
//						holdNorm= peakNorm;
					} else if( now > holdEnd ) {
						hold   += Math.max( holdFallSpeed * (lastUpdate - now), this.peak - hold );
//						holdNorm= Math.max( 0.0f, Math.min( 1.0f, hold * floorWeight + 1 ));
					}
					holdToPaint	= Math.max( holdToPaint, hold );
					holdNorm	= Math.max( 0.0f, Math.min( 1.0f, holdToPaint * floorWeight + 1 ));
					result		= holdNorm > 0f;
				} else {
					result		= peakNorm > 0f;
				}
				
	//		} else {
	//	
	//			this.peak	= peak;
	//			this.rms	= rms;
	//		}

			lastUpdate		= now;
			recentHeight	= (getHeight() - insets.top - insets.bottom + 1) & ~1;

			yHold	= ((int) ((1.0f - holdNorm) * recentHeight) + 1) & ~1;
			yPeak	= ((int) ((1.0f - peakNorm) * recentHeight) + 1) & ~1;
			yRMS	= ((int) ((1.0f - rmsNorm)  * recentHeight) + 1) & ~1;

			if( (yPeak != yPeakPainted) || (yRMS != yRMSPainted) || (yHold != yHoldPainted) ) {
				final int minY, maxY;

// System.out.println( "JA " + yPeak + ", " + yRMS + ", " + yHold + " ;; " + this.peak + ", " + peakToPaint + ", " + peakNorm );
				
				if( holdPainted ) {
					minY = Math.min( yHold, yHoldPainted );
					if( rmsPainted ) {
						maxY = Math.max( Math.max( yPeak, yPeakPainted ), Math.max( yRMS, yRMSPainted )) + 2;
					} else {
						maxY = Math.max( yPeak, yPeakPainted );
					}
				} else {
					if( rmsPainted ) {
						minY = Math.min( Math.min( yPeak, yPeakPainted ), Math.min( yRMS, yRMSPainted ));
						maxY = Math.max( Math.max( yPeak, yPeakPainted ), Math.max( yRMS, yRMSPainted )) + 2;
					} else {
						minY = Math.min( yPeak, yRMSPainted );
						maxY = Math.max( yPeak, yRMSPainted );
					}
				}

				if( refreshParent ) {
					getParent().repaint( insets.left + getX(), insets.top + minY + getY(), getWidth() - insets.left - insets.right,
										 maxY - minY );
				} else {
					repaint( insets.left, insets.top + minY, getWidth() - insets.left - insets.right,
							 maxY - minY );
				}
			} else {
// System.out.println( "NO " + yPeak + ", " + yRMS + ", " + yHold + " ;; " + this.peak + ", " + peakToPaint + ", " + peakNorm ); 			
				peakToPaint		= -160f;
				rmsToPaint		= -160f;
				holdToPaint		= -160f;
			}
			if( ttUpdate && (ttPeak != peak) ) {
				ttPeak = peak;
				setToolTipText( String.valueOf( ttPeak ));
			}
			
			return result;
		}
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
		synchronized( sync ) {
			return setPeakAndRMS( peak, this.rms );
		}
	}
	
	private void recalcPaint()
	{
		int[]			pix;
		int				rgb;
		final float[]	hsbTop	= new float[ 3 ];
		final float[]	hsbBot	= new float[ 3 ];
		float			w1, w2;
		final float		w3		= 1.0f / (recentHeight - 2);
	
		if( imgPeak != null ) {
			imgPeak.flush();
			imgPeak = null;
		}
		if( imgRMS != null ) {
			imgRMS.flush();
			imgRMS = null;
		}
		if( imgBg == null ) {
			imgBg = new BufferedImage( 10, 2, BufferedImage.TYPE_INT_ARGB );
			imgBg.setRGB( 0, 0, 10, 2, bgPixels, 0, 10 );
			pntBg = new TexturePaint( imgBg, new Rectangle( 0, 0, 10, 2 ));
		}
		
		pix = new int[ 10 * recentHeight ];
		for( int x = 0; x < 10; x++ ) {
			rgb = rmsTopColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbTop );
			rgb = rmsBotColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbBot );
			for( int y = 0, off = x; y < recentHeight; y += 2, off += 20 ) {
				w2				= y * w3;
				w1				= 1.0f - w2;
				rgb				= Color.HSBtoRGB( hsbTop[0] * w1 + hsbBot[0] * w2,
												  hsbTop[1] * w1 + hsbBot[1] * w2,
												  hsbTop[2] * w1 + hsbBot[2] * w2 );
				pix[ off ]		= rgb | 0xFF000000;
				pix[ off+10 ]	= 0xFF000000;
			}
		}
		imgRMS = new BufferedImage( 10, recentHeight, BufferedImage.TYPE_INT_ARGB );
		imgRMS.setRGB( 0, 0, 10, recentHeight, pix, 0, 10 );
//		pntRMS = new TexturePaint( imgRMS, new Rectangle( 0, 0, 10, recentHeight ));

		pix = new int[ 10 * recentHeight ];
		for( int x = 0; x < 10; x++ ) {
			rgb = peakTopColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbTop );
			rgb = peakBotColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbBot );
			for( int y = 0, off = x; y < recentHeight; y += 2, off += 20 ) {
				w2				= y * w3;
				w1				= 1.0f - w2;
				rgb				= Color.HSBtoRGB( hsbTop[0] * w1 + hsbBot[0] * w2,
												  hsbTop[1] * w1 + hsbBot[1] * w2,
												  hsbTop[2] * w1 + hsbBot[2] * w2 );
				pix[ off ]		= rgb | 0xFF000000;
				pix[ off+10 ]	= 0xFF000000;
			}
		}
		imgPeak = new BufferedImage( 10, recentHeight, BufferedImage.TYPE_INT_ARGB );
		imgPeak.setRGB( 0, 0, 10, recentHeight, pix, 0, 10 );
//		pntRMS = new TexturePaint( imgRMS, new Rectangle( 0, 0, 10, recentHeight ));

		calcedHeight = recentHeight;
	}
	
	
//boolean gaga;

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D		g2;
		final AffineTransform	atOrig;
		final int				h		= (getHeight() - insets.top - insets.bottom + 1) & ~1;
		
		g.setColor( Color.black );
		g.fillRect( 0, 0, getWidth(), getHeight() );
		if( h > 0 ) {
			synchronized( sync ) {	
				if( (h != recentHeight) || (calcedHeight != recentHeight) ) {
					recentHeight = h;
					recalcPaint();
					yPeak		= ((int) ((1.0f - peakNorm) * h) + 1) & ~1;
					yRMS		= ((int) ((1.0f - rmsNorm)  * h) + 1) & ~1;
					yHold		= ((int) ((1.0f - holdNorm) * h) + 1) & ~1;
				}
				
				g2		= (Graphics2D) g;
				atOrig	= g2.getTransform();
				g2.translate( insets.left, insets.top );
	
				g2.setPaint( pntBg );
//g2.setPaint( gaga ? Color.black : Color.white ); gaga = !gaga;
		//		g2.fillRect( 1, 0, 10, yPeak );
				if( rmsPainted ) {
					g2.fillRect( 0, 0, 10, yRMS + 1 );
					if( holdPainted ) g2.drawImage( imgPeak, 0, yHold, 10, yHold + 1, 0, yHold, 10, yHold + 1, this );
					g2.drawImage( imgPeak, 0, yPeak,    10, yRMS, 0, yPeak,    10, yRMS, this );
					g2.drawImage( imgRMS,  0, yRMS + 2, 10, h,    0, yRMS + 2, 10, h,    this );
				} else {
					g2.fillRect( 0, 0, 10, yPeak );
					if( holdPainted ) g2.drawImage( imgPeak, 0, yHold, 10, yHold + 1, 0, yHold, 10, yHold + 1, this );
					g2.drawImage( imgPeak, 0, yPeak, 10, h, 0, yPeak, 10, h, this );
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
			}
		}
	}
	
	// --------------- Disposable interface ---------------
	
	public void dispose()
	{
		synchronized( sync ) {
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
		}
	}
}