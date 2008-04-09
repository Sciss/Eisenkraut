/*
 *  DecimatedWaveTrail.java
 *  Eisenkraut
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
 *  Changelog:
 *      11-Jan-06	created
 *		12-Jul-06	added fullwave peak/rms support
 *		27-Mar-07	fixed cache support
 *		18-Feb-08	renamed from DecimatedTrail to DecimatedWaveTrail
 */

package de.sciss.eisenkraut.io;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.undo.CompoundEdit;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.common.ProcessingThread;
import de.sciss.eisenkraut.gui.WaveformView;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileCacheInfo;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.CacheManager;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;
import de.sciss.timebased.BasicTrail;
import de.sciss.util.MutableInt;

/**
 * @version 0.70, 12-Nov-07
 * 
 * @todo common superclass of AudioTrail and DecimatedTrail
 * @todo drawWaveform : the initial idea was that readFrames should be removed ;
 *       instead of filling "missing" samples, the polygon creation should use
 *       biased x position. also for coherency, drawPCM should use a Polygon not
 *       GeneralPath
 */
public class DecimatedWaveTrail
extends BasicTrail
{
	private static final int		UPDATE_PERIOD			= 2000; // millisecs in async overview calculation

	private static final boolean	DEBUG					= false;

	public static final int			MODEL_PCM				= 0;
	public static final int			MODEL_HALFWAVE_PEAKRMS	= 1;
	public static final int			MODEL_MEDIAN			= 2;
	public static final int			MODEL_FULLWAVE_PEAKRMS	= 3;

	// dependance tracking that take longer than this amount in millisecs
	// will be queued on the async thread
	// private static final int INPLACE_TIMEOUT = 2000;

	private final int				modelChannels;
	protected final int				decimChannels;
	protected final int				fullChannels;
	private final int				model;

	private AudioFile[]				tempF					= null; // lazy
	protected final DecimationHelp[] decimHelps;
	protected final AudioTrail		fullScale;

	private final int				SUBNUM;
	private final int				MAXSHIFT;
	protected final int				MAXCOARSE;
	private final long				MAXMASK;
	private final int				MAXCEILADD;

	protected float[][]				tmpBuf					= null; // lazy
	private final int				tmpBufSize;
	protected float[][]				tmpBuf2					= null; // lazy
	private final int				tmpBufSize2;

	private final Decimator			decimator;

	// waveform drawing
	// private static final Stroke strkLine = new BasicStroke( 0.5f );
	private static final Stroke		strkLine				= new BasicStroke(2.0f);

	private static final Paint		pntLine					= Color.black;

	// private static final Paint pntArea = new Color( 0x00, 0x00, 0x00, 0x7F );
	// private static final Paint pntNull = new Color( 0x7F, 0x7F, 0x00, 0xC0 );
	// private static final Stroke strkNull = new BasicStroke( 1.0f,
	// BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
	// 1.0f, new float[] { 4.0f, 4.0f }, 0.0f );
	private static final Paint		pntBusy;

	private static final int[]		busyPixels = {
			0xFFCBCBCB, 0xFFC0C0C0, 0xFFA8A8A8,
			0xFFE6E6E6, 0xFFB2B2B2, 0xFFCACACA,
			0xFFB1B1B1, 0xFFD5D5D5, 0xFFC0C0C0 };

	protected final Object			bufSync					= new Object();
	private final Object			fileSync				= new Object();

	private final List				drawBusyList			= new ArrayList();

	protected Thread				threadAsync				= null;
	private AudioFile[]				tempFAsync				= null; // lazy
	protected volatile boolean		keepAsyncRunning		= false;

	protected EventManager			asyncManager			= null;
	
	protected static final double	TWENTYBYLOG10			= 20 / Math.log( 10 ); // 8.685889638065;
	protected static final double	TENBYLOG10				= 10 / Math.log( 10 );

	static {
		final BufferedImage img = new BufferedImage( 3, 3, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 3, 3, busyPixels, 0, 3 );
		pntBusy = new TexturePaint( img, new Rectangle( 0, 0, 3, 3 ));
	}

	// public DecimatedTrail( DecimatedTrail parentSub, int shift )
	// {
	// super();
	//
	// final int factor = 1 << shift;
	//	
	// decimChannels = parentSub.getChannelNum();
	// this.shift = shift;
	// roundAdd = factor >> 1;
	// mask = -factor;
	//
	// setRate( fullScale.getRate() / factor );
	//		
	// parentTrail = parentSub;
	// parentTrail.addDependant( this );
	// }

	protected BasicTrail createEmptyCopy()
	{
		throw new IllegalStateException( "Not allowed" );
	}

	public DecimatedWaveTrail( AudioTrail fullScale, int model, int[] decimations )
	throws IOException
	{
		super();

		switch( model ) {
		case MODEL_HALFWAVE_PEAKRMS:
			modelChannels	= 4;
			decimator		= new HalfPeakRMSDecimator();
			break;
		case MODEL_MEDIAN:
			modelChannels	= 1;
			decimator		= new MedianDecimator();
			break;
		case MODEL_FULLWAVE_PEAKRMS:
			modelChannels	= 3;
			decimator		= new FullPeakRMSDecimator();
			break;
		default:
			throw new IllegalArgumentException( "Model " + model );
		}

		fullChannels	= fullScale.getChannelNum();
		decimChannels	= fullChannels * modelChannels;
		this.model		= model;

		SUBNUM			= decimations.length; // the first 'subsample' is actually fullrate
		this.decimHelps	= new DecimationHelp[ SUBNUM ];
		for( int i = 0; i < SUBNUM; i++ ) {
			this.decimHelps[ i ] = new DecimationHelp( fullScale.getRate(), decimations[ i ] );
		}
		MAXSHIFT		= decimations[ SUBNUM - 1 ];
		MAXCOARSE		= 1 << MAXSHIFT;
		MAXMASK			= -MAXCOARSE;
		MAXCEILADD		= MAXCOARSE - 1;

		tmpBufSize		= Math.max( 4096, MAXCOARSE << 1 );
		// tmpBuf = new float[channels][tmpBufSize];
		tmpBufSize2		= SUBNUM > 0 ? Math.max( 4096, tmpBufSize >> decimations[ 0 ]) : tmpBufSize;
		// System.err.print( "tmpBufSize2 : "+tmpBufSize2 );
		// tmpBuf2 = new float[modelChannels * channels][tmpBufSize2];

		// setRate( fullScale.getRate() / factor );
		setRate( fullScale.getRate() );

		this.fullScale	= fullScale;
		fullScale.addDependant( this );

		// ok, the fullScale file might have already been populated
		// final List stakes = fullScale.getAll( true );
		// if( !stakes.isEmpty() ) {
		// XXX TEST
		// addAllDep( null, stakes, null, fullScale.getSpan() );
		addAllDepAsync();
		// addAllDepAsync( null, stakes, null, fullScale.getSpan() );
		// }
	}

	public int getDefaultTouchMode() { return TOUCH_SPLIT; }
	public int getChannelNum() { return decimChannels; }
	public int getNumModelChannels() { return modelChannels; }
	public int getNumDecimations() { return SUBNUM; }
	public int getModel() { return model; }

	// private void drawPCM( float[] frames, int len, GeneralPath path, float
	// offX, float scaleX, float offY, float scaleY,
	// boolean initial )
	// {
	// if( scaleX <= 4 ) {
	// if( initial ) {
	// path.moveTo( offX, frames[ 0 ] * scaleY + offY );
	// }
	// for( int i = initial ? 1 : 0; i < len; i++ ) {
	// path.lineTo( i * scaleX + offX, frames[ i ] * scaleY + offY );
	// }
	// } else {
	// float f3 = scaleX + offX;
	// float f1;
	//			
	// if( initial ) {
	// path.moveTo( offX, frames[ 0 ] * scaleY + offY );
	// } else {
	// path.lineTo( offX, frames[ 0 ] * scaleY + offY );
	// }
	// path.lineTo( f3, frames[ 0 ] * scaleY + offY );
	// for( int i = 1; i < len; i++ ) {
	// f1 = frames[ i ] * scaleY + offY;
	// path.lineTo( f3, f1 );
	// f3 = (i + 1) * scaleX + offX;
	// path.lineTo( f3, f1 );
	// }
	// }
	// }

	// private void drawHalfWavePeakRMS( float[] sPeakP, float[] sPeakN, float[]
	// sRMSP, float[] sRMSN, int len,
	// GeneralPath pPeakP, GeneralPath pPeakN, GeneralPath pRMSP, GeneralPath
	// pRMSN,
	// float offX, float scaleX, float offY, float scaleY )
	// {
	// float f1;
	// final float scaleYN = -scaleY;
	//	
	// // if( initial ) {
	// // pPeakP.moveTo( offX, sPeakP[ 0 ] * scaleY + offY );
	// // pPeakN.moveTo( offX, sPeakN[ 0 ] * scaleY + offY );
	// // pRMSP.moveTo( offX, sRMSP[ 0 ] * scaleY + offY );
	// // pRMSN.moveTo( offX, sRMSN[ 0 ] * scaleY + offY );
	// // }
	// // for( int i = initial ? 1 : 0; i < len; i++ ) {
	// for( int i = 0; i < len; i++ ) {
	// f1 = i * scaleX + offX;
	// pPeakP.lineTo( f1, sPeakP[ i ] * scaleY + offY );
	// pPeakN.lineTo( f1, sPeakN[ i ] * scaleY + offY );
	// pRMSP.lineTo( f1, (float) Math.sqrt( sRMSP[ i ]) * scaleY + offY );
	// pRMSN.lineTo( f1, (float) Math.sqrt( sRMSN[ i ]) * scaleYN + offY );
	// }
	// }

	// private void drawHalfWavePeakRMS( float[] sPeakP, float[] sPeakN, float[]
	// sRMSP, float[] sRMSN, int len,
	// int[] peakPolyX, int[] peakPolyY, int[] rmsPolyX, int[] rmsPolyY, int
	// off,
	// float offX, float scaleX, float offY, float scaleY )
	// {
	// final float scaleYN = -scaleY;
	// int x;
	//	
	// for( int i = 0, j = off, k = peakPolyX.length - 1 - off; i < len; i++,
	// j++, k-- ) {
	// x = (int) (i * scaleX + offX);
	// peakPolyX[ j ] = x;
	// peakPolyX[ k ] = x;
	// rmsPolyX[ j ] = x;
	// rmsPolyX[ k ] = x;
	//			
	// peakPolyY[ j ] = (int) (sPeakP[ i ] * scaleY + offY);
	// peakPolyY[ k ] = (int) (sPeakN[ i ] * scaleY + offY);
	// rmsPolyY[ j ] = (int) ((float) Math.sqrt( sRMSP[ i ]) * scaleY + offY);
	// rmsPolyY[ k ] = (int) ((float) Math.sqrt( sRMSN[ i ]) * scaleYN + offY);
	// }
	// }

	/*
	 * TO-DO : should use Math.max( peakN, peakP ) should omit all zero points
	 * should align drawing on bottom
	 * 
	 * private int drawLogPeakRMS( float[] sPeakP, float[] sPeakN, float[] sRMS,
	 * int len, int[] peakPolyX, int[] peakPolyY, int[] rmsPolyX, int[]
	 * rmsPolyY, int off, float offX, float scaleX, float scaleY ) { // final
	 * float scaleYN = -scaleY; int x; float peakP;
	 * 
	 * for( int i = 0, k = peakPolyX.length - 1 - off; i < len; i++, off++, k-- ) {
	 * x = (int) (i * scaleX + offX); peakPolyX[ off ] = x; peakPolyX[ k ] = x;
	 * rmsPolyX[ off ] = x; rmsPolyX[ k ] = x; peakP = sPeakP[ i ]; // peakN =
	 * sPeakN[ i ]; peakPolyY[ off ] = (int) ((Math.log( Math.max( 0.001, peakP )) /
	 * 6.9077552789821 + 1) * scaleY ) + 2; peakPolyY[ k ] = 0; // peakC =
	 * (peakP + peakN) / 2; // rms = (float) Math.sqrt( sRMS[ i ]); // / 2; //
	 * rmsPolyY[ off ] = (int) (Math.min( peakP, rms ) * scaleY); rmsPolyY[ off ] =
	 * (int) ((Math.log( Math.max( 1.0e-6, sRMS[ i ] )) / 13.815510557964 + 1) *
	 * scaleY ) + 2; rmsPolyY[ k ] = 0; }
	 * 
	 * return off; }
	 */

	private int drawPCM( float[] frames, int len, int[] polyX, int[] polyY,
			int off, float offX, float scaleX, float scaleY,
			boolean sampleAndHold )
	{
		int x, y;

		if( sampleAndHold ) {
			x = (int) offX;
			for( int i = 0; i < len; ) {
				y				= (int) (frames[ i ] * scaleY);
				polyX[ off ]	= x;
				polyY[ off ]	= y;
				off++;
				i++;
				x				= (int) (i * scaleX + offX);
				polyX[ off ]	= x;
				polyY[ off ]	= y;
				off++;
			}
		} else {
			for( int i = 0; i < len; i++, off++ ) {
				x				= (int) (i * scaleX + offX);
				polyX[ off ]	= x;
				polyY[ off ]	= (int) (frames[ i ] * scaleY);
			}
		}

		return off;
	}
	
	/**
	 * Speed measurements (feb 2006): for HalfwavePeakRMS, using g2.fillPolygon
	 * is about twice as fast as using GeneralPath objects. The integer
	 * resolution can be compensated for by scaling the points by factor 4.0 and
	 * scaling the Graphics2D by 1/4 at no significant CPU cost.
	 * 
	 * @synchronization must be called in the event thread
	 */
	public void drawWaveform( DecimationInfo info, WaveformView view, Graphics2D g2 )
	{
		final boolean			fromPCM 		= info.idx == -1;
		final boolean			toPCM			= fromPCM && (info.inlineDecim == 1);
		// final long maxLen = toPCM ? tmpBufSize : (fromPCM ? Math.min(
		// tmpBufSize, tmpBufSize2 * info.inlineDecim ) : tmpBufSize2);
		final long				maxLen			= toPCM ? tmpBufSize : (fromPCM ?
				  Math.min( tmpBufSize, tmpBufSize2 * info.getDecimationFactor() )
				: tmpBufSize2 << info.shift);
//		final int				polySize		= view.isLogarithmic() ?
//			((int) info.sublength + 2) : ((int) (info.sublength << 1));
		final int				polySize		= (int) (info.sublength << 1);
		final AffineTransform	atOrig			= g2.getTransform();
		final Shape				clipOrig		= g2.getClip();

		final int[][]			peakPolyX		= new int[ fullChannels ][ polySize ];
		final int[][]			peakPolyY		= new int[ fullChannels ][ polySize ];
		final int[][]			rmsPolyX		= toPCM ? null : new int[ fullChannels ][ polySize ];
		final int[][]			rmsPolyY		= toPCM ? null : new int[ fullChannels ][ polySize ];
		final boolean[]			sampleAndHold	= toPCM ? new boolean[ fullChannels ] : null;
		final float				maxY, minY, minInpY, deltaY, deltaYN;
		final float				offY;
		final int[]				off				= new int[ fullChannels ];

		float[]					sPeakP;
		float					offX, scaleX, scaleY, f1;
		long					start			= info.span.start;
		long					totalLength		= info.getTotalLength();
		Span					chunkSpan;
		long					fullLen, fullStop;
		int						chunkLen, decimLen;
		Rectangle				r;

		try {
			drawBusyList.clear(); // "must be called in the event thread"
			
			if( view.isLogarithmic() ) {
				maxY	= view.getLogMax();
				minY	= view.getLogMin();
				minInpY = (float) Math.exp( minY / TWENTYBYLOG10 );
			} else {
				maxY	= view.getLinearMax();
				minY	= view.getLinearMin();
				minInpY	= 0;	// not used
			}
			deltaY	= maxY - minY;
			deltaYN = -4 / deltaY;
			offY	= maxY / deltaY;

//System.out.println( "deltaY " + deltaY + "; deltaYN " + deltaYN + "; offY " + offY );
			
			synchronized( bufSync ) {
				createBuffers();

				while( totalLength > 0 ) {
					fullLen		= Math.min( maxLen, totalLength );
					chunkLen	= (int) (fromPCM ? fullLen : decimHelps[ info.idx ].fullrateToSubsample( fullLen ));
					decimLen	= chunkLen / info.inlineDecim;
					chunkLen	= decimLen * info.inlineDecim;
					fullLen		= (long) chunkLen << info.shift;
					chunkSpan	= new Span( start, start + fullLen );

					if( fromPCM ) {
						fullStop = fullScale.getSpan().stop;
						if( start + fullLen <= fullStop ) {
							chunkSpan = new Span( start, start + fullLen );
							fullScale.readFrames( tmpBuf, 0, chunkSpan );
						} else {
							chunkSpan = new Span( start, fullStop );
							fullScale.readFrames( tmpBuf, 0, chunkSpan );
							// duplicate last frames
							for( int i = (int) chunkSpan.getLength(), j = i - 1; i < (int) fullLen; i++ ) {
								for( int ch = 0; ch < fullChannels; ch++ ) {
									sPeakP		= tmpBuf[ ch ];
									sPeakP[ i ]	= sPeakP[ j ];
								}
							}
						}
						if( !toPCM ) decimator.decimatePCM( tmpBuf, tmpBuf2, 0, decimLen, info.inlineDecim );
					} else {
						chunkSpan = new Span( start, start + fullLen );
						readFrames( info.idx, tmpBuf2, 0, drawBusyList, chunkSpan, null);
						if( info.inlineDecim > 1 ) decimator.decimate( tmpBuf2, tmpBuf2, 0, decimLen, info.inlineDecim );
					}
					if( toPCM ) {
						if( view.isLogarithmic() ) {
							for( int ch = 0; ch < fullChannels; ch++ ) {
								sPeakP = tmpBuf[ ch ];
								for( int i = 0; i < decimLen; i++ ) {
									f1 = Math.abs( sPeakP[ i ]);
									if( f1 > minInpY ) {
										sPeakP[ i ] = (float) (Math.log( f1 ) * TWENTYBYLOG10);
									} else {
										sPeakP[ i ] = minY;
									}
								}
							}
						}
						for( int ch = 0; ch < fullChannels; ch++ ) {
							sPeakP				= tmpBuf[ ch ];
							r					= view.rectForChannel( ch );
							scaleX				= 4 * r.width / (float) (info.sublength - 1);
							scaleY				= r.height * deltaYN;
							offX				= scaleX * off[ ch ];
							sampleAndHold[ch]	= scaleX > 16;
							off[ch]				= drawPCM( sPeakP, decimLen, peakPolyX[ ch ],
								       			           peakPolyY[ ch ], off[ ch ], offX, scaleX, scaleY, sampleAndHold[ ch ]);
						}
					} else {
						if( view.isLogarithmic() ) {
							for( int ch = 0; ch < fullChannels; ch++ ) {
								off[ ch ] = decimator.drawLog( info, ch, peakPolyX, peakPolyY, rmsPolyX, rmsPolyY, decimLen, view.rectForChannel( ch ), deltaYN, off[ ch ], minY, minInpY );
							}
						} else {
							for( int ch = 0; ch < fullChannels; ch++ ) {
								off[ ch ] = decimator.draw( info, ch, peakPolyX, peakPolyY, rmsPolyX, rmsPolyY, decimLen, view.rectForChannel( ch ), deltaYN, off[ ch ]);
							}
						}
					}
					start += fullLen;
					totalLength -= fullLen;
				}
			} // synchronized( bufSync )

			// System.err.println( "busyList.size() = "+busyList.size() );

			if( toPCM ) {
				final Stroke strkOrig = g2.getStroke();
				g2.setStroke( strkLine );
				g2.setPaint( pntLine );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					r = view.rectForChannel( ch );
					g2.clipRect( r.x, r.y, r.width, r.height );
					g2.translate( r.x, r.y + r.height * offY );
					g2.scale( 0.25f, 0.25f );
					g2.drawPolyline( peakPolyX[ ch ], peakPolyY[ ch ], off[ ch ]);
					g2.setTransform( atOrig );
					g2.setClip( clipOrig );
				}
				g2.setStroke( strkOrig );
			} else {
				// g2.setPaint( pntArea );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					r = view.rectForChannel( ch );
					g2.clipRect( r.x, r.y, r.width, r.height );
					if (!drawBusyList.isEmpty()) {
						// g2.setColor( Color.red );
						g2.setPaint( pntBusy );
						for (int i = 0; i < drawBusyList.size(); i++) {
							chunkSpan = (Span) drawBusyList.get(i);
							scaleX = r.width / (float) info.getTotalLength(); // (info.sublength
																				// -
																				// 1);
							// System.err.println( "scaleX = "+scaleX+ ";
							// chunkSpan "+chunkSpan );
							// System.err.println( "fill " + ((int)
							// ((chunkSpan.start - info.span.start) * scaleX) +
							// r.x) + ", "+r.y+", "+((int)
							// (chunkSpan.getLength() * scaleX))+", "+r.height
							// );
							g2.fillRect(
							    (int) ((chunkSpan.start - info.span.start) * scaleX) + r.x,
							    r.y,
							    (int) (chunkSpan.getLength() * scaleX),
							    r.height );
						}
					}
					g2.translate( r.x, r.y + r.height * offY );
					g2.scale( 0.25f, 0.25f );
					g2.setColor( Color.gray );
					// g2.setColor( Color.black );
					g2.fillPolygon( peakPolyX[ ch ], peakPolyY[ ch ], polySize );
					g2.setColor( Color.black );
					// g2.setColor( Color.gray );
					g2.fillPolygon( rmsPolyX[ ch ], rmsPolyY[ ch ], polySize );
					g2.setTransform( atOrig );
					g2.setClip( clipOrig );
				}
			}
		} catch( IOException e1 ) {
			System.err.println( e1 );
		}
	}

	/**
	 * Determines which subsampled version is suitable for a given display range
	 * (the most RAM and CPU economic while maining optimal display resolution).
	 * For a given time span, the lowest resolution is chosen which will produce
	 * at least <code>minLen</code> frames.
	 * 
	 * @param tag
	 *            the time span the caller is interested in
	 * @param minLen
	 *            the minimum number of sampled points wanted.
	 * @return an information object describing the best subsample of the track
	 *         editor. note that info.sublength will be smaller than minLen if
	 *         tag.getLength() was smaller than minLen (in this case the
	 *         fullrate version is used).
	 */
	public DecimationInfo getBestSubsample( Span tag, int minLen )
	{
		final DecimationInfo	info;
		final boolean			fromPCM, toPCM;
		final long				fullLength	= tag.getLength();
		long					subLength, n;
		int						idx, inlineDecim;

		subLength = tag.getLength();
		for( idx = 0; idx < SUBNUM; idx++ ) {
			n = decimHelps[ idx ].fullrateToSubsample( fullLength );
			if( n < minLen ) break;
			subLength = n;
		}
		idx--;
		// had to change '>= minLen' to '> minLen' because minLen could be zero!
		switch( model ) {
		case MODEL_HALFWAVE_PEAKRMS:
		case MODEL_FULLWAVE_PEAKRMS:
			for( inlineDecim = 2; subLength / inlineDecim > minLen; inlineDecim++ ) ;
			inlineDecim--;
			break;

		case MODEL_MEDIAN:
			inlineDecim = 1;
			break;

		default:
			assert false : model;
			inlineDecim = 1; // never gets here
		}
		subLength /= inlineDecim;
		// System.err.println( "minLen = "+minLen+"; subLength = "+subLength+";
		// inlineDecim = "+inlineDecim+" ; idx = "+idx );
		fromPCM	= idx == -1;
		toPCM	= fromPCM && inlineDecim == 1;
		info	= new DecimationInfo( tag, subLength, toPCM ? fullChannels : decimChannels, idx,
						fromPCM ?				0 : decimHelps[ idx ].shift,
						inlineDecim, toPCM ?	MODEL_PCM : model );
		return info;
	}

	/**
	 * Reads a block of subsampled frames.
	 * 
	 * @param info
	 *            the <code>DecimationInfo</code> as returned by
	 *            <code>getBestSubsample</code>, describing the span to read
	 *            and which resolution to choose.
	 * @param frames
	 *            to buffer to fill, where frames[0][] corresponds to the first
	 *            channel etc. and the buffer length must be at least off +
	 *            info.sublength!
	 * @param off
	 *            offset in frames, such that the first frame will be placed in
	 *            frames[ch][off]
	 * @throws IOException
	 *             if a read error occurs
	 * @see #getBestSubsample( Span, int )
	 * @see DecimationInfo#sublength
	 */
	public boolean readFrame( int sub, long pos, int ch, float[] data )
	throws IOException
	{
		synchronized( bufSync ) {
			createBuffers();

			final int				idx	= indexOf( pos, true );
			final DecimatedWaveStake	ds	= (DecimatedWaveStake) editGetLeftMost( idx, true, null );
			if( ds == null ) return false;

			if( !ds.readFrame( sub, tmpBuf2, 0, pos )) return false;

			for( int i = ch * modelChannels, k = 0; k < modelChannels; i++, k++ ) {
				data[ k ] = tmpBuf2[ i ][ 0 ];
			}

			return true;
		}
	}

	/*
	 * Same as in <code>NondestructiveDecimatedSampledTrack</code> but with
	 * automaic bias adjust.
	 * 
	 * @param tag unbiased fullrate span @param frames buffer to fill. note that
	 * this will not do any interpolation but fill at the decimated rate! @param
	 * framesOff offset in frames for the first frame which is read
	 * 
	 * @see NondestructiveDecimatedSampledTrack#read( Span, float[][], int )
	 */
	private void readFrames( int sub, float[][] data, int dataOffset, List busyList,
							 Span readSpan, AbstractCompoundEdit ce )
	throws IOException
	{
		int					idx			= editIndexOf( readSpan.start, true, ce );
		if( idx < 0 ) idx = -(idx + 2);
		final long			startR		= decimHelps[sub].roundAdd - readSpan.start;
		final List			coll		= editGetCollByStart( ce );
		final MutableInt	readyLen	= new MutableInt( 0 );
		final MutableInt	busyLen		= new MutableInt( 0 );
		DecimatedWaveStake	stake;
		int					chunkLen, discrepancy;
		Span				subSpan;
		int					readOffset, nextOffset = dataOffset;
		int					len			= (int) (readSpan.getLength() >> decimHelps[ sub ].shift);

		while( (len > 0) && (idx < coll.size()) ) {
			stake		= (DecimatedWaveStake) coll.get( idx );
			subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
									Math.min( stake.getSpan().stop, readSpan.stop ));
			stake.readFrames( sub, data, nextOffset, subSpan, readyLen, busyLen );
			chunkLen	= readyLen.value() + busyLen.value();
			readOffset	= nextOffset + readyLen.value(); // chunkLen;
			nextOffset	= (int) ((subSpan.stop + startR) >> decimHelps[ sub ].shift) + dataOffset;
			discrepancy	= nextOffset - readOffset;
			len 	   -= readyLen.value() + discrepancy;
			if( busyLen.value() == 0 ) {
				if( discrepancy > 0 ) {
					if( readOffset > 0 ) {
						for( int i = readOffset, k = readOffset - 1; i < nextOffset; i++ ) {
							for( int j = 0; j < data.length; j++ ) {
								data[ j ][ i ] = data[ j ][ k ];
							}
						}
					}
				}
			} else {
				busyList.add( new Span( subSpan.stop - (subSpan.getLength() * busyLen.value() / chunkLen),
										subSpan.stop ));
				for( int i = Math.max( 0, readOffset ); i < nextOffset; i++ ) {
					for( int j = 0; j < data.length; j++ ) {
						data[ j ][ i ] = 0f;
					}
				}
			}
			idx++;
		}
	}

	public void debugDump()
	{
		for( int i = 0; i < getNumStakes(); i++ ) {
			((DecimatedWaveStake) get( i, true )).debugDump();
		}
	}

	/*
	 * @synchronization call within synchronoized( bufSync ) block
	 */
	private void createBuffers()
	{
		if( !Thread.holdsLock( bufSync )) throw new IllegalMonitorStateException();

		if( tmpBuf == null ) {
			tmpBuf	= new float[ fullChannels  ][ tmpBufSize ];
			tmpBuf2	= new float[ decimChannels ][ tmpBufSize2 ];
		}
	}

	private void freeBuffers()
	{
		synchronized( bufSync ) {
			tmpBuf	= null;
			tmpBuf2	= null;
		}
	}

	private void freeTempFiles()
	{
		synchronized( fileSync ) {
			if( tempF != null ) {
				deleteTempFiles( tempF );
			}
			// XXX THIS IS THE PLACE TO KEEP WAVEFORM CACHE FILE
			if( tempFAsync != null ) {
				deleteTempFiles( tempFAsync );
			}
		}
	}

	private void killAsyncThread()
	{
		if( threadAsync != null ) {
			synchronized( threadAsync ) {
				if( threadAsync.isAlive() ) {
					keepAsyncRunning = false;
					try {
						threadAsync.wait();
					} catch( InterruptedException e1 ) {
						System.err.println( e1 );
					}
				}
			}
		}
	}

	public boolean isBusy()
	{
		return( (threadAsync != null) && threadAsync.isAlive() );
	}

	public void addAsyncListener( AsyncListener l )
	{
		if( !isBusy() ) {
			l.asyncFinished( new AsyncEvent( this, AsyncEvent.FINISHED, System.currentTimeMillis()) );
			return;
		}
		if( asyncManager == null ) {
			asyncManager = new EventManager( new EventManager.Processor() {
				public void processEvent( BasicEvent e ) {
					final AsyncEvent	ae = (AsyncEvent) e;
					AsyncListener		al;

					for( int i = 0; i < asyncManager.countListeners(); i++ ) {
						al = (AsyncListener) asyncManager.getListener( i );
						switch( e.getID() ) {
						case AsyncEvent.UPDATE:
							al.asyncUpdate( ae );
							break;
						case AsyncEvent.FINISHED:
							al.asyncFinished( ae );
							break;
						default:
							assert false : e.getID();
							break;
						}
					}
				}
			});
		}
		asyncManager.addListener( l );
	}

	public void removeAsyncListener( AsyncListener l )
	{
		if( asyncManager != null ) asyncManager.removeListener( l );
	}

	// ----------- dependant implementation -----------

	public void dispose()
	{
		// System.err.println( "DecimatedTrail.dispose()" );
		killAsyncThread(); // this has to be the first step
		fullScale.removeDependant( this );
		freeBuffers();
		freeTempFiles();
		super.dispose();
	}

	// public void insert( Object source, Span span, CompoundEdit ce )
	// {
	// insert( source, span, TOUCH_SPLIT, ce );
	// }
	//
	// public void remove( Object source, Span span, CompoundEdit ce )
	// {
	// remove( source, span, TOUCH_SPLIT, ce );
	// }

	// handled by superclass
	// public void insert( Object source, Span span, int touchMode, CompoundEdit
	// ce )
	// {
	// System.err.println( "insert" );
	// }

	// handled by superclass
	// public void remove( Object source, Span span, int touchMode, CompoundEdit
	// ce )
	// {
	// System.err.println( "remove" );
	// }

	// public void add( Object source, Stake stake, CompoundEdit ce )
	// {
	// final DecimatedStake as = (DecimatedStake) stake;
	// System.err.println( "add" );
	//
	// // ____ dep ____
	// if( dependants != null ) {
	// synchronized( dependants ) {
	// for( int i = 0; i < dependants.size(); i++ ) {
	// ((Trail) dependants.get( i )).add( source, stake, ce );
	// }
	// }
	// }
	// }

	// private void addAllDepAsync( Object source, List stakes, SyncCompoundEdit
	// ce, Span union )
	private void addAllDepAsync()
	throws IOException
	{
		if( threadAsync != null ) throw new IllegalStateException();

		final List					stakes		= fullScale.getAll(true);
		if( stakes.isEmpty() ) return;

		final DecimatedWaveStake		das;
		final Span					union		= fullScale.getSpan();
		final Span					extSpan;
		final long					fullrateStop, fullrateLen; // , insertLen;
		final int					numFullBuf;
//		final Object				enc_this	= this;
		// final CacheManager cm = CacheManager.getInstance();
		final AbstractCompoundEdit	ce			= null; // XXX
		final Object				source		= null; // XXX
		final AudioStake			cacheReadAS;
		final AudioStake			cacheWriteAS;
		final DecimatedWaveTrail	enc_this	= this;

		synchronized( fileSync ) {
			das			= allocAsync( union );
		}
		extSpan			= das.getSpan();
		// insertLen	= extSpan.getLength();
		fullrateStop	= Math.min( extSpan.getStop(), fullScale.editGetSpan( ce ).stop );
		fullrateLen		= fullrateStop - extSpan.getStart();

		cacheReadAS		= openCacheForRead( model );
		if( cacheReadAS == null ) {
			// cacheWriteAS = fullScale.openCacheForWrite( model,
			// decimHelps[ 0 ].fullrateToSubsample( union.getLength() ));
			cacheWriteAS = openCacheForWrite( model, (fullrateLen + MAXCEILADD) & MAXMASK );
			numFullBuf	= (int) (fullrateLen >> MAXSHIFT);
		} else {
			// cached files always have integer fullBufs!
			numFullBuf	= (int) ((fullrateLen + MAXCEILADD) >> MAXSHIFT);
			cacheWriteAS = null;
		}

		synchronized( bufSync ) {
			createBuffers();
		}

		editClear( source, das.getSpan(), ce );
		editAdd( source, das, ce );

		threadAsync = new Thread( new Runnable() {
			public void run()
			{
				final int			minCoarse;
				final CacheManager	cm					= PrefCacheManager.getInstance();
				long				pos;
				// long framesWritten = 0;
				long				framesWrittenCache	= 0;
				boolean				cacheWriteComplete	= false;
				Span				tag2;
				float				f1;
				int					len;
				long				time;
				long				nextTime			= System.currentTimeMillis() + UPDATE_PERIOD;

				if( cacheReadAS != null ) {
					pos = decimHelps[ 0 ].fullrateToSubsample( extSpan.getStart() );
				} else {
					pos = extSpan.getStart();
				}
				minCoarse = MAXCOARSE >> decimHelps[ 0 ].shift;

				try {
					for( int i = 0; (i < numFullBuf) && keepAsyncRunning; i++ ) {
						synchronized( bufSync ) {
							if( cacheReadAS != null ) {
								// System.out.println( "tmpBuf2.length = "+tmpBuf2.length+"; fullChannels = "+fullChannels + "; decimChannels = "+decimChannels );
								tag2 = new Span( pos, pos + minCoarse );
								cacheReadAS.readFrames( tmpBuf2, 0, tag2 );
								das.continueWrite( 0, tmpBuf2, 0, minCoarse );
								subsampleWrite2( tmpBuf2, das, minCoarse );
								pos += minCoarse;
							} else {
								tag2 = new Span(pos, pos + MAXCOARSE);
								// fullScale.readFrames( tmpBuf, 0, tag2, ce );
								fullScale.readFrames(tmpBuf, 0, tag2, null);
								// for( int k = 0; k < tmpBuf.length; k++ ) {
								// for( int j = 0; j < MAXCOARSE; j++ ) {
								// tmpBuf[ k ][ j ] = 0.125f; }}
								subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE,
								                cacheWriteAS, framesWrittenCache );
								pos += MAXCOARSE;
								framesWrittenCache += minCoarse;
							}
							// framesWritten += MAXCOARSE;
						}
						time = System.currentTimeMillis();
						if( time >= nextTime ) {
							nextTime = time + 2000;
							if( asyncManager != null ) {
								asyncManager.dispatchEvent( new AsyncEvent(
										enc_this, AsyncEvent.UPDATE, time ));
							}
						}
					}

					 // cached files always have integer fullBufs!
					if( (cacheReadAS == null) && keepAsyncRunning ) {
						len = (int) (fullrateStop - pos);
						if( len > 0 ) {
							synchronized( bufSync ) {
								tag2 = new Span( pos, pos + len );
								// fullScale.readFrames( tmpBuf, 0, tag2, ce );
								fullScale.readFrames( tmpBuf, 0, tag2, null );
								for( int ch = 0; ch < fullChannels; ch++ ) {
									f1 = tmpBuf[ch][len - 1];
									for( int i = len; i < MAXCOARSE; i++ ) {
										tmpBuf[ch][i] = f1;
									}
								}
								subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE,
								                cacheWriteAS, framesWrittenCache );
								pos += MAXCOARSE;
								// framesWritten += MAXCOARSE;
								framesWrittenCache += minCoarse;
							}
						}
					}
					if( keepAsyncRunning ) {
						cacheWriteComplete = true;
						if( cacheWriteAS != null ) cacheWriteAS.addToCache( cm );
					}
				} catch( IOException e1 ) {
					e1.printStackTrace();
				} finally {
					if( cacheReadAS != null ) {
						cacheReadAS.cleanUp();
						cacheReadAS.dispose(); // !!!
					}
					if( cacheWriteAS != null ) {
						cacheWriteAS.cleanUp();
						cacheWriteAS.dispose(); // !!!
						if( !cacheWriteComplete ) { // indicates process was aborted ...
							final File[] f = createCacheFileNames();
							 // ... therefore delete incomplete cache files!
							if( f != null ) {
								for( int i = 0; i < f.length; i++ ) {
									if( !f[i].delete() ) f[i].deleteOnExit();
									// cm.removeFile( f[ i ]);
								}
							}
						}
					}

					if( asyncManager != null ) {
						asyncManager.dispatchEvent( new AsyncEvent( enc_this,
							AsyncEvent.FINISHED, System.currentTimeMillis() ));
					}
					synchronized( threadAsync ) {
						threadAsync.notifyAll();
						// threadAsync = null;
					}
				}
			}
		});

		keepAsyncRunning = true;
		threadAsync.start();
	}

	private static void setProgression( long len, double progWeight )
	throws ProcessingThread.CancelledException
	{
// System.err.println( "dec prog len " + len + ", p " + (float) (len * progWeight) );
		ProcessingThread.update( (float) (len * progWeight) );
	}

	private static void flushProgression()
	{
		ProcessingThread.flushProgression();
	}

	protected void addAllDep(Object source, List stakes, AbstractCompoundEdit ce,
			Span union) throws IOException {
		if (DEBUG)
			System.err.println("addAllDep " + union.toString());

		final DecimatedWaveStake	das;
		final Span				extSpan;
		final long				fullrateStop, fullrateLen; // , insertLen;
		final int				numFullBuf;
		final double			progWeight;
		long					pos;
		long					framesWritten	= 0;
		Span					tag2;
		float					f1;
		int						len;

		synchronized (fileSync) {
			das = alloc(union);
		}
		extSpan			= das.getSpan();
		pos				= extSpan.getStart();
		// insertLen	= extSpan.getLength();
		fullrateStop	= Math.min(extSpan.getStop(), fullScale.editGetSpan(ce).stop);
		fullrateLen		= fullrateStop - extSpan.getStart();
		progWeight		= 1.0 / fullrateLen;
		numFullBuf		= (int) (fullrateLen >> MAXSHIFT);
		pos				= extSpan.getStart();

		synchronized( bufSync ) {
			flushProgression();
			createBuffers();

			for( int i = 0; i < numFullBuf; i++ ) {
				tag2		   = new Span( pos, pos + MAXCOARSE );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE, null, 0 );
				pos			  += MAXCOARSE;
				framesWritten += MAXCOARSE;
				
				setProgression( framesWritten, progWeight );
			}

			len = (int) (fullrateStop - pos);
			if (len > 0) {
				tag2 = new Span( pos, pos + len );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					f1 = tmpBuf[ ch ][ len - 1 ];
					for( int i = len; i < MAXCOARSE; i++ ) {
						tmpBuf[ ch ][ i ] = f1;
					}
				}
				subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE, null, 0 );
				pos			  += MAXCOARSE;
				framesWritten += MAXCOARSE;
				
				setProgression( framesWritten, progWeight );
			}
		} // synchronized( bufSync )

		// editRemove( source, das.getSpan(), ce );
		editClear(source, das.getSpan(), ce);
		// System.err.println( "editRemove "+das.getSpan() );
		editAdd(source, das, ce);
		// System.err.println( "editAdd ..." );
	}

	// public void remove( Object source, Stake stake, CompoundEdit ce )
	// {
	// final DecimatedStake as = (DecimatedStake) stake;
	// System.err.println( "remove" );
	//
	// // ____ dep ____
	// if( dependants != null ) {
	// synchronized( dependants ) {
	// for( int i = 0; i < dependants.size(); i++ ) {
	// ((Trail) dependants.get( i )).remove( source, stake, ce );
	// }
	// }
	// }
	// }

	protected void removeAllDep(Object source, List stakes, CompoundEdit ce,
			Span union) {
		if (DEBUG)
			System.err.println("removeAllDep " + union.toString());
		if (1 == 1)
			throw new IllegalArgumentException("n.y.i.");

		// // ____ dep ____
		// if( dependants != null ) {
		// synchronized( dependants ) {
		// for( int i = 0; i < dependants.size(); i++ ) {
		// ((Trail) dependants.get( i )).removeAllDep( source, stakes, ce, union
		// );
		// }
		// }
		// }
	}

	// ----------- private schnucki -----------

	protected File[] createCacheFileNames()
	{
		final AudioFile[] audioFiles = fullScale.getAudioFiles();
		if( (audioFiles.length == 0) || (audioFiles[0] == null) ) return null;

		final CacheManager cm = PrefCacheManager.getInstance();
		if( !cm.isActive() ) return null;

		final File[] f = new File[ audioFiles.length ];
		for( int i = 0; i < f.length; i++ ) {
			f[i] = cm.createCacheFileName( audioFiles[i].getFile() );
		}
		return f;
	}

	private int[][] createCacheChannelMaps()
	{
		final int[][] fullChanMaps	= fullScale.getChannelMaps();
		final int[][] cacheChanMaps	= new int[ fullChanMaps.length ][];

		for( int i = 0; i < fullChanMaps.length; i++ ) {
//			System.out.println( "fullChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < fullChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + fullChanMaps[ i ][ k ]);
//			}
			cacheChanMaps[ i ] = new int[ fullChanMaps[ i ].length * modelChannels ];
			for( int j = 0; j < cacheChanMaps[ i ].length; j++ ) {
				cacheChanMaps[ i ][ j ] = j;
			}
//			System.out.println( "cacheChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < cacheChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + cacheChanMaps[ i ][ k ]);
//			}
		}

		return cacheChanMaps;
	}

	/*
	 * @returns the cached stake or null if no cache file is available
	 */
	private AudioStake openCacheForRead( int decimModel )
	throws IOException
	{
		final File[]		f			= createCacheFileNames();
		if( f == null ) return null;

		final AudioFile[]	audioFiles	= fullScale.getAudioFiles();
		final Span[]		fileSpans	= new Span[ audioFiles.length ];
		final AudioFile[]	cacheAFs	= new AudioFile[ audioFiles.length ];
		final String		ourCode		= AbstractApplication.getApplication().getMacOSCreator();
		final int[][]		channelMaps	= createCacheChannelMaps();
		AudioStake			result		= null;
		AudioFileDescr		afd;
		byte[]				appCode;
		AudioFileCacheInfo	infoA, infoB;

		try {
			for( int i = 0; i < cacheAFs.length; i++ ) {
// System.out.println( "openCacheForRead checking '" + f[ i ].getAbsolutePath() + "'" );
				
				if( !f[ i ].isFile() ) return null;
				cacheAFs[ i ] = AudioFile.openAsRead( f[ i ]);
				cacheAFs[ i ].readAppCode();
				afd = cacheAFs[ i ].getDescr();
				final long expected = ((audioFiles[ i ].getFrameNum() + MAXCEILADD) & MAXMASK) >> decimHelps[ 0 ].shift;
				// System.out.println( "expected " + expected+ "; cacheF " +
				// cacheAFs[ i ].getFile().getAbsolutePath() );
				if( expected != afd.length ) {
					// System.err.println( "expected numFrames = "+ expected +
					// ", but got " + afd.length );
					return null;
				}
				appCode = (byte[]) afd.getProperty( AudioFileDescr.KEY_APPCODE );
				// System.err.println( "ourCode = '" + ourCode + "'; afd.appCode
				// = '" + afd.appCode + "'; appCode = '" + appCode + "'" );
				if( ourCode.equals( afd.appCode ) && (appCode != null) ) {
					infoA = AudioFileCacheInfo.decode( appCode );
					if( infoA != null ) {
						infoB = new AudioFileCacheInfo( audioFiles[ i ], decimModel, audioFiles[ i ].getFrameNum() );
						if( !infoA.equals( infoB )) {
							// System.err.println( "info mismatch!" );
							return null;
						}
						// System.err.println( "ok. numChans = " +
						// infoA.getNumChannels() );
					} else {
						return null;
					}
				} else {
					return null;
				}
				fileSpans[ i ] = new Span( 0, cacheAFs[ i ].getFrameNum() );
			}
			// XXX WE NEED A WAY TO CLOSE THE FILES UPON STAKE DISPOSAL XXX
			if( channelMaps.length == 1 ) {
				result = new InterleavedAudioStake( fileSpans[ 0 ], cacheAFs[ 0 ], fileSpans[ 0 ]);
			} else {
				result = new MultiMappedAudioStake( fileSpans[ 0 ], cacheAFs, fileSpans, channelMaps );
			}
			return result;
		} finally {
			if( result == null ) {
				for( int i = 0; i < cacheAFs.length; i++ ) {
					if( cacheAFs[ i ] != null ) {
						cacheAFs[ i ].cleanUp();
						// if( !cacheAFs[ i ].getFile().delete() ) {
						// cacheAFs[ i ].getFile().deleteOnExit();
						// }
					}
				}
			}
		}
	}

	private AudioStake openCacheForWrite( int decimModel, long decimFrameNum )
	throws IOException
	{
		final File[]			f			= createCacheFileNames();
		if( f == null ) return null;

		final AudioFile[]		audioFiles	= fullScale.getAudioFiles();
		final AudioFileDescr	afdProto	= new AudioFileDescr();
		final CacheManager		cm			= PrefCacheManager.getInstance();
		final Span[]			fileSpans	= new Span[ audioFiles.length ];
		final AudioFile[]		cacheAFs	= new AudioFile[ audioFiles.length ];
		final String			ourCode		= AbstractApplication.getApplication().getMacOSCreator();
		final int[][]			channelMaps	= createCacheChannelMaps();
		AudioStake				result		= null;
		AudioFileDescr			afd;
		AudioFileCacheInfo		info;

		afdProto.type			= AudioFileDescr.TYPE_AIFF;
		afdProto.bitsPerSample	= 32;
		afdProto.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afdProto.rate			= decimHelps[ 0 ].rate; // getRate();
		afdProto.appCode		= ourCode;

		try {
			for( int i = 0; i < f.length; i++ ) {
				cm.removeFile( f[ i ]); // in case it existed
// System.out.println( "openCacheForWrite doing '" + f[ i ].getAbsolutePath() + "'" );
				afd				= new AudioFileDescr( afdProto );
				afd.channels	= channelMaps[ i ].length;
				// System.out.println( "channels = " + afd.channels );
				afd.file		= f[ i ];
				info			= new AudioFileCacheInfo( audioFiles[ i ], decimModel, audioFiles[ i ].getFrameNum() );
				afd.setProperty( AudioFileDescr.KEY_APPCODE, info.encode() );
				cacheAFs[ i ]	= AudioFile.openAsWrite( afd );
				fileSpans[ i ]	= new Span( 0, decimFrameNum );
			}
			// XXX WE NEED A WAY TO CLOSE THE FILES UPON STAKE DISPOSAL XXX
			if( channelMaps.length == 1 ) {
				result = new InterleavedAudioStake( fileSpans[ 0 ], cacheAFs[ 0 ], fileSpans[ 0 ]);
			} else {
				result = new MultiMappedAudioStake( fileSpans[ 0 ], cacheAFs, fileSpans, channelMaps);
			}
			// System.err.println( "Cache was written" );
			return result;
		} finally {
			if( result == null ) {
				for( int i = 0; i < cacheAFs.length; i++ ) {
					if( cacheAFs[ i ] != null ) {
						cacheAFs[ i ].cleanUp();
						if( !cacheAFs[ i ].getFile().delete() ) {
							cacheAFs[ i ].getFile().deleteOnExit();
						}
					}
				}
			}
		}
	}

	// @synchronization caller must have sync on fileSync !!!
	private DecimatedWaveStake allocAsync(Span span) throws IOException {
		if (!Thread.holdsLock(fileSync))
			throw new IllegalMonitorStateException();

		final long floorStart = span.start & MAXMASK;
		final long ceilStop = (span.stop + MAXCEILADD) & MAXMASK;
		final Span extSpan = (floorStart == span.start)
				&& (ceilStop == span.stop) ? span : new Span(floorStart,
				ceilStop);
		final Span[] fileSpans = new Span[SUBNUM];
		final Span[] biasedSpans = new Span[SUBNUM];
		long fileStart;
		long fileStop;

		if (tempFAsync == null) {
			// XXX THIS IS THE PLACE TO OPEN WAVEFORM CACHE FILE
			tempFAsync = createTempFiles();
		}
		synchronized (tempFAsync) {
			for (int i = 0; i < SUBNUM; i++) {
				fileStart = tempFAsync[i].getFrameNum();
				fileStop = fileStart
						+ (extSpan.getLength() >> decimHelps[i].shift);
				tempFAsync[i].setFrameNum(fileStop);
				fileSpans[i] = new Span(fileStart, fileStop);
				biasedSpans[i] = extSpan;
			}
		}
		return new DecimatedWaveStake(extSpan, tempFAsync, fileSpans, biasedSpans,
				decimHelps);
	}

	// @synchronization caller must have sync on fileSync !!!
	private DecimatedWaveStake alloc(Span span) throws IOException {
		if (!Thread.holdsLock(fileSync))
			throw new IllegalMonitorStateException();

		final long floorStart = span.start & MAXMASK;
		final long ceilStop = (span.stop + MAXCEILADD) & MAXMASK;
		final Span extSpan = (floorStart == span.start)
				&& (ceilStop == span.stop) ? span : new Span(floorStart,
				ceilStop);
		final Span[] fileSpans = new Span[SUBNUM];
		final Span[] biasedSpans = new Span[SUBNUM];
		long fileStart;
		long fileStop;

		if (tempF == null) {
			tempF = createTempFiles(); // XXX sync
		}
		synchronized (tempF) {
			for (int i = 0; i < SUBNUM; i++) {
				fileStart = tempF[i].getFrameNum();
				fileStop = fileStart
						+ (extSpan.getLength() >> decimHelps[i].shift);
				tempF[i].setFrameNum(fileStop);
				fileSpans[i] = new Span(fileStart, fileStop);
				biasedSpans[i] = extSpan;
			}
		}
		return new DecimatedWaveStake(extSpan, tempF, fileSpans, biasedSpans,
				decimHelps);
	}

	// private InterleavedStreamFile[] prepareTempFiles()
	// throws IOException
	// {
	// if( tempF == null ) {
	// tempF = createTempFiles(); // XXX sync
	// } else {
	// tempF.seekFrame( tempF.getFrameNum() );
	// }
	// return tempF;
	// }

	private AudioFile[] createTempFiles() throws IOException {
		// simply use an AIFC file with float format as temp file
		final AudioFileDescr proto = new AudioFileDescr();
		final AudioFile[] tempFiles = new AudioFile[SUBNUM];
		AudioFileDescr afd;
		proto.type = AudioFileDescr.TYPE_AIFF;
		proto.channels = decimChannels;
		proto.bitsPerSample = 32;
		proto.sampleFormat = AudioFileDescr.FORMAT_FLOAT;
		// proto.bitsPerSample = 8;
		// proto.sampleFormat = AudioFileDescr.FORMAT_INT;
		try {
			for (int i = 0; i < SUBNUM; i++) {
				afd = new AudioFileDescr(proto);
				afd.file = IOUtil.createTempFile();
				afd.rate = decimHelps[i].rate;
				tempFiles[i] = AudioFile.openAsWrite(afd);
			}
			return tempFiles;
		} catch (IOException e1) {
			for (int i = 0; i < SUBNUM; i++) {
				if (tempFiles[i] != null)
					tempFiles[i].cleanUp();
			}
			throw e1;
		}
	}

	private void deleteTempFiles( AudioFile[] tempFiles ) {
		for( int i = 0; i < tempFiles.length; i++ ) {
			if( tempFiles[i] != null ) {
				tempFiles[i].cleanUp();
				tempFiles[i].getFile().delete();
			}
		}
	}

	/*
	 * This is invoked by insert(). it subsamples the given buffer for all
	 * subsample STEs and writes it out using continueWrite; therefore the call
	 * to this method should be bracketed with beginInsert() and finishWrite().
	 * len must be an integer muliple of MAXCOARSE !
	 * 
	 * inBuf == null indicates cache skip
	 */
	// private void subsampleWrite( float[][] inBuf, float[][] outBuf,
	// DecimatedStake das, int len )
	protected void subsampleWrite( float[][] inBuf, float[][] outBuf, DecimatedWaveStake das,
								   int len, AudioStake cacheAS, long cacheOff )
	throws IOException
	{
		int decim;

		if( SUBNUM < 1 ) return;

		decim = decimHelps[ 0 ].shift;
		// calculate first decimation from fullrate PCM
		len >>= decim;
		if( inBuf != null ) {
			decimator.decimatePCM( inBuf, outBuf, 0, len, 1 << decim );
			das.continueWrite( 0, outBuf, 0, len );
			if( cacheAS != null ) {
				cacheAS.writeFrames( outBuf, 0, new Span( cacheOff, cacheOff + len ));
			}
		}

		subsampleWrite2( outBuf, das, len );
	}

	// same as subsampleWrite but input is already at first decim stage
	protected void subsampleWrite2( float[][] buf, DecimatedWaveStake das, int len )
	throws IOException
	{
		int decim;

		// calculate remaining decimations from preceding ones
		for( int i = 1; i < SUBNUM; i++ ) {
			decim = decimHelps[ i ].shift - decimHelps[ i - 1 ].shift;
			len >>= decim;
			// framesWritten >>= decim;
			decimator.decimate( buf, buf, 0, len, 1 << decim );
			// ste[i].continueWrite( ts[i], framesWritten, outBuf, 0, len );
			das.continueWrite( i, buf, 0, len );
		} // for( SUBNUM )
	}

	// ---------------------- internal classes and interfaces ----------------------

	public static interface AsyncListener {
		public void asyncFinished( AsyncEvent e );
		public void asyncUpdate( AsyncEvent e );
	}

	public static class AsyncEvent
	extends BasicEvent
	{
		protected static final int UPDATE = 0;
		protected static final int FINISHED = 1;

		protected AsyncEvent( Object source, int id, long when ) {
			super( source, id, when );
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			if( (oldEvent instanceof AsyncEvent) &&
				(this.getSource() == oldEvent.getSource()) &&
				(this.getID() == oldEvent.getID()) ) {

				return true;
			} else
				return false;
		}
	}

	// ---------------------- decimation subclasses ----------------------

	private abstract class Decimator
	{
		protected Decimator() { /* empty */ }
		
		protected abstract void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim );
		protected abstract void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim );
		// protected abstract void decimatePCMFast( float[][] inBuf, float[][]
		// outBuf, int outOff, int len, int decim );
		protected abstract int draw( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
						  			 int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
						  			 Rectangle r, float deltaYN, int off );
		protected abstract int drawLog( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
										int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
										Rectangle r, float deltaYN, int off, float minY, float minInpY );
	}

	private class HalfPeakRMSDecimator
	extends Decimator
	{
		protected HalfPeakRMSDecimator() { /* empty */ }
		
		protected void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
System.out.println( "warning: HalfPeakRMSDecimator : not checked" );

			int		stop, j, k, m, ch, ch2;
			float	f1, f2, f3, f4, f5;
			float[] inBufCh1, outBufCh1;	// pos. peak
			float[] inBufCh2, outBufCh2;	// neg. peak
			float[]	inBufCh3, outBufCh3;	// pos. RMS
			float[]	inBufCh4, outBufCh4;	// neg. RMS

			for (ch = 0; ch < fullChannels; ch++) {
				ch2			= ch << 2;
				inBufCh1	= inBuf[ ch2 ];	// [ch]
				outBufCh1	= outBuf[ ch2 ];	// [ch]
				ch2++;	// ch + fullChannels;
				inBufCh2	= inBuf[ ch2 ];
				outBufCh2	= outBuf[ ch2 ];
				ch2++; // += fullChannels;
				inBufCh3	= inBuf[ ch2 ];
				outBufCh3	= outBuf[ ch2 ];
				ch2++; // += fullChannels;
				inBufCh4	= inBuf[ ch2 ];
				outBufCh4	= outBuf[ ch2 ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f1 = inBufCh1[ k ];
					f2 = inBufCh2[ k ];
					f3 = inBufCh3[ k ];
					f4 = inBufCh4[ k ];
					for (m = k + decim, k++; k < m; k++) {
						f5 = inBufCh1[ k ];
						if (f5 > f1)
							f1 = f5;
						f5 = inBufCh2[ k ];
						if (f5 < f2)
							f2 = f5;
						f3 += inBufCh3[ k ];
						f4 += inBufCh4[ k ];
					}
					outBufCh1[ j ] = f1; // positive halfwave peak
					outBufCh2[ j ] = f2; // negative halfwave peak
					outBufCh3[ j ] = f3 / decim; // positive halfwave mean square
					outBufCh4[ j ] = f4 / decim; // negative halfwave mean square
				}
			} // for( ch )
		}

		// protected void decimatePCMFast( float[][] inBuf, float[][] outBuf,
		// int outOff, int len, int decim )
		// {
		// if( true ) return;
		//		
		// int stop, j, k, m, ch, ch2;
		// float f1, f2, f3, f4, f5;
		// float[] inBufCh1, outBufCh1, outBufCh2, outBufCh3, outBufCh4;
		//
		// for( ch = 0; ch < fullChannels; ch++ ) {
		// inBufCh1 = inBuf[ ch ];
		// outBufCh1 = outBuf[ ch ];
		// ch2 = ch + fullChannels;
		// outBufCh2 = outBuf[ch2];
		// ch2 += fullChannels;
		// outBufCh3 = outBuf[ ch2 ];
		// ch2 += fullChannels;
		// outBufCh4 = outBuf[ ch2 ];
		//
		// for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
		// f5 = inBufCh1[ k++ ];
		// if( f5 >= 0.0f ) {
		// f1 = f5;
		// f3 = f5 * f5;
		// f2 = 0.0f;
		// f4 = 0.0f;
		// } else {
		// f2 = f5;
		// f4 = f5 * f5;
		// f1 = 0.0f;
		// f3 = 0.0f;
		// }
		// for( m = 1; m < decim; m++ ) {
		// f5 = inBufCh1[ k++ ];
		// if( f5 >= 0.0f ) {
		// if( f5 > f1 ) f1 = f5;
		// f3 += f5 * f5;
		// } else {
		// if( f5 < f2 ) f2 = f5;
		// f4 += f5 * f5;
		// }
		// }
		// outBufCh1[ j ] = f1; // positive halfwave peak
		// outBufCh2[ j ] = f2; // negative halfwave peak
		// outBufCh3[ j ] = f3 / decim; // positive halfwave mean square
		// outBufCh4[ j ] = f4 / decim; // negative halfwave mean square
		// }
		// } // for( ch )
		// }

		protected void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			int		stop, j, k, m, ch, ch2;
			float	f1, f2, f3, f4, f5;
			float[] inBufCh1, outBufCh1, outBufCh2, outBufCh3, outBufCh4;

			for( ch = 0; ch < fullChannels; ch++ ) {
				ch2	= ch << 2;
				inBufCh1 = inBuf[ ch2 ];	// [ch]
				outBufCh1 = outBuf[ ch2 ];	// [ch]
				ch2++;	// ch + fullChannels;
				outBufCh2 = outBuf[ ch2 ];
				ch2++;	// += fullChannels;
				outBufCh3 = outBuf[ ch2 ];
				ch2++;	// += fullChannels;
				outBufCh4 = outBuf[ ch2 ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f5 = inBufCh1[ k++ ];
					if( f5 >= 0.0f ) {
						f1 = f5;
						f3 = f5 * f5;
						f2 = 0.0f;
						f4 = 0.0f;
					} else {
						f2 = f5;
						f4 = f5 * f5;
						f1 = 0.0f;
						f3 = 0.0f;
					}
					for( m = 1; m < decim; m++ ) {
						f5 = inBufCh1[ k++ ];
						if( f5 >= 0.0f ) {
							if( f5 > f1 ) f1 = f5;
							f3 += f5 * f5;
						} else {
							if( f5 < f2 ) f2 = f5;
							f4 += f5 * f5;
						}
					}
					outBufCh1[ j ] = f1; // positive halfwave peak
					outBufCh2[ j ] = f2; // negative halfwave peak
					outBufCh3[ j ] = f3 / decim; // positive halfwave mean square
					outBufCh4[ j ] = f4 / decim; // negative halfwave mean square
				}
			} // for( ch )
		}
		
		protected int draw( DecimationInfo info, int ch,
				  			int[][] peakPolyX, int[][] peakPolyY,
				  			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
				  			Rectangle r, float deltaYN, int off )
		{
			float[]		sPeakP, sPeakN, sRMSP, sRMSN;
			float		offX, scaleX, scaleY;
			int			ch2;
			
			ch2		= ch <<= 2;
			sPeakP	= tmpBuf2[ ch2++ ];
			sPeakN	= tmpBuf2[ ch2++ ];
			sRMSP	= tmpBuf2[ ch2++ ];
			sRMSN	= tmpBuf2[ ch2 ];
			
			scaleX	= 4 * r.width / (float) (info.sublength - 1);
			scaleY	= r.height * deltaYN;
			offX	= scaleX * off;
			
			return( drawHalfWavePeakRMS( sPeakP, sPeakN,
									 	 sRMSP, sRMSN, decimLen, peakPolyX[ ch ],
									 	 peakPolyY[ ch ], rmsPolyX[ ch ],
									 	 rmsPolyY[ ch ], off, offX, scaleX,
									 	 scaleY ));
		}

		protected int drawLog( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
 			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
 			Rectangle r, float deltaYN, int off, float minY, float minInpY )
		{
			throw new IllegalStateException( "HalfWavePeakRMS log drawing not yet working" );
		}

		private int drawHalfWavePeakRMS( float[] sPeakP, float[] sPeakN,
				float[] sRMSP, float[] sRMSN, int len, int[] peakPolyX,
				int[] peakPolyY, int[] rmsPolyX, int[] rmsPolyY, int off,
				float offX, float scaleX, float scaleY )
		{
			final float scaleYN = -scaleY;
			int			x;

			for( int i = 0, k = peakPolyX.length - 1 - off; i < len; i++, off++, k-- ) {
				x					= (int) (i * scaleX + offX);
				peakPolyX[ off ]	= x;
				peakPolyX[ k ]		= x;
				rmsPolyX[ off ]		= x;
				rmsPolyX[ k ]		= x;
				peakPolyY[ off ]	= (int) (sPeakP[i] * scaleY);
				peakPolyY[ k ]		= (int) (sPeakN[i] * scaleY);
				rmsPolyY[ off ]		= (int) ((float) Math.sqrt( sRMSP[ i ]) * scaleY );
				rmsPolyY[ k ]		= (int) ((float) Math.sqrt( sRMSN[ i ]) * scaleYN );
			}

			return off;
		}
	} // class HalfPeakRMSDecimator

	private class MedianDecimator
	extends Decimator
	{
		protected MedianDecimator() { /* empty */ }
		
		protected void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			int		stop, j, k, ch;
			float	f1, f2, f3, f4, f5;
			float[] inBufCh1, outBufCh1;

			assert decim == 4 : decim;

			for( ch = 0; ch < fullChannels; ch++ ) {
				inBufCh1	= inBuf[ ch ];
				outBufCh1	= outBuf[ ch ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f1 = inBufCh1[ k++ ];
					f2 = inBufCh1[ k++ ];
					f3 = inBufCh1[ k++ ];
					f4 = inBufCh1[ k++ ];

					// calculate the median of four successive frames
					if( f1 > f2 ) {
						f5 = f1;
						f1 = f2;
						f2 = f5;
					}
					if( f2 > f3 ) {
						if( f1 > f3 ) {
							f5 = f1;
							f1 = f3;
							f3 = f2;
							f2 = f5;
						} else {
							f5 = f2;
							f2 = f3;
							f3 = f5;
						}
					}
					if( f3 > f4 ) {
						if( f2 > f4 ) {
							if( f1 > f4 ) {
								outBufCh1[ j ] = (f1 + f2) / 2;
							} else {
								outBufCh1[ j ] = (f4 + f2) / 2;
							}
						} else {
							outBufCh1[ j ] = (f2 + f4) / 2;
						}
					} else {
						outBufCh1[ j ] = (f2 + f3) / 2;
					}
				}
			} // for( ch )
		}

		protected void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			decimate( inBuf, outBuf, outOff, len, decim );  // same as subsample decimation
		}

		// protected void decimatePCMFast( float[][] inBuf, float[][] outBuf,
		// int outOff, int len, int decim )
		// {
		// decimate( inBuf, outBuf, outOff, len, decim );
		// }

		protected int draw( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
	  			 			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
	  			 			Rectangle r, float deltaYN, int off )
		{
			throw new IllegalStateException( "Median drawing not yet working" );
		}

		protected int drawLog( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
	 			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
	 			Rectangle r, float deltaYN, int off, float minY, float minInpY )
		{
			throw new IllegalStateException( "Median drawing not yet working" );
		}

	} // class MedianDecimator

	private class FullPeakRMSDecimator
	extends Decimator
	{
		protected FullPeakRMSDecimator() { /* empty */ }
		
		protected void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			int 	stop, j, k, m, ch;
			float	f1, f2, f3, f5;
			float[]	inBufCh1, outBufCh1;	// pos. peak
			float[]	inBufCh2, outBufCh2;	// neg. peak
			float[]	inBufCh3, outBufCh3;	// RMS

			for( ch = 0; ch < decimChannels; ) {
				inBufCh1	= inBuf[ ch ];
				outBufCh1	= outBuf[ ch++ ];
				inBufCh2	= inBuf[ ch ];
				outBufCh2	= outBuf[ ch++ ];
				inBufCh3	= inBuf[ ch ];
				outBufCh3	= outBuf[ ch++ ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f1 = inBufCh1[ k ];
					f2 = inBufCh2[ k ];
					f3 = inBufCh3[ k ];
					for( m = k + decim, k++; k < m; k++ ) {
						f5 = inBufCh1[ k ];
						if( f5 > f1 ) f1 = f5;
						f5 = inBufCh2[ k ];
						if( f5 < f2 ) f2 = f5;
						f3 += inBufCh3[ k ];
					}
					outBufCh1[ j ] = f1; // positive halfwave peak
					outBufCh2[ j ] = f2; // negative halfwave peak
					outBufCh3[ j ] = f3 / decim; // fullwave mean square
				}
			} // for( ch )
		}

		protected void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim ) {
			int		stop, j, k, m, ch, ch2;
			float	f1, f2, f3, f4;
			float[] inBufCh1, outBufCh1, outBufCh2, outBufCh3;

			for( ch = 0, ch2 = 0; ch < fullChannels; ) {
				inBufCh1	= inBuf[ ch++ ];
				outBufCh1	= outBuf[ ch2++ ];
				outBufCh2	= outBuf[ ch2++ ];
				outBufCh3	= outBuf[ ch2++ ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f4 = inBufCh1[ k++ ];
					f1 = f4;
					f2 = f4;
					f3 = f4 * f4;
					for( m = 1; m < decim; m++ ) {
						f4 = inBufCh1[ k++ ];
						if( f4 > f1 ) f1 = f4;
						if( f4 < f2 ) f2 = f4;
						f3 += f4 * f4;
					}
					outBufCh1[ j ] = f1; // positive halfwave peak
					outBufCh2[ j ] = f2; // negative halfwave peak
					outBufCh3[ j ] = f3 / decim; // fullwave mean square
				}
			} // for( ch )
		}
		
		protected int draw( DecimationInfo info, int ch,
				  			int[][] peakPolyX, int[][] peakPolyY,
				  			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
				  			Rectangle r, float deltaYN, int off )
		{
			int			ch2;
			float[]		sPeakP, sPeakN, sRMSP;
			float		offX, scaleX, scaleY;
			
			ch2		= ch * 3;
			sPeakP	= tmpBuf2[ ch2++ ];
			sPeakN	= tmpBuf2[ ch2++ ];
			sRMSP	= tmpBuf2[ ch2 ];
			scaleX	= 4 * r.width / (float) (info.sublength - 1);
			scaleY	= r.height * deltaYN;
			offX	= scaleX * off;
			
			return drawFullWavePeakRMS( sPeakP, sPeakN,
										sRMSP, decimLen, peakPolyX[ ch ],
										peakPolyY[ ch ], rmsPolyX[ ch ],
										rmsPolyY[ ch ], off, offX, scaleX,
										scaleY );
		}
		
		private int drawFullWavePeakRMS( float[] sPeakP, float[] sPeakN,
			float[] sRMS, int len, int[] peakPolyX, int[] peakPolyY,
			int[] rmsPolyX, int[] rmsPolyY, int off, float offX, float scaleX,
			float scaleY )
		{
			// final float scaleYN = -scaleY;
			int		x;
			float	peakP, peakN, rms;

			for( int i = 0, k = peakPolyX.length - 1 - off; i < len; i++, off++, k-- ) {
				x					= (int) (i * scaleX + offX);
				peakPolyX[ off ]	= x;
				peakPolyX[ k ]		= x;
				rmsPolyX[ off ]		= x;
				rmsPolyX[ k ]		= x;
				peakP				= sPeakP[ i ];
				peakN				= sPeakN[ i ];
				peakPolyY[ off ]	= (int) (peakP * scaleY) + 2;
				peakPolyY[ k ]		= (int) (peakN * scaleY) - 2;
				// peakC = (peakP + peakN) / 2;
				rms					= (float) Math.sqrt( sRMS[ i ]); // / 2;
				rmsPolyY[ off ]		= (int) (Math.min( peakP, rms ) * scaleY);
				rmsPolyY[ k ]		= (int) (Math.max( peakN, -rms ) * scaleY);
			}

			return off;
		}

		protected int drawLog( DecimationInfo info, int ch,
  			int[][] peakPolyX, int[][] peakPolyY,
  			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
  			Rectangle r, float deltaYN, int off, float minY, float minInpY )
		{
			int			ch2;
			float[]		sPeakP, sPeakN, sRMSP;
			float		offX, scaleX, scaleY;

			ch2		= ch * 3;
			sPeakP	= tmpBuf2[ ch2++ ];
			sPeakN	= tmpBuf2[ ch2++ ];
			sRMSP	= tmpBuf2[ ch2 ];
			scaleX	= 4 * r.width / (float) (info.sublength - 1);
			scaleY	= r.height * deltaYN;
			offX	= scaleX * off;

			return drawFullWavePeakRMSLog( sPeakP, sPeakN,
			                               sRMSP, decimLen, peakPolyX[ ch ],
			                               peakPolyY[ ch ], rmsPolyX[ ch ],
			                               rmsPolyY[ ch ], off, offX, scaleX,
			                               scaleY, minY, minInpY );
		}


		private int drawFullWavePeakRMSLog( float[] sPeakP, float[] sPeakN,
				float[] sRMS, int len, int[] peakPolyX, int[] peakPolyY,
				int[] rmsPolyX, int[] rmsPolyY, int off, float offX, float scaleX,
				float scaleY, float minY, float minInpY )
		{
			// final float scaleYN = -scaleY;
			final int	minYPix		= (int) (minY * scaleY - 2);
			final float minInpYSqr	= minInpY * minInpY;
			int			x;
//			int			botOff;
			float		peak, rms;

//			for( int k = peakPolyX.length >> 1; k < peakPolyX.length; k++ ) {
//				peakPolyY[ k ]		= minYPix;
//				rmsPolyY[ k ]		= minYPix;
//				peakPolyX[ k ]		= x;
//				rmsPolyX[ k ]		= minYPix;
//			}
			
//			botOff					= peakPolyX.length - 1;
//			x						= (int) offX;
//			peakPolyX[ botOff ]		= x;
//			peakPolyY[ botOff ]		= minYPix;
//			rmsPolyX[ botOff ]		= x;
//			rmsPolyY[ botOff ]		= minYPix;
//			botOff--;
//			x						= (int) ((len - 1) * scaleX + offX);
//			peakPolyX[ botOff ]		= x;
//			peakPolyY[ botOff ]		= minYPix;
//			rmsPolyX[ botOff ]		= x;
//			rmsPolyY[ botOff ]		= minYPix;

			for( int i = 0, k = peakPolyX.length - 1 - off; i < len; i++, off++, k-- ) {
				x					= (int) (i * scaleX + offX);
				peakPolyX[ off ]	= x;
				peakPolyX[ k ]		= x;
				rmsPolyX[ off ]		= x;
				rmsPolyX[ k ]		= x;
				peak				= Math.max( Math.abs( sPeakP[ i ]), Math.abs( sPeakN[ i ]));
				if( peak > minInpY ) {
					peak			= (float) (Math.log( peak ) * TWENTYBYLOG10);
				} else {
					peak			= minY;
				}
				peakPolyY[ off ]	= (int) (peak * scaleY) + 2;
				peakPolyY[ k ]		= minYPix;
				rms					= sRMS[ i ];
				if( rms > minInpYSqr ) {
					rms				= (float) (Math.log( rms ) * TENBYLOG10);
				} else {
					rms				= minY;
				}
				rmsPolyY[ off ]		= (int) (Math.min( peak, rms ) * scaleY);
				rmsPolyY[ k ]		= minYPix;
			}

			return off;
		}
	} // class FullPeakRMSDecimator
}