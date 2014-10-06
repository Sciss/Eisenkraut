/*
 *  SlidingDFT.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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
 * 	For the sliding DFT algorithm, see for example
 * 	Bradford/Dobson/ffitch "Sliding is smoother than jumping".
 * 	
 *	@version	0.70, 25-Apr-08
 *	@author		Hanns Holger Rutz
 */
public class SlidingDFT
{
	private final int			fftSize;
	private final int			fftSizeP2;
	private final int			bins;
//	private final int			binsQ1;
//	private final int			binsQ3;
	private final double[]		cos;
	private final double[]		sin;
	private final float[][]		timeBuf;
	private final double[][]	fftBufD;
	private int[]				timeBufIdx;
	
	public SlidingDFT( int fftSize, int numChannels )
	{
		this.fftSize	= fftSize;

		final double	d1;
		final int		binsH;
		double			d2;
		
		bins		= fftSize >> 1;
		fftSizeP2	= fftSize + 2;
		fftBufD		= new double[ numChannels ][ fftSizeP2 ];
		d1			= MathUtil.PI2 / fftSize;
//		d1			= Math.PI / fftSize;
//		binsQ1		= bins >> 2;
//		fftSizeQ2	= fftSizeQ1 << 1;
		binsH		= bins >> 1;
//		binsQ		= bins >> 2;
//		binsQ3		= bins - binsQ1;
		
		// well we could save 25% of the storage tables
		// by calculating the cosine table till 1.5pi instead
		// of pi and using it for sine lookup
		cos			= new double[ bins + 1 ];
		sin			= new double[ bins + 1 ];
		timeBuf		= new float[ numChannels ][ fftSize ];
		timeBufIdx	= new int[ numChannels ];
		// note that cos[ binsH ] == 0, so we can write i < binsH instead of i <= binsH
		// and don't invert the roundoff error here.
//double phas = Math.PI/2;
		for( int i = 0, j = bins, k = binsH, m = binsH; i < binsH; i++, j--, k--, m++ ) {
			d2		 = Math.cos( d1 * i );
			cos[ i ] = d2;
			cos[ j ] = -d2;
			sin[ k ] = d2;
			sin[ m ] = d2;
		}
	}
	
	public void next( float[] inBuf, int inOff, int len, int chan, float[] fftBuf )
	{
		// formula: ("The Sliding DFT" Tutorial)
		// Xk[n] = (Xk[n-1] - x[n-N] + x[n]) * exp( j2pi*k/N )
		
		final double[]	fftBufDC	= fftBufD[ chan ];
		final float[]	timeBufC	= timeBuf[ chan ];
		int				timeBufIdxC	= timeBufIdx[ chan ];
		double			delta, re1, im1, re2, im2;
		float			f1;
				
		for( int i = 0, j = inOff; i < len; i++, j++ ) {
			f1						= inBuf[ j ];
			delta					= (double) f1 - (double) timeBufC[ timeBufIdxC ];

			timeBufC[ timeBufIdxC ]	= f1;
			for( int k = 0, m = 0; m < fftSizeP2; k++ ) {
// Unfortunately we cannot just add the real input since due to
// fftBuf buffer rotation, new samples don't come in at index
// fftSize - 1, but instead add fftSize/2 - 1!
//				re1				= fftBufDC[ m ] + delta;
//				im1				= fftBufDC[ m + 1 ];
				
// this is the theoretical rotation needed for the fftBuf rotation
//				double deltaRe = delta * Math.cos( MathUtil.PI2 * k * bins / fftSize );
//				double deltaIm = delta * Math.sin( MathUtil.PI2 * k * bins / fftSize );
// obviously cos( PI * k ) cycles 1, -1, 1, -1 etc. ; and sin( PI * k ) is zero! 
//				double deltaRe = delta * Math.cos( Math.PI * k );
//				double deltaIm = delta * Math.sin( Math.PI * k );
//				re1				= fftBufDC[ m ] + deltaRe;
//				im1				= fftBufDC[ m + 1 ] + deltaIm;

				// so here's the easy one:
				if( (k & 1) == 0 ) {
					re1			= fftBufDC[ m ] + delta;
				} else {
					re1			= fftBufDC[ m ] - delta;
				}
				im1				= fftBufDC[ m + 1 ];

				re2				= cos[ k ];
				im2				= sin[ k ];
//				// sin( x ) == cos( x + 1.5pi )
//				im2				= cos[ (k + binsQ3) % bins ];
//re2 = Math.cos( -MathUtil.PI2 * k / fftSize );
//if( i == 0 ) System.out.println( "Should be " + re2 + ", but is " + cos[k ]);
//im2 = Math.sin( -MathUtil.PI2 * k / fftSize );
//if( i == 0 ) System.out.println( "Should be " + im2 + ", but is " + sin[k ]);
//				fftBufDC[ m++ ]	= re1 * re2 - im1 * im2;
//				fftBufDC[ m++ ]	= re1 * im2 + re2 * im1;
				fftBufDC[ m++ ]	= re1 * re2 - im1 * im2; // + deltaRe;
				fftBufDC[ m++ ]	= re1 * im2 + re2 * im1; // + deltaIm;
			}
			if( ++timeBufIdxC == fftSize ) timeBufIdxC = 0;
		}
		timeBufIdx[ chan ] = timeBufIdxC;
		// now cast the internal fftBufDC back to fftBuf
		for( int i = 0; i < fftSizeP2; i++ ) {
			fftBuf[ i ] = (float) fftBufDC[ i ];
		}
		
// this is to compare with non-sliding variant
//		int gaga = timeBufIdxC;
//		for( int i = 0; i < fftSize; i++ ) {
//			fftBuf[ (i + (fftSize >> 1)) % fftSize ] = timeBufC[ gaga % fftSize ];
////		fftBuf[ i ] = timeBufC[ gaga % fftSize ];
//			gaga++;
//		}
//		Fourier.realTransform( fftBuf, fftSize, Fourier.FORWARD );
	}
}