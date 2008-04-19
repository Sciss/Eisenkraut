/*
 *  ConstQ.java
 *  Eisenkraut
 *
 *  Copyright (c) 2001-2008 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.eisenkraut.math;

import java.util.prefs.Preferences;

import de.sciss.util.Param;

/**
 *	@version	0.70, 15-Apr-08
 *	@author		Hanns Holger Rutz
 */
public class ConstQ
{
	public static final String KEY_MINFREQ		= "minfreq";	// Param (Hz)
	public static final String KEY_MAXFREQ		= "maxfreq";	// Param (Hz)
	public static final String KEY_BANDSPEROCT	= "bandsperoct";	// Param (none)
	public static final String KEY_MAXTIMERES	= "maxtimeres" ;	// Param (ms)
	public static final String KEY_MAXFFTSIZE	= "maxfftsize";	// Param (String fftSize)
	
	private Kernel[]	kernels;
	private int			numKernels;
	private int			fftSize;
	private float[]		fftBuf;

	private float		minFreq		= 27.5f;
	private float		maxFreq		= 20000f;
	private float		maxTimeRes	= 5f;
	private int			bandsPerOct	= 24;
	private int			maxFFTSize	= 8192;
	
	private double		fs;
	
	private double		LNKORR_ADD; // = -2 * Math.log( fftSize );

	private static final double	TENBYLOG10				= 10 / MathUtil.LN10;

	public ConstQ() { /* empty */ }
	
	public int getNumKernels()
	{
		return numKernels;
	}

	public int getFFTSize()
	{
		return fftSize;
	}

	public void setSampleRate( double fs )
	{
		this.fs = fs;
	}
	
	public double getSampleRate()
	{
		return fs;
	}
	
	public void setMinFreq( float minFreq )
	{
		this.minFreq	= minFreq;
	}
	
	public float getMinFreq()
	{
		return minFreq;
	}
	
	public void setMaxFreq( float minFreq )
	{
		this.maxFreq	= minFreq;
	}
	
	public float getMaxFreq()
	{
		return maxFreq;
	}
	
	public void setMaxTimeRes( float maxTimeRes )
	{
		this.maxTimeRes	= maxTimeRes;
	}
	
	public float getMaxTimeRes()
	{
		return maxTimeRes;
	}
	
	public void setMaxFFTSize( int maxFFTSize )
	{
		this.maxFFTSize	= maxFFTSize;
	}
	
	public float getMaxFFTSize()
	{
		return maxFFTSize;
	}
	
	public void readPrefs( Preferences prefs )
	{
		Param p;
		String s;
		
		p = Param.fromPrefs( prefs, KEY_MINFREQ, null );
		if( p != null ) minFreq = Math.max( 1, (float) p.val );
		p = Param.fromPrefs( prefs, KEY_MAXFREQ, null );
		if( p != null ) maxFreq = Math.max( minFreq, (float) p.val );
		p = Param.fromPrefs( prefs, KEY_BANDSPEROCT, null );
		if( p != null ) bandsPerOct = Math.max( 1, (int) p.val );
		p = Param.fromPrefs( prefs, KEY_MAXTIMERES, null );
		if( p != null ) maxTimeRes = (float) p.val;
		s = prefs.get( KEY_MAXFFTSIZE, null );
		if( s != null ) {
			try {
				int i = Integer.parseInt( s );
				for( maxFFTSize = 256; maxFFTSize < i; maxFFTSize <<= 1 );
			}
			catch( NumberFormatException e1 ) { /* ignore */ }
		}
	}

	public float getFrequency( int kernel )
	{
		return kernels[ kernel ].freq;
	}
	
	public void createKernels()
	{
		final float		threshSqr, q;
		final double	maxKernLen;
		final int		fftSizeC;

		int				kernelLen, kernelLenE, specStart, specStop;
		float[]			win;
		float			centerFreq, f1, f2;
		double			theorKernLen, weight, d1, cos, sin;

//		System.out.println( "Calculating sparse kernel matrices" );
		
		maxFreq		= (float) Math.max( maxFreq, fs/2 );
		q			= (float) (1 / (Math.pow( 2, 1.0/bandsPerOct ) - 1));
		numKernels	= (int) Math.ceil( bandsPerOct * MathUtil.log2( maxFreq / minFreq ));
		kernels		= new Kernel[ numKernels ];
//		cqKernels	= new float[ cqKernelNum ][];
//		cqKernelOffs= new int[ cqKernelNum ];
		maxKernLen	= q * fs / minFreq;
		
//		System.out.println( "ceil " + ((int) Math.ceil( maxKernLen )) + "; nextPower " + (MathUtil.nextPowerOfTwo( (int) Math.ceil( maxKernLen )))) ;
		
		fftSize		= Math.min( maxFFTSize,
		    MathUtil.nextPowerOfTwo( (int) Math.ceil( maxKernLen )));
		LNKORR_ADD	= -2 * Math.log( fftSize );
		fftSizeC	= fftSize << 1;
		fftBuf		= new float[ fftSizeC ];
//		thresh		= 0.0054f / fftLen; // for Hamming window
		// weird observation : lowering the threshold will _increase_ the
		// spectral noise, not improve analysis! so the truncating of the
		// kernel is essential for a clean analysis (why??). the 0.0054
		// seems to be a good choice, so don't touch it.
		threshSqr	= 2.916e-05f / (fftSize * fftSize); // for Hamming window (squared!)
		// tempKernel= zeros(fftLen, 1); 
//		sparKernel= [];
		
//		System.out.println( "cqKernelNum = " + cqKernelNum + "; maxKernLen = " + maxKernLen + "; fftSize = " + fftSize + "; threshSqr " + threshSqr );
		
		for( int k = 0; k < numKernels; k++ ) {
			theorKernLen = maxKernLen * (float) Math.pow( 2, (double) -k / bandsPerOct );
			kernelLen	= Math.min( fftSize, (int) Math.ceil( theorKernLen ));
			kernelLenE	= kernelLen & ~1;
			win			= Filter.createFullWindow( kernelLen, Filter.WIN_HAMMING );
			
			centerFreq	= (float) (-MathUtil.PI2 * minFreq * Math.pow( 2, (float) k / bandsPerOct ) / fs);

//			weight		= 1.0f / (kernelLen * fftSize);
			// this is a good approximation in the kernel len truncation case
			// (tested with pink noise, where it will appear pretty much
			// with the same brightness over all frequencies)
			weight		= 2 / ((theorKernLen + kernelLen) * fftSize);
			for( int m = kernelLenE, n = fftSizeC - kernelLenE; m < n; m++ ) {
				fftBuf[ m ] = 0f;
			}
									
			// note that we calculate the complex conjugation of
			// the temporalKernal and reverse its time, so the resulting
			// FFT can be immediately used for the convolution and does not
			// need to be conjugated; this is due to the Fourier property
			// h*(-x) <-> H*(f). time reversal is accomplished by
			// having iteration variable j run....

			// XXX NO! we don't take the reversed conjugate since
			// there seems to be a bug with Fourier.complexTransform
			// that already computes the conjugate spectrum (why??)
//			for( int i = kernelLen - 1, j = fftSizeC - kernelLenE; i >= 0; i-- ) { ... }
			for( int i = 0, j = fftSizeC - kernelLenE; i < kernelLen; i++ ) {
				// complex exponential of a purely imaginary number
				// is cos( imag( n )) + i sin( imag( n ))
				f1			= centerFreq * i;
				cos			= Math.cos( f1 );
				sin			= Math.sin( f1 ); // Math.sqrt( 1 - cos*cos );
				d1			= win[ i ] * weight;
				fftBuf[ j++ ] = (float) (d1 * cos);
//				fftBuf[ j++ ] = -f1 * sin;  // conj!
				fftBuf[ j++ ] = (float) (d1 * sin);  // NORM!
				if( j == fftSizeC ) j = 0;
			}
			
			// XXX to be honest, i don't get the point
			// of calculating the fft here, since we
			// have an analytic description of the kernel
			// function, it should be possible to calculate
			// the spectral coefficients directly
			// (the fft of a hamming is a gaussian,
			// isn't it?)
			
			Fourier.complexTransform( fftBuf, fftSize, Fourier.FORWARD );
			// with a "high" threshold like 0.0054, the
			// point it _not_ to create a sparse matrix by
			// gating the values. in fact we can locate
			// the kernal spectrally, so all we need to do
			// is to find the lower and upper frequency
			// of the transformed kernel! that makes things
			// a lot easier anyway since we don't need
			// to employ a special sparse matrix library.
			for( specStart = 0; specStart <= fftSize; specStart += 2 ) {
				f1 = fftBuf[ specStart ];
				f2 = fftBuf[ specStart+1 ];
				if( (f1 * f1 + f2 * f2) > threshSqr ) break;
			}
			// final matrix product:
			// input chunk (fft'ed) is a row vector with n = fftSize
			// kernel is a matrix mxn with m = fftSize, n = numKernels
			// result is a row vector with = numKernels
			// ; note that since the input is real and hence we
			// calculate only the positive frequencies (up to pi),
			// we might need to mirror the input spectrum to the
			// negative frequencies. however it can be observed that
			// for practically all kernels their frequency bounds
			// lie in the positive side of the spectrum (only the
			// high frequencies near nyquest blur accross the pi boundary,
			// and we will cut the overlap off by limiting specStop
			// to fftSize instead of fftSize<<1 ...).

			for( specStop = specStart; specStop <= fftSize; specStop += 2 ) {
				f1 = fftBuf[ specStop ];
				f2 = fftBuf[ specStop+1 ];
				if( (f1 * f1 + f2 * f2) <= threshSqr ) break;
			}

			kernels[ k ] = new Kernel( specStart, new float[ specStop - specStart ], centerFreq );
			System.arraycopy( fftBuf, specStart, kernels[ k ].data, 0, specStop - specStart );
		}
	}
	
	public float[] transform( float[] input, int inOff, int inLen, float output[], int outOff, double gain )
	{
		if( output == null ) output = new float[ numKernels ];
		
		final int off, num, num2;
		float[]	kern;
		float	f1, f2;
		
		gain *= TENBYLOG10;
		off = fftSize >> 1;
		num = Math.min( fftSize - off, inLen );
		
//System.out.println( "inOff " + inOff + "; num  " + num + " ; inOff + num " + (inOff + num) + "; input.length " + input.length + "; fftBuf.length " + fftBuf.length );
		
		System.arraycopy( input, inOff, fftBuf, off, num );
		for( int i = off + num; i < fftSize; i++ ) {
			fftBuf[ i ] = 0f;
		}
		num2 = Math.min( fftSize - num, inLen - num );
		System.arraycopy( input, inOff + num, fftBuf, 0, num2 );
		for( int i = num2; i < off; i++ ) {
			fftBuf[ i ] = 0f;
		}
		
		// XXX evtl., wenn inpWin weggelassen werden kann,
		// optimierte overlap-add fft
		Fourier.realTransform( fftBuf, fftSize, Fourier.FORWARD );
		
		for( int k = 0; k < numKernels; k++, outOff++ ) {
			kern = kernels[ k ].data;
			f1 = 0f;
			f2 = 0f;
			for( int i = kernels[ k ].offset, j = 0; j < kern.length; i += 2, j += 2 ) {
				// complex mult: a * b =
				// (re(a)re(b)-im(a)im(b))+i(re(a)im(b)+im(a)re(b))
				// ; since we left out the conjugation of the kernel(!!)
				// this becomes (assume a is input and b is kernel):
				// (re(a)re(b)+im(a)im(b))+i(im(a)re(b)-re(a)im(b))
				// ; in fact this conjugation is unimportant for the
				// calculation of the magnitudes...
				f1 += fftBuf[ i ] * kern[ j ] - fftBuf[ i+1 ] * kern[ j+1 ];
				f2 += fftBuf[ i ] * kern[ j+1 ] + fftBuf[ i+1 ] * kern[ j ];
			}
//			cBuf[ k ] = ;  // squared magnitude
			f1 = (float) ((Math.log( f1 * f1 + f2 * f2 ) + LNKORR_ADD) * gain);
f1 = Math.max( -160, f1 + 90 );	// XXX
			output[ outOff ] = f1;
		}
		
		return output;
	}
	
	private static class Kernel
	{
		protected final int			offset;
		protected final float[]		data;
		protected final float		freq;
		
		protected Kernel( int offset, float[] data, float freq )
		{
			this.offset	= offset;
			this.data	= data;
			this.freq	= freq;
		}
	}
}