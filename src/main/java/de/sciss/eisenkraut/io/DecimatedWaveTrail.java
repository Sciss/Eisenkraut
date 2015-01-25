/*
 *  DecimatedWaveTrail.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.List;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.eisenkraut.gui.WaveformView;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileCacheInfo;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.CacheManager;
import de.sciss.io.Span;
import de.sciss.timebased.Stake;
import de.sciss.util.MutableInt;

/**
 *	TODO: common superclass of AudioTrail and DecimatedTrail
 *	TODO: drawWaveform : the initial idea was that readFrames should be removed ;
 *      	instead of filling "missing" samples, the polygon creation should use
 *       	biased x position. also for coherency, drawPCM should use a Polygon not
 *       	GeneralPath
 */
public class DecimatedWaveTrail
extends DecimatedTrail
{
	private static final int		UPDATE_PERIOD			= 2000; // millisecs in async overview calculation

	private final Decimator			decimator;

	// waveform drawing
	// private static final Stroke strkLine = new BasicStroke( 0.5f );
	private static final Stroke		strkLine				= new BasicStroke(2.0f);

	private static final Paint		pntLine					= Color.black;

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
	 */
	public void drawWaveform(DecimationInfo info, WaveformView view, Graphics2D g2) {
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
		final boolean			logAmp			= view.getVerticalScale() == PrefsUtil.VSCALE_AMP_LOG;

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
			
			if( logAmp ) {
				maxY	= view.getAmpLogMax();
				minY	= view.getAmpLogMin();
				minInpY = (float) Math.exp( minY / TWENTYBYLOG10 );
			} else {
				maxY	= view.getAmpLinMax();
				minY	= view.getAmpLinMin();
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
//					chunkSpan	= new Span( start, start + fullLen );

					if( fromPCM ) {
						fullStop = fullScale.getSpan().stop;
						chunkSpan = new Span( start, Math.min( fullStop, start + fullLen ));
						fullScale.readFrames( tmpBuf, 0, chunkSpan );
						final long chunkStop = chunkSpan.getLength();
						if( (chunkStop < fullLen) && (chunkStop > 0) ) {
							// duplicate last frames
							for( int i = (int) chunkStop, j = i - 1; i < (int) fullLen; i++ ) {
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
						if( logAmp ) {
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
						if( logAmp ) {
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
					if( !drawBusyList.isEmpty() ) {
						// g2.setColor( Color.red );
						g2.setPaint( pntBusy );
						for (Span aDrawBusyList : drawBusyList) {
							chunkSpan = aDrawBusyList;
							scaleX = r.width / (float) info.getTotalLength(); // (info.sublength - 1);
							g2.fillRect((int) ((chunkSpan.start - info.span.start) * scaleX) + r.x, r.y,
									(int) (chunkSpan.getLength() * scaleX), r.height);
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
			System.err.println("draw waveform:");
			e1.printStackTrace();
		}
	}


	/**
	 * Determines which sub-sampled version is suitable for a given display range
	 * (the most RAM and CPU economic while maintaining optimal display resolution).
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
	 *         full-rate version is used).
	 */
	public DecimationInfo getBestSubsample( Span tag, int minLen )
	{
		final DecimationInfo	info;
		final boolean			fromPCM, toPCM;
		final long				fullLength	= tag.getLength();
		long					subLength, n;
		int						idx, inlineDecim;

		subLength = fullLength;
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
	 * Reads a block of sub-sampled frames.
	 * 
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
			final DecimatedStake	ds	= (DecimatedStake) editGetLeftMost( idx, true, null );
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
	private void readFrames(int sub, float[][] data, int dataOffset, List<Span> busyList,
							Span readSpan, AbstractCompoundEdit ce)
			throws IOException {

		int					idx			= editIndexOf(readSpan.start, true, ce);
		if( idx < 0 ) idx = -(idx + 2);
		final long			startR		= decimHelps[sub].roundAdd - readSpan.start;
		final List			coll		= editGetCollByStart( ce );
		final MutableInt	readyLen	= new MutableInt( 0 );
		final MutableInt	busyLen		= new MutableInt( 0 );
		DecimatedStake	stake;
		int					chunkLen, discrepancy;
		Span				subSpan;
		int					readOffset, nextOffset = dataOffset;
		int					len			= (int) (readSpan.getLength() >> decimHelps[ sub ].shift);

		while( (len > 0) && (idx < coll.size()) ) {
			stake		= (DecimatedStake) coll.get( idx );
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
				busyList.add(new Span(subSpan.stop - (subSpan.getLength() * busyLen.value() / chunkLen),
						subSpan.stop));
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
			((DecimatedStake) get( i, true )).debugDump();
		}
	}

	// ----------- dependant implementation -----------

//	public void dispose()
//	{
//		super.dispose();
//	}

	// private void addAllDepAsync( Object source, List stakes, SyncCompoundEdit
	// ce, Span union )
	private void addAllDepAsync()
	throws IOException
	{
		if( threadAsync != null ) throw new IllegalStateException();

		final List					stakes		= fullScale.getAll(true);
		if( stakes.isEmpty() ) return;

		final DecimatedStake		das;
		final Span					union		= fullScale.getSpan();
		final Span					extSpan;
		final long					fullrateStop, fullrateLen; // , insertLen;
		final int					numFullBuf;
		// final CacheManager cm = CacheManager.getInstance();
		final AbstractCompoundEdit	ce			= null; // XXX
		final Object				source		= null; // XXX
		final AudioStake			cacheReadAS;
		final AudioStake			cacheWriteAS;

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
final int pri = Thread.currentThread().getPriority();
//System.out.println( "pri was " + pri );
Thread.currentThread().setPriority( pri - 2 );
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
							nextTime = time + UPDATE_PERIOD;
							if( asyncManager != null ) {
								asyncManager.dispatchEvent( new AsyncEvent(
										DecimatedWaveTrail.this, AsyncEvent.UPDATE, time, DecimatedWaveTrail.this ));
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
								// pos += MAXCOARSE;
								// framesWrittenCache += minCoarse;
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
								for (File aF : f) {
									if (!aF.delete()) aF.deleteOnExit();
								}
							}
						}
					}

					if( asyncManager != null ) {
						asyncManager.dispatchEvent( new AsyncEvent( DecimatedWaveTrail.this,
							AsyncEvent.FINISHED, System.currentTimeMillis(), DecimatedWaveTrail.this ));
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

	protected void addAllDep( Object source, List<Stake> stakes, AbstractCompoundEdit ce, Span union )
	throws IOException
	{
		if( DEBUG ) System.err.println( "addAllDep " + union.toString() );

		final DecimatedStake das;
		final Span extSpan;
		final long fullrateStop, fullrateLen; // , insertLen;
		final int numFullBuf;
		final double progWeight;
		long pos;
		long framesWritten = 0;
		Span tag2;
		float f1;
		int len;

		synchronized( fileSync ) {
			das = alloc( union );
		}
		extSpan = das.getSpan();
		pos = extSpan.start;
		// insertLen = extSpan.getLength();
		fullrateStop = Math.min( extSpan.stop, fullScale.editGetSpan( ce ).stop );
		fullrateLen = fullrateStop - extSpan.start;
		progWeight = 1.0 / fullrateLen;
		numFullBuf = (int) (fullrateLen >> MAXSHIFT);

		synchronized( bufSync ) {
			flushProgression();
			createBuffers();

			for( int i = 0; i < numFullBuf; i++ ) {
				tag2 = new Span( pos, pos + MAXCOARSE );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE, null, 0 );
				pos += MAXCOARSE;
				framesWritten += MAXCOARSE;

				setProgression( framesWritten, progWeight );
			}

			len = (int) (fullrateStop - pos);
			if( len > 0 ) {
				tag2 = new Span( pos, pos + len );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					f1 = tmpBuf[ ch ][ len - 1 ];
					for( int i = len; i < MAXCOARSE; i++ ) {
						tmpBuf[ ch ][ i ] = f1;
					}
				}
				subsampleWrite( tmpBuf, tmpBuf2, das, MAXCOARSE, null, 0 );
				// pos += MAXCOARSE;
				framesWritten += MAXCOARSE;

				setProgression( framesWritten, progWeight );
			}
		} // synchronized( bufSync )

		editClear( source, das.getSpan(), ce );
		editAdd( source, das, ce );
	}

	// ----------- private -----------

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
				for (AudioFile cacheAF : cacheAFs) {
					if (cacheAF != null) {
						cacheAF.cleanUp();
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
			if (result == null) {
				for (AudioFile cacheAF : cacheAFs) {
					if (cacheAF != null) {
						cacheAF.cleanUp();
						if (!cacheAF.getFile().delete()) {
							cacheAF.getFile().deleteOnExit();
						}
					}
				}
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
	protected void subsampleWrite( float[][] inBuf, float[][] outBuf, DecimatedStake das,
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
	protected void subsampleWrite2( float[][] buf, DecimatedStake das, int len )
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