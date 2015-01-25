/*
 *  Needlehole.java
 *  FScape
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		06-Feb-05	added standard deviation + minimum filter + improved speed
 *		17-Mar-05	added center clipping
 *		18-Sep-05	refurbished as processing plug-in
 */

package de.sciss.fscape.render;

import java.awt.BorderLayout;
import java.beans.XMLDecoder;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JPanel;

import de.sciss.eisenkraut.math.Fourier;
import de.sciss.eisenkraut.math.MathUtil;
import de.sciss.eisenkraut.render.AbstractRenderPlugIn;
import de.sciss.eisenkraut.render.RandomAccessRequester;
import de.sciss.eisenkraut.render.RenderConsumer;
import de.sciss.eisenkraut.render.RenderContext;
import de.sciss.eisenkraut.render.RenderHost;
import de.sciss.eisenkraut.render.RenderSource;
import de.sciss.fscape.util.Filter;
import de.sciss.gui.GUIUtil;
import de.sciss.io.AudioFile;
import de.sciss.io.Span;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *	Processing module for moving window
 *	based filtering of a sound.
 */
public class Needlehole
		extends AbstractRenderPlugIn
		implements RandomAccessRequester {

	private static final String KEY_GAINTYPE		= "gaintype";
	private static final String KEY_GAIN			= "gain";

	private static final String KEY_FILTER			= "filter";
	private static final String KEY_LENGTH			= "length";
	private static final String KEY_THRESH			= "thresh";
	private static final String KEY_SUBDRY			= "subdry";

//	private static final String GAIN_ABSOLUTE		= "abs";
	private static final String GAIN_NORMALIZED		= "norm";

	private static final String FILTER_MEDIAN		= "median";
	private static final String FILTER_STDDEV		= "stddev";
	private static final String FILTER_MINIMUM		= "minimum";
	private static final String FILTER_CENTER		= "center";
	private static final String FILTER_MINIMUM2		= "min2";
	private static final String FILTER_AUTOCORR		= "autocorr";
	private static final String FILTER_AUTOCORR2	= "autocorr2";
	private static final String FILTER_MINPHASE		= "minphase";
	
//	private static final ParamSpace	spcSmps			= new ParamSpace( 1, Double.POSITIVE_INFINITY, 1.0, 0, 0, 1, ParamSpace.TIME | ParamSpace.SMPS );
	private static final ParamSpace	spcSmps			= new ParamSpace( 2, Double.POSITIVE_INFINITY, 2, 0, 0, 2, ParamSpace.TIME | ParamSpace.SMPS );
	private static final ParamSpace	spcAmp			= new ParamSpace( 0.0, Double.POSITIVE_INFINITY, 0.0, 0, 0, 1.0, ParamSpace.AMP | ParamSpace.REL );
	
	private static final Param paraDefLength		= new Param(  1, ParamSpace.TIME | ParamSpace.SECS | ParamSpace.MILLI );
	private static final Param paraDefThresh		= new Param( -3, ParamSpace.AMP | ParamSpace.REL | ParamSpace.DECIBEL );
	private static final Param paraDefGain			= new Param(  0, ParamSpace.AMP | ParamSpace.REL | ParamSpace.DECIBEL );
	
	private DefaultUnitTranslator ut;

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
		try {
			final XMLDecoder			d	= new XMLDecoder( this.getClass().getClassLoader().getResourceAsStream(
													"NeedleholeGUI.xml" ));
			final Object				o	= d.readObject();
			final JComponent			jc	= (JComponent) o;
			final JPanel				p	= new JPanel( new BorderLayout( 8, 0 ));
			d.close();
			
			ut = (DefaultUnitTranslator) jc.getClientProperty( "de.sciss.gui.UT" );
			if( ut != null ) ut.setLengthAndRate( context.getTimeSpan().getLength(), context.getSourceRate() );
			GUIUtil.setPreferences( jc, prefs );
			
			p.add( new de.sciss.fscape.gui.SideBarLogo(), BorderLayout.WEST );
			p.add( jc, BorderLayout.CENTER );
			return p;
		}
		catch( NullPointerException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
			return null;
		}
	}

	public String getName()
	{
		return "Needlehole Cherry Blossom";
	}

	private RunningWindowFilter		prFilter;
	private boolean					prSubDry;
	private boolean					prNormalize;
	private float					prGain;
	private float[][]				prInBuf;
	private int						prInBufSize;
	private float					prMaxAmp;
	private int						prOutBufSize;
	private int						prOffStart;
	private int						prProcLen;
//	private long					prFramesRead;
	private long					prFramesWritten;
	private long					prRenderLength;
	private int						prWinSize;
//	private int						prWinSizeH;
	private AudioFile prTempFile;
	private Span					prNextSpan;
	private float					prProgWeight;
	private RenderConsumer			prConsumer;
	private RenderHost				prHost;
	private Span					prTotalSpan;

	public boolean producerBegin( RenderSource source )
	throws IOException
	{
		prTotalSpan		= source.context.getTimeSpan();

		if( ut == null ) {
			ut = new DefaultUnitTranslator();
			ut.setLengthAndRate( prTotalSpan.getLength(), source.context.getSourceRate() );
		}
	
		final String	filterType		= prefs.get( KEY_FILTER, FILTER_MEDIAN );
		final double	threshAmp		= ut.translate( Param.fromPrefs( prefs, KEY_THRESH, paraDefThresh ), spcAmp ).val;
		final Integer	outBufSizeI;

		prSubDry		= prefs.getBoolean( KEY_SUBDRY, false );
		prNormalize		= prefs.get( KEY_GAINTYPE, "" ).equals( GAIN_NORMALIZED );
		prConsumer		= source.context.getConsumer();
		prHost			= source.context.getHost();

		prWinSize		= Math.max( 2, (int) ut.translate( Param.fromPrefs( prefs, KEY_LENGTH, paraDefLength ), spcSmps ).val );
		prOutBufSize	= Math.max( 8192, prWinSize );
		outBufSizeI		= prOutBufSize;
		prInBufSize		= prOutBufSize + prWinSize;
//		prWinSizeH		= prWinSize >> 1;

		prInBuf			= new float[ source.numAudioChannels ][ prInBufSize ];

//System.err.println( "winsizeH "+prWinSizeH+"; winsize "+prWinSize+"; prInBufSize "+prInBufSize+"; prOutBufSize "+prOutBufSize );

		// cannot have other buffer sizes than this
		source.context.setOption( RenderContext.KEY_MINBLOCKSIZE, outBufSizeI );
		source.context.setOption( RenderContext.KEY_PREFBLOCKSIZE, outBufSizeI );
		source.context.setOption( RenderContext.KEY_MAXBLOCKSIZE, outBufSizeI );

		if( filterType.equals( FILTER_MEDIAN )) {
			prFilter	= new MedianFilter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_STDDEV )) {
			prFilter	= new StdDevFilter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_MINIMUM )) {
			prFilter	= new MinimumFilter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_CENTER )) {
			prFilter	= new CenterClippingFilter( prWinSize, source.numAudioChannels, threshAmp );
		} else if( filterType.equals( FILTER_MINIMUM2 )) {
			prFilter	= new Minimum2Filter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_AUTOCORR )) {
			prFilter	= new AutoCorrelationFilter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_AUTOCORR2 )) {
			prFilter	= new AutoCorrelation2Filter( prWinSize, source.numAudioChannels );
		} else if( filterType.equals( FILTER_MINPHASE )) {
			prFilter	= new MinimumPhaseFilter( prWinSize, source.numAudioChannels );
		} else {
			throw new IOException( "Unknown filter type : " + filterType );
		}
		
		prOffStart		= prFilter.getStartOffset(); // prWinSizeH;
//		prProcLen		= prOutBufSize - prWinSizeH;
		prProcLen		= prOutBufSize - prOffStart;
		
		prRenderLength	= prTotalSpan.getLength();
//		prFramesRead	= 0;
		prFramesWritten	= 0;
		prGain			= (float) ut.translate( Param.fromPrefs( prefs, KEY_GAIN, paraDefGain ), spcAmp ).val;

		if( prNormalize ) {
			prMaxAmp	= 0.0f;
			source.context.setOption( RenderContext.KEY_RANDOMACCESS, this );
			prNextSpan	= new Span( prTotalSpan.start, Math.min( prTotalSpan.start + prOutBufSize,
									prTotalSpan.stop ));
//System.err.println( "initial span "+prNextSpan.start + " ... " +prNextSpan.stop );
			prTempFile	= createTempFile( source.numAudioChannels, source.context.getSourceRate() );
			prProgWeight= 1.0f / Math.max( 1, prRenderLength ) * prWinSize / (prWinSize + 1);
			return true;
		} else {
			prTempFile	= null;
			return prConsumer.consumerBegin( source );
		}
	}
	
	public boolean producerRender( RenderSource source )
	throws IOException
	{
		render( source );
		// in normalize mode write to temp file
		if( prNormalize ) {
			float f1;
		
			for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
				for( int i = source.audioBlockBufOff, j = i + source.audioBlockBufLen; i < j; i++ ) {
					f1 = Math.abs( source.audioBlockBuf[ ch ][ i ]);
					if( f1 > prMaxAmp ) {
						prMaxAmp = f1;
					}
				}
			}
			
			prTempFile.writeFrames( source.audioBlockBuf, source.audioBlockBufOff, source.audioBlockBufLen );
			
			prNextSpan = new Span( prNextSpan.stop, Math.min( prTotalSpan.stop,
								   prNextSpan.stop + prOutBufSize ));
//System.err.println( "next span "+prNextSpan.start + " ... " +prNextSpan.stop );

			prHost.setProgression( prFramesWritten * prProgWeight );

			// everything's rendered, now active the consumer
			if( prFramesWritten >= prRenderLength ) {
				assert prNextSpan.isEmpty();
				if( prMaxAmp > 0f ) prGain /= prMaxAmp;
				return prConsumer.consumerBegin( source );
			} else {
				return true;
			}
		// in immedate mode, directly feed consumer
		} else {
			if( prGain != 1.0f ) {
				multiply( source.audioBlockBuf, source.audioBlockBufOff, source.audioBlockBufLen, prGain );
			}
			return prConsumer.consumerRender( source );
		}
	}

	public boolean producerFinish(RenderSource source)
			throws IOException {
		try {
			if (prNormalize) {
				if (!normalize(source)) return false;
			}
			return prConsumer.consumerFinish(source);
		} finally {
			if (prTempFile != null) {
				deleteTempFile(prTempFile);
				prTempFile = null;
			}
		}
	}

	private boolean normalize(RenderSource source )
	throws IOException
	{
		long writeOffset = source.context.getTimeSpan().start;
		int transLen;

		prFramesWritten = 0; // reset
		prTempFile.seekFrame( 0 );
		
//		System.out.println( "prRenderLength " + prRenderLength + "; prGain " + prGain );
	
		while( prFramesWritten < prRenderLength ) {
			transLen			= (int) Math.min( prOutBufSize, prRenderLength - prFramesWritten );
			source.audioBlockBufOff	= 0;
			source.audioBlockBufLen	= transLen;
			source.blockSpan	= new Span( writeOffset, writeOffset + transLen );

			prTempFile.readFrames( source.audioBlockBuf, source.audioBlockBufOff, source.audioBlockBufLen );
			
//			float test = 0f;
//			for( int i = 0; i < source.audioBlockBufLen; i++ ) {
//				for( int j = 0; j < source.audioBlockBuf.length; j++ ) {
//					test = Math.max( test, Math.abs( source.audioBlockBuf[ j ][ i ]));
//				}
//			}
//			System.out.println( "after " + (prFramesWritten+transLen) + " maxAmp is " + test );

			if( prGain != 1.0f ) {
				multiply( source.audioBlockBuf, source.audioBlockBufOff, source.audioBlockBufLen, prGain );
			}

//			float test = 0f;
//			for( int i = 0; i < source.audioBlockBufLen; i++ ) {
//				for( int j = 0; j < source.audioBlockBuf.length; j++ ) {
//					test = Math.max( test, Math.abs( source.audioBlockBuf[ j ][ i ]));
//				}
//			}
//			System.out.println( "after " + (prFramesWritten+transLen) + " maxAmp is " + test );

			prFramesWritten += transLen;
			writeOffset += transLen;

			if( !prConsumer.consumerRender( source )) return false;
		}
		return true;
	}

	private void render( RenderSource source )
	throws IOException
	{
		final int transLen	= (int) Math.min( prProcLen, prRenderLength - prFramesWritten );

//System.err.println( "received "+source.audioBlockBufLen+" input samples. copied to ("+prOffStart+" ... "+(prOffStart+source.audioBlockBufLen)+")" );
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			System.arraycopy( source.audioBlockBuf[ ch ], source.audioBlockBufOff, prInBuf[ ch ], prOffStart,
							  source.audioBlockBufLen );
		}

		// zero-padding last chunk
		if( prOffStart + source.audioBlockBufLen < prInBufSize ) {
//System.err.println( "zero padding "+(prInBufSize - prOffStart - source.audioBlockBufLen)+" samples in ("+(prOffStart + source.audioBlockBufLen) + " ... " + prInBufSize + ")" );
			fill( prInBuf, prOffStart + source.audioBlockBufLen, prInBufSize - prOffStart - source.audioBlockBufLen, 0f );
		}
		
//System.err.println( "processing "+transLen+" samples in (0 ... " + transLen+")" );
		prFilter.process( prInBuf, source.audioBlockBuf, 0, source.audioBlockBufOff, transLen );
		
		if( prSubDry) {
			subtract( source.audioBlockBuf, source.audioBlockBufOff, prInBuf, prOffStart, transLen );
		}

		// shift buffers
//System.err.println( "copy "+prWinSize+" overlap samples from (" + transLen+ " ... " + (transLen + prWinSize)+") to (0 ... " + prWinSize + ")" );
		for( int ch = 0; ch < source.numAudioChannels; ch++ ) {
			System.arraycopy( prInBuf[ ch ], transLen, prInBuf[ ch ], 0, prWinSize );
		}

		prProcLen		 = prOutBufSize;
		prOffStart		 = prWinSize;
		prFramesWritten += transLen;

		source.audioBlockBufLen = transLen;
	}

	// ---------- RandomAccessRequester interface ----------
	
	public Span getNextSpan()
	{
		return prNextSpan;
	}

// -------- Window Filter --------

	private static abstract class RunningWindowFilter
	{
		protected final int winSize;
		
		protected RunningWindowFilter( int winSize )
		{
			this.winSize = winSize;
		}
		
		public abstract void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len ) throws IOException;
		
		public int getStartOffset()
		{
			return 0; // winSize >> 1;
		}
		
//		public int getOutputSkip()
//		{
//			return 0;
//		}
	}
	
	private static class StdDevFilter
	extends RunningWindowFilter
	{
		private final int			channels;
		private final double[][]	dcMem;
		private final int			winSizeM1;
	
		protected StdDevFilter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM1		= winSize - 1;
			
			dcMem		= new double[channels][2];
		}
		
		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, i, j, k, m, n;
			float[]		convBuf2, convBuf3;
			double[]	convBuf4;
			double		d1, d2, mu, mus, omus, sum;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf4	= dcMem[ ch ];
				convBuf2	= inBuf[ch];
				convBuf3	= outBuf[ch];
				// calc first full window sum
				mus			= 0.0;
				for( i = 0, m = inOff; i < winSizeM1; i++, m++ ) {
					mus	   += convBuf2[ m ];	// sum all but last one in window
				}
				omus		= 0.0;
				for( j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					// shift by one : remove obsolete sample
					// and add new last window sample
					mus		= mus - omus + convBuf2[ m + winSizeM1 ];
					mu		= mus / winSize;	// mean now
					sum		= 0.0;
					for( i = 0, k = m; i < winSize; i++, k++ ) {
						d1		= convBuf2[ k ] - mu;
						sum	   += d1 * d1;	// variance
					}
					d1				= Math.sqrt( sum );	// standard deviation
				// ---- remove DC ----
					d2				= d1 - convBuf4[ 0 ] + 0.99 * convBuf4[ 1 ];
					convBuf3[ n ]	= (float) d2;
					convBuf4[ 0 ]	= d1;
					convBuf4[ 1 ]	= d2;
					omus			= convBuf2[ m ];
				}
			} // for channels
		} // process
	} // class StdDevFilter

	/*
	 *	Verfolgt das betragsmaessige Minimum
	 */
	private static class MinimumFilter
	extends RunningWindowFilter
	{
		private final int			channels;
		private final int			winSizeM1;
	
		protected MinimumFilter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM1		= winSize - 1;
		}

		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, i, j, k, m, n, minidx;
			float[]		convBuf2, convBuf3;
			float		f1, f2, min;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf2	= inBuf[ch];
				convBuf3	= outBuf[ch];
				minidx		= -1;
				min			= 0.0f;
				for( j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					if( minidx < m ) {	// need to find again
						f1		= Math.abs( convBuf2[ m ]);
						minidx	= m;
						for( i = 1, k = m + 1; i < winSize; i++, k++ ) {
							f2	= Math.abs( convBuf2[ k ]);
							if( f2 < f1 ) {
								f1		= f2;
								minidx	= k;
							}
						}
						min	= convBuf2[ minidx ];
					} else {
						f1	= convBuf2[ m + winSizeM1 ];
						if( Math.abs( f1 ) < Math.abs( min )) {
							min		= f1;
							minidx	= m + winSizeM1;
						}
					}
					convBuf3[ n ]	= min;
					minidx--;
				}
			} // for channels
		} // process
	} // class MinimumFilter

	// this is about 20 times faster than the original code
	private static class MedianFilter
	extends RunningWindowFilter
	{
		private final int		winSizeH;
		private final int		winSizeM1;
		private final int		channels;
		
		private final float[][]	medianBuf;
		private final float[][]	histoBuf;
		
		private int				histoIdx	= 0;
		private int				medianNum	= 0;
	
		protected MedianFilter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeH		= winSize >> 1;
			winSizeM1		= winSize - 1;
			
			medianBuf		= new float[ channels ][ winSize ];
			histoBuf		= new float[ channels ][ winSize ];
		}

//		public int getStartOffset()
//		{
//			return 0;
//		}

		// we need access to low and high, that's why we cannot
		// use Arrays.binarySearch directly ;-/
	    private int binarySearch( float[] a, float key, int low,int high )
	    {
	    	while( low <= high ) {
	    	    final int	mid		= (low + high) >> 1;
	    	    final float	midVal	= a[ mid ];
	    	    
	    	    if( midVal < key ) {
	    	    	low = mid + 1;
	    	    } else if( midVal > key ) {
	    	    	high = mid - 1;
	    	    } else {
	    	    	return mid;
	    	    }
	    	}
	    	return -(low + 1);  // key not found.
	    }
	    
		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
//System.out.println( "inOff = " + inOff + "; outOff = " + outOff + "; len = " + len );
			
			for( int ch = 0; ch < channels; ch++ ) {
				final float[]	medianChanBuf	= medianBuf[ ch ];
				final float[]	inChanBuf		= inBuf[ ch ];
				final float[]	outChanBuf		= outBuf[ ch ];
				final float[]	histoChanBuf	= histoBuf[ ch ];
				int				medianChanNum	= medianNum;
				int				histoChanIdx	= histoIdx;
				            
//int lastCpySrcIdx = -666, lastCpyDstIdx = -666, lastCpyLen = -666;
//boolean didLastCpy = false;
//int lastOldIdx = -666, lastNewIdx = -666;
//float lastOldVal = -666;
//float[] backup = new float[ winSize ];

				for( int i = inOff, j = outOff, k = inOff + len; i < k; i++, j++ ) {
					final float val = inChanBuf[ i ];
//System.arraycopy( medianChanBuf, 0, backup, 0, winSize );
					if( medianChanNum == winSize ) { // full buffer size reached
						if( histoChanIdx == winSize ) histoChanIdx = 0;
//lastOldVal = histoChanBuf[ histoChanIdx ];
						final int oldIdx = binarySearch( medianChanBuf, histoChanBuf[ histoChanIdx ], 0, winSizeM1 );
						// trick to kind of ignore the removed value
//						medianChanBuf[ oldIdx ] = medianChanBuf[ oldIdx > 0 ? oldIdx - 1 : oldIdx + 1 ];
						int newIdx = binarySearch( medianChanBuf, val, 0, winSizeM1 );
						if( newIdx < 0 ) newIdx = -(newIdx + 1);
//try {
						if( newIdx < oldIdx ) {
//lastCpySrcIdx=newIdx; lastCpyDstIdx=newIdx+1;lastCpyLen=oldIdx-newIdx; didLastCpy = true;
							System.arraycopy( medianChanBuf, newIdx, medianChanBuf, newIdx + 1, oldIdx - newIdx );
						} else if( newIdx > oldIdx ) {
							newIdx--;
//lastCpySrcIdx=oldIdx+1; lastCpyDstIdx=oldIdx;lastCpyLen=newIdx-oldIdx; didLastCpy = true;
							System.arraycopy( medianChanBuf, oldIdx + 1, medianChanBuf, oldIdx, newIdx - oldIdx );
						}
//						else didLastCpy = false; 
						medianChanBuf[ newIdx ] = val;
						
//lastOldIdx = oldIdx; lastNewIdx = newIdx;
							
//}
//catch( ArrayIndexOutOfBoundsException e1 ) {
//	System.out.println( "ch = " + ch + "; oldIdx = " + oldIdx + "; newIdx = " + newIdx + "; winSize = " + winSize );
//	throw e1;
//}
						outChanBuf[ j ] = medianChanBuf[ winSizeH ];
					} else {	// buffer still in the process of filling up
						int newIdx = binarySearch( medianChanBuf, val, 0, medianChanNum - 1 );
						if( newIdx < 0 ) newIdx = -(newIdx + 1);
//						System.out.println( "ch = " + ch + "; newIdx = " + newIdx + "; medianChanNum = " + medianChanNum + "; winSize = " + winSize );
						System.arraycopy( medianChanBuf, newIdx, medianChanBuf, newIdx + 1, medianChanNum - newIdx );
						medianChanBuf[ newIdx ] = val;
						medianChanNum++;
						outChanBuf[ j ] = medianChanBuf[ medianChanNum >> 1 ];
					}
					
					histoChanBuf[ histoChanIdx++ ] = val;
//					histoChanIdx = (histoChanIdx + 1) % winSize;
//boolean failed=false;
//for( int ii = 0; ii < Math.max( histoChanIdx, medianChanNum ); ii++ ) {
//	if( binarySearch( medianChanBuf, histoChanBuf[ ii ], 0, medianChanNum - 1 ) < 0 ) {
//		System.out.println( "TEST FAILED! value " + histoChanBuf[ ii ] + " not found" );
//		failed=true;
//	}
//}
//if( failed ) {
//	if( !didLastCpy ) System.out.println( "DIDN'T DO LASTCPY" );
//	System.out.println( "last copy : from " + lastCpySrcIdx + "; to " + lastCpyDstIdx + "; len = " + lastCpyLen + "; oldIdx = " +lastOldIdx + "; newIdx = " + lastNewIdx + "; oldVal = " + lastOldVal + "; newVal = " + val );
//}
					
//					float prev = medianChanBuf[ 0 ];
//					for( int kk = 1; kk < medianChanNum; kk++ ) {
//						if( medianChanBuf[ kk ] < prev ) {
//							System.out.println( "TEST FAILED!" );
//						}
//						prev = medianChanBuf[ kk ]; 
//					}
				}
				
				if( ch == channels - 1 ) {
					medianNum	= medianChanNum;
					histoIdx	= histoChanIdx;
				}
			} // for channels
		} // process
	} // class MedianFilter

// this is about 6 times faster than the original code
//	private static class MedianFilterTEST
//	implements RunningWindowFilter
//	{
//		private final int		winSize;
//		private final int		channels;
//		
//		private final List[]	medianBuf;
//		private final Float[][]	histoBuf;
//		
//		private int				histoIdx	= 0;
//	
//		protected MedianFilterTEST( int winSize, int channels )
//		{
//			this.winSize	= winSize;
//			this.channels   = channels;
//			
//			medianBuf		= new List[ channels ];
//			for( int ch = 0; ch < channels; ch++ ) { 
//				medianBuf[ ch ] = new ArrayList( winSize );
////				medianBuf[ ch ] = new LinkedList();
//			}
//			histoBuf		= new Float[ channels ][ winSize ];
//		}
//	
//		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
//		throws IOException
//		{
//			for( int ch = 0; ch < channels; ch++ ) {
//				final List		medianChanBuf	= medianBuf[ ch ];
//				final float[]	inChanBuf		= inBuf[ ch ];
//				final float[]	outChanBuf		= outBuf[ ch ];
//				final Float[]	histoChanBuf	= histoBuf[ ch ];
//				            
//				for( int i = inOff, j = outOff, k = inOff + len; i < k; i++, j++ ) {
//					final Float oldVal = histoChanBuf[ histoIdx ]; 
//					if( oldVal != null ) {
//						medianChanBuf.remove( Collections.binarySearch( medianChanBuf, oldVal ));
////						medianChanBuf.remove( oldVal );
//					}
//					final Float inVal	= new Float( inChanBuf[ i ]);
//					final int	idx		= Collections.binarySearch( medianChanBuf, inVal );
//					if( idx >= 0 ) {
//						medianChanBuf.add( idx, inVal );
//					} else {
//						medianChanBuf.add( -(idx + 1), inVal );
//					}
//					outChanBuf[ j ] = ((Float) medianChanBuf.get( medianChanBuf.size() >> 1 )).floatValue();
//					histoChanBuf[ histoIdx ] = inVal;
//					histoIdx = (histoIdx + 1) % winSize;
//				}
//			} // for channels
//		} // process
//	} // class MedianFilter

// this is the original code (very slow)
//	private static class MedianFilterOLD
//	implements RunningWindowFilter
//	{
//		final int		winSize, medianOff, winSizeM;
//		final int		channels;
//		final float[][] buf;
//		final int[][]	idxBuf;
//	
//		protected MedianFilterOLD( int winSize, int channels )
//		{
//			this.winSize	= winSize;
//			this.channels   = channels;
//			
//			buf			= new float[channels][winSize];
//			idxBuf		= new int[channels][winSize];
//			medianOff   = winSize >> 1;
//			winSizeM	= winSize - 1;
//		}
//	
//		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
//		throws IOException
//		{
////			Util.clear( buf );
//			
//			int		ch, i, j, k, m, n;
//			float[] convBuf1, convBuf2, convBuf3;
//			int[]   convBuf4;
//			float   f1;
//			
//			for( ch = 0; ch < channels; ch++ ) {
//				convBuf1	= buf[ch];
//				convBuf2	= inBuf[ch];
//				convBuf3	= outBuf[ch];
//				convBuf4	= idxBuf[ch];
//				m			= inOff;
//				n			= outOff;
//				convBuf1[0] = convBuf2[m++];
//				convBuf4[0] = 0;
//
//				// --- calculate the initial median by sorting inBuf content of length 'winSize ---
//				// XXX this is a really slow sorting algorithm and should be replaced by a fast one
//				// e.g. by exchanging the j-loop by a step-algorithm (stepping right into
//				// i/2 and if f1 < convBuf1[i/2] steppping to i/4 else i*3/4 etc.
//				for( i = 1; i < winSize; i++ ) {
//					f1  = convBuf2[m++];
//					for( j = 0; j < i; j++ ) {
//						if( f1 < convBuf1[j] ) {
//							System.arraycopy( convBuf1, j, convBuf1, j + 1, i - j );
//							for( k = 0; k < i; k++ ) {
//								if( convBuf4[k] >= j ) convBuf4[k]++;
//							}
//							break;
//						}
//					}
//					convBuf1[j] = f1;
//					convBuf4[i] = j;
//				}
//				// now the median is approx. (for winSize >> 1) the sample in convBuf1[winSize/2]
//
////System.err.println( "A---unsorted---" );
////for( int p = 0; p < winSize; p++ ) {
////	System.err.println( p + " : "+convBuf2[inOff+p] );
////}
////System.err.println( " --sorted---" );
////for( int p = 0; p < winSize; p++ ) {
////	System.err.println( p + " : "+convBuf1[p] );
////}
//
//				// XXX this is a really slow sorting algorithm and should be replaced by a fast one
//				// e.g. by exchanging the j-loop by a step-algorithm (stepping right into
//				// i/2 and if f1 < convBuf1[i/2] steppping to i/4 else i*3/4 etc.
//				// ; also the two arraycopies could be collapsed into one or two shorter ones
//				for( i = 0; i < len; i++ ) {
//					convBuf3[n++] = convBuf1[medianOff];
//
//					j   = convBuf4[i%winSize];  // index of the element to be removed (i.e. shifted left out of the win)
//					System.arraycopy( convBuf1, j + 1, convBuf1, j, winSizeM - j );
//					for( k = 0; k < winSize; k++ ) {
//						if( convBuf4[k] > j ) convBuf4[k]--;
//					}
//					f1  = convBuf2[m++];
//					for( j = 0; j < winSizeM; j++ ) {
//						if( f1 < convBuf1[j] ) {
//							System.arraycopy( convBuf1, j, convBuf1, j + 1, winSizeM - j );
//							for( k = 0; k < winSize; k++ ) {
//								if( convBuf4[k] >= j ) convBuf4[k]++;
//							}
//							break;
//						}
//					}
//					// j = index of the element to be inserted (i.e. coming from the right side of the win)
//					convBuf1[j] = f1;
//					convBuf4[i%winSize] = j;
//				}
////System.err.println( "B---unsorted---" );
////for( int p = 0; p < winSize; p++ ) {
////	System.err.println( p + " : "+convBuf2[inOff+len+p] );
////}
////System.err.println( " ---sorted---" );
////for( int p = 0; p < winSize; p++ ) {
////	System.err.println( p + " : "+convBuf1[p] );
////}
//			} // for channels
//		} // process
//	} // class MedianFilter

	/*
	 *	Center Clipping for a variable threshold
	 *	which is determined by a running histogram
	 *	and a percentage threshold value
	 *
	 *	this only works if a) process() is
	 *	called on successive chunks; b) samples don't exceed +12 dBFS
	 *
	 *	@todo	delay compensation stimmt nicht
	 */
	private static class CenterClippingFilter
	extends RunningWindowFilter
	{
//		private final int			winSize;
		private final int			channels;
		private final int			winSizeM1;
		private final int[][]		histogram;
		private final int			threshSum;
		private boolean	init		= false;
		
		private static final int	HISTOSIZE		= 16384;
		private static final int	HISTOSIZEM1		= HISTOSIZE - 1;
		private static final float	HISTOSIZEM05	= HISTOSIZE - 0.5f;
		private static final int	HISTOSIZEH		= HISTOSIZE >> 1;
		
		protected CenterClippingFilter( int winSize, int channels, double threshAmp )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM1		= winSize - 1;
			histogram		= new int[ channels ][ HISTOSIZE ];
			threshSum		= (int) (threshAmp * winSize + 0.5);
		}

		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			float[]		convBuf2, convBuf3;
			int[]		convBuf4;
			int			histoIdx, histoSum;
			float		f1, clip;
			
			for( int ch = 0; ch < channels; ch++ ) {
				convBuf4	= histogram[ ch ];
				convBuf2	= inBuf[ ch ];
				convBuf3	= outBuf[ ch ];
				// calc first maximum
//				max			= 0.0f;
//				for( int i = 0, m = inOff; i < len; i++, m++ ) {
//					f1 = Math.abs( convBuf2[ m ]);
//					if( f1 > max ) max = f1;
//				}
				// then calc initial histo
//				for( int i = 0; i < 8192; i++ ) {
//					convBuf4[ i ] = 0;
//				}
				if( !init ) {
					for( int i = 0, j = inOff; i < winSizeM1; i++, j++ ) {
						f1			= convBuf2[ j ];
						histoIdx	= (int) (Math.sqrt( Math.min( 1.0f, Math.abs( f1 / 4))) * HISTOSIZEM05);
//						histoIdx	= 8191 - (int) (Math.log( Math.max( 4.656613e-10, Math.min( 1.0f, Math.abs( f1 / 4)))) * -381.2437);
						convBuf4[ histoIdx ]++;
					}
				}
				for( int j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					// shift by one : remove obsolete sample
					// and add new last window sample
					f1			= convBuf2[ m + winSizeM1 ];
					histoIdx	= (int) (Math.sqrt( Math.min( 1.0f, Math.abs( f1 / 4))) * HISTOSIZEM05);
//					histoIdx	= 8191 - (int) (Math.log( Math.max( 4.656613e-10, Math.min( 1.0f, Math.abs( f1 / 4)))) * -381.2437);
					convBuf4[ histoIdx ]++;

					// find thresh
					for( histoIdx = 0, histoSum = 0; histoIdx < HISTOSIZEH && histoSum < threshSum; histoIdx++ ) {
						histoSum += convBuf4[ histoIdx ];
					}
					clip	= (float) histoIdx / HISTOSIZEM1;
					clip	= clip*clip*4;
//					clip	= (float) (Math.exp( (histoIdx - 8191) / 381.2437 ) * 4);
					f1		= convBuf2[ m ];
					if( f1 >= 0.0f ) {
						convBuf3[ n ] = Math.max( 0.0f, f1 - clip );
					} else {
						convBuf3[ n ] = Math.min( 0.0f, f1 + clip );
					}
					f1			= convBuf2[ m ];	// now obsolete
					histoIdx	= (int) (Math.sqrt( Math.min( 1.0f, Math.abs( f1 / 4))) * HISTOSIZEM05);
//					histoIdx	= 8191 - (int) (Math.log( Math.max( 4.656613e-10, Math.min( 1.0f, Math.abs( f1 / 4)))) * -381.2437);
					convBuf4[ histoIdx ]--;
				}
			} // for channels
			
			init = true;
		} // process
	} // class CenterClippingFilter

	/*
	 *	Verfolgt das tatsaechliche Minimum,
	 *	mit nachgeschaltetem DC block filter
	 */
	private static class Minimum2Filter
	extends RunningWindowFilter
	{
		final int			channels;
		final int			winSizeM1;
		final double[][]	dcMem;
	
		protected Minimum2Filter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM1		= winSize - 1;
			dcMem			= new double[channels][2];
		}

		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, i, j, k, m, n, minidx;
			float[]		convBuf2, convBuf3;
			double[]	convBuf4;
			float		f1, f2, min;
			double		d2;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf4	= dcMem[ ch ];
				convBuf2	= inBuf[ch];
				convBuf3	= outBuf[ch];
				minidx		= -1;
				min			= 0.0f;
				for( j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					if( minidx < m ) {	// need to find again
						f1		= convBuf2[ m ];
						minidx	= m;
						for( i = 1, k = m + 1; i < winSize; i++, k++ ) {
							f2	= convBuf2[ k ];
							if( f2 < f1 ) {
								f1		= f2;
								minidx	= k;
							}
						}
						min	= f1;
					} else {
						f1	= convBuf2[ m + winSizeM1 ];
						if( f1 < min ) {
							min		= f1;
							minidx	= m + winSizeM1;
						}
					}

				// ---- remove DC ----
					d2				= min - convBuf4[ 0 ] + 0.99 * convBuf4[ 1 ];
					convBuf3[ n ]	= (float) d2;
					convBuf4[ 0 ]	= min;
					convBuf4[ 1 ]	= d2;
					minidx--;
				}
			} // for channels
		} // process
	} // class Minimum2Filter

	/*
	 *	Autokorrelations-Grad: Summe der Koefficienten
	 *	fuer alle Verschiebungen 0 ... N-1 (N = window size)
	 *	, plus DC block filter
	 */
	private static class AutoCorrelationFilter
	extends RunningWindowFilter
	{
		final int			winSizeM2;
		final int			channels;
		final double[][]	dcMem;
		final float[]		fftBuf;
		final int			fftSize;
		final int			fftSizeP2;
	
		protected AutoCorrelationFilter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM2		= winSize << 1;
			fftSize			= MathUtil.nextPowerOfTwo( winSizeM2 );
			fftSizeP2		= fftSize + 2;
			fftBuf			= new float[ fftSizeP2 ];
			dcMem			= new double[channels][2];
		}
	
		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, j, k, m, n;
			float[]		convBuf2, convBuf3;
			double[]	convBuf4;
			float		f1;
			double		d1, d2;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf4	= dcMem[ ch ];
				convBuf2	= inBuf[ch];
				convBuf3	= outBuf[ch];
				for( j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					System.arraycopy( convBuf2, m, fftBuf, 0, winSize );
					// calc rms
					d2 = 0.0;
					d1 = 0.0;
					for( k = 0; k < winSize; k++ ) {
						f1	= fftBuf[ k ];
						d2 += f1 * f1;
					}
					d2	= Math.sqrt( d2 / winSize );
					if( d2 > 0.0 ) {
						// zero padding
						for( ; k < fftSize; k++ ) {
							fftBuf[ k ] = 0.0f;
						}
						Fourier.realTransform( fftBuf, fftSize, Fourier.FORWARD );
						// autokorrelation: square amplitudes, zero phases
						for( k = 0; k < fftSizeP2; ) {
							f1				= fftBuf[ k ] / winSize;
							fftBuf[ k++ ]	= f1 * f1;
							fftBuf[ k++ ]	= 0.0f;
						}
						Fourier.realTransform( fftBuf, fftSize, Fourier.INVERSE );
						// sum coefficients
						for( k = 0; k < winSizeM2; k++ ) {
							d1 += fftBuf[ k ];
						}
						d1 /= d2;
					}

				// ---- remove DC ----
					d2				= d1 - convBuf4[ 0 ] + 0.99 * convBuf4[ 1 ];
					convBuf3[ n ]	= (float) d2;
					convBuf4[ 0 ]	= d1;
					convBuf4[ 1 ]	= d2;
				}
			} // for channels
		} // process
	} // class AutoCorrelationFilter

	/*
	 *	Autokorrelations-Grad: ueberlagerte fenster
	 *	fuer alle verschiebungen 0 ... N-1 (N = window size)
	 *	, plus DC block filter
	 */
	private static class AutoCorrelation2Filter
	extends RunningWindowFilter
	{
		private final int			winSizeD2;
		private final int			winSizeP4;
		private final int			channels;
		private final double[][]	dcMem;
		private final float[]		fftBuf;
		private final int			fftSize;
		private final int			fftSizeP2;
		private final float[][]		procBuf;
		private int					procBufOff	= 0;
	
		protected AutoCorrelation2Filter( int winSize, int channels )
		{
			super( winSize & ~1 );
			this.winSizeD2	= winSize >> 1;
			this.channels   = channels;
			winSizeP4		= this.winSizeD2; // * this.winSize; // Math.pow( this.winSize, 4 );
			fftSize			= MathUtil.nextPowerOfTwo( winSize );
			fftSizeP2		= fftSize + 2;
			fftBuf			= new float[ fftSizeP2 ];
			dcMem			= new double[ channels ][ 2 ];
			procBuf			= new float[ channels ][ winSize ];
		}

		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, j, k, m, n;
			float[]		convBuf2, convBuf3;
			double[]	convBuf4;
			float		f1;
			double		d1, d2;
			final int	procBufOffN = (procBufOff + 1) % winSize;
			
inOff += winSizeD2;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf4	= dcMem[ ch ];
				convBuf2	= inBuf[ch];
				convBuf3	= outBuf[ch];
				for( j = 0, m = inOff, n = outOff; j < len; j++, m++, n++ ) {
					System.arraycopy( convBuf2, m, fftBuf, 0, winSizeD2 );
					// calc rms
					d2 = 0.0;
					d1 = 0.0;
					for( k = 0; k < winSizeD2; k++ ) {
						f1	= fftBuf[ k ];
						d2 += f1 * f1;
					}
					d2 = Math.sqrt( d2 / winSizeD2 ) * winSizeP4;
					
					procBuf[ ch ][ (procBufOff + winSize) % winSize ] = 0.0f;
					
					if( d2 > 0.0 ) {
						// zero padding
						for( ; k < fftSize; k++ ) {
							fftBuf[ k ] = 0.0f;
						}
						Fourier.realTransform( fftBuf, fftSize, Fourier.FORWARD );
						// autokorrelation: square amplitudes, zero phases
						for( k = 0; k < fftSizeP2; ) {
							f1				= fftBuf[ k ] / winSizeD2;
							fftBuf[ k++ ]	= f1 * f1;
							fftBuf[ k++ ]	= 0.0f;
						}
						Fourier.realTransform( fftBuf, fftSize, Fourier.INVERSE );
//						// sum coefficients
//						for( k = 0; k < winSizeM2; k++ ) {
//							d1 += fftBuf[ k ];
//						}
						add( procBuf[ ch ], procBufOffN, fftBuf, 0, winSize - procBufOffN );
						add( procBuf[ ch ], 0, fftBuf, winSize - procBufOffN, procBufOffN );

//						d1 /= Math.sqrt( d2 / winSize ) * winSize;
						d1 = procBuf[ ch ][ (procBufOffN + winSizeD2) % winSize ] / d2;
					}

				// ---- remove DC ----
					d2				= d1 - convBuf4[ 0 ] + 0.99 * convBuf4[ 1 ];
					convBuf3[ n ]	= (float) d2;
					convBuf4[ 0 ]	= d1;
					convBuf4[ 1 ]	= d2;
				}
			} // for channels
			
			procBufOff = procBufOffN;
		} // process
	} // class AutoCorrelation2Filter

	/*
	 *	Minimum Phase schnuckendorfer
	 */
	private static class MinimumPhaseFilter
	extends RunningWindowFilter
	{
		private final int			winSizeM2;
		private final int			winSizeH;
		private final int			channels;
		private final double[][]	dcMem;
		private final float[]		fftBuf;
		private final int			fftSize;
		private final int			fftSizeP2;
		private final int			complexFFTsize;
		private final float[][]		winBuf;
		private final float			gain;
		private int					winBufIdx	= 0;
	
		protected MinimumPhaseFilter( int winSize, int channels )
		{
			super( winSize );
			
			this.channels   = channels;
			winSizeM2		= winSize << 1;
			winSizeH		= winSize >> 1;
			fftSize			= MathUtil.nextPowerOfTwo( winSizeM2 );
			fftSizeP2		= fftSize + 2;
			complexFFTsize	= fftSize << 1;
//			gain			= (float) (1.0 / Math.sqrt( winSize ));	// ??? seems to be ok
			gain			= 1.0f / winSize;
//			gain			= (float) (1.0 / Math.pow( winSize, 0.75 ));	// ??? seems to be ok
			fftBuf			= new float[ complexFFTsize ];
			dcMem			= new double[ channels ][ 2 ];
			winBuf			= new float[ channels ][ winSize ];
		}
	
		public void process( float[][] inBuf, float[][] outBuf, int inOff, int outOff, int len )
		throws IOException
		{
			int			ch, j, k, m, n, p;
			float[]		convBuf2, convBuf3, convBuf5, win;
			double[]	convBuf4;
			float		f1, f2, maxImpAmp;
			double		d1, d2;
			int			support;
			
			for( ch = 0; ch < channels; ch++ ) {
				convBuf4	= dcMem[ ch ];
				convBuf2	= inBuf[ ch ];
				convBuf3	= outBuf[ ch ];
				convBuf5	= winBuf[ ch ];
				for( j = 0, m = inOff, n = outOff, p = winBufIdx; j < len; j++, m++, n++, p = (p + 1) % winSize ) {
					System.arraycopy( convBuf2, m, fftBuf, 0, winSize );

					// zero padding
					for( k = winSize; k < fftSize; k++ ) {
						fftBuf[ k ] = 0.0f;
					}
					Fourier.realTransform( fftBuf, fftSize, Fourier.FORWARD );

					Fourier.rect2Polar(   fftBuf, 0, fftBuf, 0, fftSizeP2 );
					Fourier.unwrapPhases( fftBuf, 0, fftBuf, 0, fftSizeP2 );

					for( int jj = 0; jj <= fftSize; jj += 2 ) {
						fftBuf[ jj ] = (float) Math.log( Math.max( 1.0e-48, fftBuf[ jj ]));
					}
					// remove phase line ? XXX
					
				f1 = fftBuf[ 1 ];
				f2 = (fftBuf[ fftSize + 1 ] - f1) / (fftSize >> 1);
				for( int jj = 1, kk = 0; jj < fftSize + 2; jj += 2, kk++ ) {
					fftBuf[ jj ] -= f1 + kk * f2;
				}
					
					// make complex conjugate spectrum
					for( int jj = fftSize + 2, kk = fftSize - 2; jj < complexFFTsize; kk -= 2, jj += 2 ) {
						fftBuf[ jj ]	= fftBuf[ kk ];
						fftBuf[ jj+1 ]	= -fftBuf[ kk+1 ];
					}
					// transform to cepstrum domain
					Fourier.complexTransform( fftBuf, fftSize, Fourier.INVERSE );
					// fold cepstrum (make anticausal parts causal)
					for( int jj = 2, kk = complexFFTsize - 2; jj < fftSize; jj += 2, kk -= 2 ) {
						fftBuf[ jj ]   += fftBuf[ kk ];		// add conjugate left wing to right wing
						fftBuf[ jj+1 ] -= fftBuf[ kk+1 ];
					}
					fftBuf[ fftSize + 1 ] = -fftBuf[ fftSize + 1 ];
					// clear left wing
					for( int jj = fftSize + 2; jj < complexFFTsize; jj++ ) {
						fftBuf[ jj ] = 0.0f;
					}

					// back to frequency domain
					Fourier.complexTransform( fftBuf, fftSize, Fourier.FORWARD );
					// complex exponential : mag' = exp(real); phase' = imag
					for( int jj = 0; jj <= fftSize; jj += 2 ) {
						fftBuf[ jj ]		= (float) Math.exp( fftBuf[ jj ]);
					}
					
				for( int jj = 1, kk = 0; jj < fftSize + 2; jj += 2, kk++ ) {
					fftBuf[ jj ] += f1 + kk * f2;
				}

					// go to timedomain and apply window
					Fourier.polar2Rect( fftBuf, 0, fftBuf, 0, fftSize + 2 );
					Fourier.realTransform( fftBuf, fftSize, Fourier.INVERSE );

					maxImpAmp	= 0.0f;
					support		= winSizeH;	// will be overriden below

					for( int jj = 0; jj < winSize; jj++ ) {
						f1 = Math.abs( fftBuf[ jj ]);
						if( f1 > maxImpAmp ) {
							maxImpAmp	= f1;
							support		= jj;	// empiricially determine support from greatest elongation
						}
					}
					support		= support >> 1;

					if( support > 0 ) {
						win		= Filter.createWindow( support, Filter.WIN_KAISER6 );
						for( int jj = support - 1, kk = 0; kk < support; jj--, kk++ ) {
							fftBuf[ jj ] *= win[ kk ];
						}
						for( int jj = winSize - support, kk = 0; jj < winSize; jj++, kk++ ) {
							fftBuf[ jj ] *= win[ kk ];
						}
					}

					multiply( fftBuf, 0, winSize, gain );
					add( convBuf5, p, fftBuf, 0, winSize - p );
					add( convBuf5, 0, fftBuf, winSize - p, p );
//					d1 = convBuf5[ (p + winSizeH) % winSize ];
					d1 = convBuf5[ p ];
					convBuf5[ p ] = 0f;

				// ---- remove DC ----
					d2				= d1 - convBuf4[ 0 ] + 0.99 * convBuf4[ 1 ];
					convBuf3[ n ]	= (float) d2;
					convBuf4[ 0 ]	= d1;
					convBuf4[ 1 ]	= d2;
				}
			} // for channels
			
			winBufIdx = (winBufIdx + 1) % winSize;
			
		} // process
	} // class MinimumPhaseFilter
} // class Needlehole
