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

/**
 *	@version	0.70, 15-Apr-08
 *	@author		Hanns Holger Rutz
 */
public class ConstQ
{
//	private double		fs;
	private Kernel[]	kernels;
	
	public ConstQ() { /* empty */ }
	
	public int getNumKernels()
	{
		return kernels.length;
	}
	
//	public double getSampleRate()
//	{
//		return fs;
//	}

	public float getFrequency( int kernel )
	{
		return kernels[ kernel ].freq;
	}
	
	public void createKernels( float minFreq, float maxFreq, int binsPerOct, double fs )
	{
		final float		threshSqr, q;
		final double	maxKernLen;
		final int		fftSize, numKernels, fftSizeC, fftSizeLimit;
		final float[]	fftBuf;

		int				kernelLen, kernelLenE, specStart, specStop;
		float[]			win;
		float			centerFreq, f1, f2;
		double			theorKernLen, weight, d1, cos, sin;

//		System.out.println( "Calculating sparse kernel matrices" );
		
		maxFreq		= (float) Math.max( maxFreq, fs/2 );
		q			= (float) (1 / (Math.pow( 2, 1.0/binsPerOct ) - 1));
		numKernels	= (int) Math.ceil( binsPerOct * MathUtil.log2( maxFreq / minFreq ));
		kernels		= new Kernel[ numKernels ];
//		cqKernels	= new float[ cqKernelNum ][];
//		cqKernelOffs= new int[ cqKernelNum ];
		maxKernLen	= q * fs / minFreq;
		
//		System.out.println( "ceil " + ((int) Math.ceil( maxKernLen )) + "; nextPower " + (MathUtil.nextPowerOfTwo( (int) Math.ceil( maxKernLen )))) ;
		
		fftSizeLimit= 8192;
		fftSize		= Math.min( fftSizeLimit,
		    MathUtil.nextPowerOfTwo( (int) Math.ceil( maxKernLen )));
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
			theorKernLen = maxKernLen * (float) Math.pow( 2, (double) -k / binsPerOct );
			kernelLen	= Math.min( fftSize, (int) Math.ceil( theorKernLen ));
			kernelLenE	= kernelLen & ~1;
			win			= Filter.createFullWindow( kernelLen, Filter.WIN_HAMMING );
			
			centerFreq	= (float) (-MathUtil.PI2 * minFreq * Math.pow( 2, (float) k / binsPerOct ) / fs);

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