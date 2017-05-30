/*
 *  FastLog.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.math;

/**
 * 	Implementation of the ICSILog algorithm
 *	as described in O. Vinyals, G. Friedland, N. Mirghafori
 *	"Revisiting a basic function on current CPUs: A fast logarithm implementation
 *	with adjustable accuracy" (2007).
 *
 *	@see		java.lang.Float#floatToRawIntBits( float )
 */
public class FastLog
{
	private final int		q, qM1;
	private final float[]	data;
	private float			korr;

	/**
	 * 	Create a new logarithm calculation instance. This will
	 * 	hold the pre-calculated log values for a given base
	 * 	and a table size depending on a given mantissa quantization.
	 * 
	 *	@param	base	the logarithm base (e.g. 2 for log duals, 10 for
	 *					decibels calculations, Math.E for natural log)
	 *	@param	q		the quantization, the number of bits to remove
	 *					from the mantissa. for q = 11, the table storage
	 *					requires 32 KB.
	 */
	public FastLog( double base, int q )
	{
		final int tabSize = 1 << (24 - q);

		this.q	= q;
		qM1		= q - 1;
		korr	= (float) (MathUtil.LN2 / Math.log( base ));
		data	= new float[ tabSize ];
		
		for( int i = 0; i < tabSize; i++ ) {
			// note: the -150 is to avoid this addition in the calculation
			// of the exponent (see the floatToRawIntBits doc).
			data[ i ] = (float) (MathUtil.log2( i << q ) - 150);
		}
	}
	
	/**
	 *	Calculate the logarithm to the base given in the constructor.
	 *
	 *	@param	x	the argument. must be positive!
	 *	@return		log( x )
	 */
	public float calc( float x )
	{
//		final int raw	= Float.floatToRawIntBits( x );
		final int raw	= Float.floatToIntBits( x );
		final int exp	= (raw >> 23) & 0xFF;
		final int mant	= (raw & 0x7FFFFF);

		return (exp + data[ exp == 0 ?
			(mant >> qM1) :
			((mant | 0x800000) >> q) ]) * korr;
	}
}
