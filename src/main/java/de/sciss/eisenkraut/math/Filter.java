/*
 *  Filter.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.math;

public class Filter
{
// -------- public variables --------

    public static final int FLT_SMP_PER_CROSSING = 256;

    public static final int WIN_HAMMING			= 0;
    public static final int WIN_BLACKMAN		= 1;
    public static final int WIN_KAISER4			= 2;
    public static final int WIN_KAISER5			= 3;
    public static final int WIN_KAISER6			= 4;
    public static final int WIN_KAISER8			= 5;
    public static final int WIN_RECT			= 6;
    public static final int WIN_HANNING			= 7;
    public static final int WIN_TRI				= 8;

    public static final int WIN_MAX				= 8;

// -------- private variables --------

    private static final double LN10 = Math.log( 10 );

    private static final String[] windowNames = {
        "Hamming", "Blackman", "Kaiser ß=4", "Kaiser ß=5", "Kaiser ß=6", "Kaiser ß=8", "Rectangle",
        "von Hann", "Triangle"
    };

    private static final float[] dBAweight = {
    //		1 Hz		1.25		1.6			2			2.5			3.15
        0.0f,		1.000e-7f,	2.512e-7f,	6.310e-7f,	1.567e-6f,	3.890e-6f,
    //		4			5			6.3			8			10			12
        9.661e-6f,	2.371e-5f,	5.370e-5f,	1.365e-4f,	3.162e-4f,	7.161e-4f,
    //		16			20			25			31.5		40			50
        1.531e-3f,	3.126e-3f,	6.026e-3f,	1.096e-2f,	1.905e-2f,	3.090e-2f,
    //		63			80			100			125			160			200
        4.955e-2f,	7.586e-2f,	1.109e-1f,	1.585e-1f,	2.163e-1f,	2.884e-1f,
    //		250			315			400			500			630			800
        3.715e-1f,	4.677e-1f,	5.754e-1f,	6.918e-1f,	8.035e-1f,	9.120e-1f,
    //		1k			1.25k		1.6k		2k			2.5k		3.15k
        1.0f,		1.072f,		1.122f,		1.148f,		1.161f,		1.148f,
    //		4k			5k		6.3k		8k			10k			12.5k
        1.122f,		1.059f,		9.886e-1f,	8.810e-1f,	7.499e-1f,	6.095e-1f,
    //		16k			20k			25k			31.5k
        4.677e-1f,	3.428e-1f,	2.399e-1f,	0.0f
    };


// -------- public methods --------

    /**
     *	Fills array with dB(A)-weighting factors
     *
     *	@param	weights				Array of size 'num', which will be filled
     *	@param	freq				Center frequencies in Hertz; this object may be identical to 'weights',
     *								i.e. the frequencies will be overwritten by their
     *								corresponding weights
     *	@param	num					number of weights or frequencies
     */
    public static void getDBAweights( float[] weights, float[] freq, int num )
    {
        float f, f2;
        int	i, j;

        for( i = 0; i < num; i++ ) {
            f = freq[i];
            if( f < 1.0f ) {
                weights[i]	= dBAweight[0];
            } else if( f > 31622.7f ) {
                weights[i]	= dBAweight[dBAweight.length-1];
            } else {
                f2	= (float) (10 * Math.log( f ) / LN10);
                j	= (int) f2;
                f2 %= 1.0f;
                weights[i]	= dBAweight[j] * (1.0f - f2) + dBAweight[j+1] * f2;	// ? XXX
            }
        }
    }

    /**
     *	@param	impResp				target array of size 'halfWinSize' for impulse response
     *	@param	freq				cut-off frequency
     *	@param	halfWinSize			size of the Kaiser window divided by two
     *	@param	kaiserBeta			parameter for Kaiser window
     *	@param	fltSmpPerCrossing	number of coefficients per period
     */
    public static void createLPF(float[] impResp, float freq,
                                 int halfWinSize, float kaiserBeta, int fltSmpPerCrossing) {
        final double smpRate = freq * 2.0;
        final double normFactor = 1.0 / (halfWinSize - 1);
        double iBeta;
        double d;

        // ideal lpf = infinite sinc-function; create truncated version
        impResp[0] = (float) smpRate;
        for (int i = 1; i < halfWinSize; i++) {
            d = Math.PI * i / fltSmpPerCrossing;
            impResp[i] = (float) (Math.sin(smpRate * d) / d);
        }

        // apply Kaiser window
        iBeta = 1.0 / calcBesselZero(kaiserBeta);
        for (int i = 1; i < halfWinSize; i++) {
            d = i * normFactor;
            impResp[i] *= calcBesselZero(kaiserBeta * Math.sqrt(1.0 - d * d)) * iBeta;
        }
    }

    public static float createAntiAliasFilter(float[] impResp, float[] impRespD, int NWing,
                                              float rollOff, float kaiserBeta) {
        return Filter.createAntiAliasFilter(impResp, impRespD, NWing, FLT_SMP_PER_CROSSING,
                rollOff, kaiserBeta);
    }

    /**
     *	@param	impResp			will be filled with impulse response
     *	@param	impRespD		differences: null is permitted, in this case no interpolation is possible
     *	                        for resampling
     *	@param	NWing			number of coefficients; i.e. smpPerCrossing * NumberOfZeroCrossings
     *	@param	smpPerCrossing	refers to the sinc
     *	@param	rollOff			0...1 CutOff
     *	@param	kaiserBeta		parameter for Kaiser window
     *
     *	@return	Gain value (abs amp), which would compensate the loss of energy due to LPF
     */
    public static float createAntiAliasFilter(float[] impResp, float[] impRespD, int NWing,
                                              int smpPerCrossing, float rollOff, float kaiserBeta )
    {
        int		i;
        float	DCGain	= 0.0f;

        createLPF( impResp, 0.5f * rollOff, NWing, kaiserBeta, smpPerCrossing );

        if( impRespD != null ) {
            for( i = 0; i < NWing - 1; i++ ) {
                impRespD[ i ]	= impResp[ i + 1 ] - impResp[ i ];
            }
            impRespD[ i ]	= -impResp[ i ];
        }
        for( i = smpPerCrossing; i < NWing; i+= smpPerCrossing ) {
            DCGain	+= impResp[ i ];
        }
        DCGain = 2 * DCGain + impResp[ 0 ];

/*		Frame f = new Frame( "Yo chuck" );
        f.setSize( 440, 440 );
        f.setVisible( true );
        Graphics g = f.getGraphics();
        int lastX = 0;
        int lastY = 0;
        int x;
        int y;
        for( i = 0; i < NWing; i++ ) {
            x = (int) ((float) i / (float) NWing * 200);
            y = (int) (impResp[ i ] / impResp[ 0 ] * 200);
            g.drawLine( lastX + 220, 220 - lastY, x + 220, 220 - y );
            g.drawLine( 220 - lastX, 220 - lastY, 220 - x, 220 - y );
            lastX = x;
            lastY = y;
        }
        g.dispose();
*/		
        return( 1.0f / Math.abs( DCGain ));
    }

    /**
     *	@param NWing	Number of Points (the window is only a half wing!)
     */
    public static float[] createKaiserWindow(int NWing, float kaiserBeta) {
        final float[] win = new float[NWing];
        final double normFactor = 1.0 / (NWing - 1);
        double d;
        double iBeta;

        iBeta = 1.0 / calcBesselZero(kaiserBeta);
        win[0] = 1.0f;
        for (int i = 1; i < NWing; i++) {
            d = i * normFactor;
            win[i] = (float) (calcBesselZero(kaiserBeta * Math.sqrt(1.0 - d * d)) * iBeta);
        }

        return win;
    }

    /**
     *	Calculates a full window, i.e. both left and right wing.
     *	This is preferable over the single-wing version,
     *	because only here the periodicity is actually correct.
     *
     *	@param length	Number of Points (should be even)
     *	@return			Maximum will be sample length/2!
     */
    public static float[] createFullKaiserWindow(int length, float kaiserBeta) {
        final float[] win = new float[length];
        final int NWing = length >> 1;
        final double normFactor = 1.0 / NWing;
        double d;
        double iBeta;

        iBeta = 1.0 / calcBesselZero(kaiserBeta);
        for (int i = 0, j = -NWing; i < length; i++, j++) {
            d = j * normFactor;
            win[i] = (float) (calcBesselZero(kaiserBeta * Math.sqrt(1.0 - d * d)) * iBeta);
        }

        return win;
    }

    /**
     *	@param NWing	Number of Points (the window is only a half wing!)
     *	@param type		WIN_....
     */
    public static float[] createWindow( int NWing, int type )
    {
        float[] win = new float[ NWing ];
        double	normFactor	= Math.PI / (NWing - 1);
        double	d;
        float	f;
        int		i;

        switch( type ) {
        case WIN_BLACKMAN:
            for( i = 0; i < NWing; i++ ) {
                d			= i * normFactor;
                win[ i ]	= (float) (0.42 + 0.5 * Math.cos( d ) + 0.08 * Math.cos( 2 * d ));
            }
            break;
        case WIN_HANNING:
            for( i = 0; i < NWing; i++ ) {
                d			= i * normFactor;
                win[ i ]	= (float) (0.5 + 0.5 * Math.cos( d ));
            }
            break;
        case WIN_KAISER4:
            return createKaiserWindow( NWing, 4.0f );
        case WIN_KAISER5:
            return createKaiserWindow( NWing, 5.0f );
        case WIN_KAISER6:
            return createKaiserWindow( NWing, 6.0f );
        case WIN_KAISER8:
            return createKaiserWindow( NWing, 8.0f );
        case WIN_RECT:
            for( i = 0; i < NWing; i++ ) {
                win[ i ]	= 1.0f;
            }
            break;
        case WIN_TRI:
            f = 1.0f / (NWing - 1);
            for( i = 0; i < NWing; i++ ) {
                win[ i ]	= (NWing - 1 - i) * f;
            }
            break;
        default:	// WIN_HAMMING
            for( i = 0; i < NWing; i++ ) {
                d			= i * normFactor;
                win[ i ]	= (float) (0.54 + 0.46 * Math.cos( d ));
            }
            break;
        }

        return win;
    }

    /**
     *	Calculates a full window, i.e. both left and right wing.
     *	This is preferable over the single-wing version,
     *	because only here the periodicity is actually correct.
     *
     *	@param length	Number of Points (should be even)
     *	@param type		WIN_....
     *	@return			Maximum is sample length/2!
     */
    public static float[] createFullWindow( int length, int type )
    {
        float[] win = new float[ length ];
        int		NWing		= length >> 1;
        double	normFactor	= Math.PI / NWing;
        double	d;
        float	f;
        int		i, j;

        switch( type ) {
        case WIN_BLACKMAN:
            for( i = 0, j = -NWing; i < length; i++, j++ ) {
                d			= j * normFactor;
                win[ i ]	= (float) (0.42 + 0.5 * Math.cos( d ) + 0.08 * Math.cos( 2 * d ));
            }
            break;
        case WIN_HANNING:
            for( i = 0, j = -NWing; i < length; i++, j++ ) {
                d			= j * normFactor;
                win[ i ]	= (float) (0.5 + 0.5 * Math.cos( d ));
            }
            break;
        case WIN_KAISER4:
            return createFullKaiserWindow( length, 4.0f );
        case WIN_KAISER5:
            return createFullKaiserWindow( length, 5.0f );
        case WIN_KAISER6:
            return createFullKaiserWindow( length, 6.0f );
        case WIN_KAISER8:
            return createFullKaiserWindow( length, 8.0f );
        case WIN_RECT:
            for( i = 0; i < length; i++ ) {
                win[ i ]	= 1.0f;
            }
            break;
        case WIN_TRI:
            f = 1.0f / NWing;
            for( i = 0; i <= NWing; i++ ) {
                win[ i ]	= i * f;
            }
            for( j = i; i < length; ) {
                win[ i++ ]	= win[ --j ];
            }
            break;
        default:	// WIN_HAMMING
            for( i = 0, j = -NWing; i < length; i++, j++ ) {
                d			= j * normFactor;
                win[ i ]	= (float) (0.54 + 0.46 * Math.cos( d ));
            }
            break;
        }

        return win;
    }

    /**
     *	Obtains array with names of existing windows
     */
    public static String[] getWindowNames()
    {
        return windowNames;
    }

    /**
     *	Obtains types for given window name; can be used as parameter for createWindow; if
     *	name is unknown, Hamming window is returned
     */
    public static int getWindowType(String windowName) {
        for (int i = 0; i < windowNames.length; i++) {
            if (windowNames[i].equals(windowName)) return i;
        }
        return WIN_HAMMING;
    }

    /**
     *	Sample data; "band-limited interpolation"
     *
     *	@param	srcOff		allows sub-sample shift!
     *	@param	length		refers to dest!! src must have length 'length/factor' (ceil / rounded up)!
     *	@param	factor		dest-smpRate/src-smpRate
     *	@param	quality		0 = low, 1 = medium, 2 = high
     */
    public static void resample(float[] src, double srcOff, float[] dest, int destOff, int length,
                                double factor, int quality) {
        int		fltSmpPerCrossing	= 4096;
        int		fltCrossings;
        float	fltRollOff, fltWin;
        switch( quality ) {
        case 0:
            fltRollOff				= 0.70f;
            fltWin					= 6.5f;
            fltCrossings			= 5;
            break;
        case 1:
            fltRollOff				= 0.80f;
            fltWin					= 7.0f;
            fltCrossings			= 9;
            break;
        default:
            fltRollOff				= 0.86f;
            fltWin					= 7.5f;
            fltCrossings			= 15;
            break;
        }
        int		fltLen				= (int) ((fltSmpPerCrossing * fltCrossings) / fltRollOff + 0.5f);
        float[] flt = new float[ fltLen ];
        float[] fltD = null;	// new float[ fltLen ];
        float	gain				= Filter.createAntiAliasFilter( flt, fltD, fltLen, fltSmpPerCrossing,
                                                                    fltRollOff, fltWin );
        float[][] filter = new float[ 4 ][];

        filter[ 0 ] = flt;
        filter[ 1 ] = fltD;
        filter[ 2 ] = new float[ 2 ];
        filter[ 2 ][ 0 ] = fltSmpPerCrossing;
        filter[ 2 ][ 1 ] = gain;

        Filter.resample( src, srcOff, dest, destOff, length, factor, filter );
    }

    /**
     *	Resample data; "band-limited interpolation"
     *
     *	if fltD == null, no linear interpolation is performed (much faster!)
     *
     *	@param	srcOff		allows sub-sample shift!
     *	@param	length		refers to dest! src must have length 'length/factor' (ceil / rounded up)!
     *	@param	factor		dest-smpRate/src-smpRate
     *	@param	filter		Dimension 0: flt (first argument to createAntiAliasFilter), 1: fltD;
     *						2: fltSmpPerCrossing; 3: fltGain (createAAF result)
     */
    public static void resample(float[] src, double srcOff, float[] dest, int destOff, int length,
                                double factor, float[][] filter) {
        double	smpIncr				= 1.0 / factor;
        int		i, fltOffI, srcOffI;
        double	q, r, val, fltIncr, fltOff;
        double	phase				= srcOff;

        float[] flt = filter[ 0 ];
        float[] fltD = filter[ 1 ];
        float	fltSmpPerCrossing	= filter[ 2 ][ 0 ];
        double	gain				= filter[ 2 ][ 1 ];
        double  rsmpGain;
        int		fltLen				= flt.length;
        int		srcLen				= src.length;

        if( smpIncr > 1.0 ) {
            fltIncr		= fltSmpPerCrossing * factor;
            rsmpGain	= gain;
        } else {
            fltIncr		= fltSmpPerCrossing;
            rsmpGain	= gain * smpIncr;
        }

        if( fltD == null ) {	// -------------------- without interpolation --------------------

            for( i = 0; i < length; i++, phase = srcOff + i * smpIncr ) {

                q		= phase % 1.0;
                val		= 0.0;
//				k		= -1;					// src-smpIncr

                srcOffI	= (int) phase;
                fltOff	= q * fltIncr + 0.5f;	// at least we can do this if no longer using interpolation...
                fltOffI	= (int) fltOff;
                while( (fltOffI < fltLen) && (srcOffI >= 0) ) {
                    val	   += (double) src[ srcOffI ] * flt[ fltOffI ];
                    srcOffI--;
                    fltOff += fltIncr;
                    fltOffI	= (int) fltOff;
                }

                srcOffI	= (int) phase + 1;
                fltOff	= (1.0 - q) * fltIncr;
                fltOffI	= (int) fltOff;
                while( (fltOffI < fltLen) && (srcOffI < srcLen) ) {
                    val	   += (double) src[ srcOffI ] * flt[ fltOffI ];
                    srcOffI++;
                    fltOff += fltIncr;
                    fltOffI	= (int) fltOff;
                }

                dest[ destOff++ ] = (float) (val * rsmpGain);
            }

        } else {	// -------------------- linear interpolation --------------------

            for( i = 0; i < length; i++, phase = srcOff + i * smpIncr ) {

                q		= phase % 1.0;
                val		= 0.0;
//				k		= -1;					// src-smpIncr

                srcOffI	= (int) phase;
                fltOff	= q * fltIncr;
                fltOffI	= (int) fltOff;
                while( (fltOffI < fltLen) && (srcOffI >= 0) ) {
                    r		= fltOff % 1.0;		// 0...1 for interpol.
                    val	   += src[ srcOffI ] * (flt[ fltOffI ] + fltD[ fltOffI ] * r);
                    srcOffI--;
                    fltOff += fltIncr;
                    fltOffI	= (int) fltOff;
                }

                srcOffI	= (int) phase + 1;
                fltOff	= (1.0 - q) * fltIncr;
                fltOffI	= (int) fltOff;
                while( (fltOffI < fltLen) && (srcOffI < srcLen) ) {
                    r		= fltOff % 1.0;		// 0...1 for interpol.
                    val	   += src[ srcOffI ] * (flt[ fltOffI ] + fltD[ fltOffI ] * r);
                    srcOffI++;
                    fltOff += fltIncr;
                    fltOffI	= (int) fltOff;
                }

                dest[ destOff++ ] = (float) (val * rsmpGain);
            }
        }
    }

    /**
     *	Calculates energy (sum(x^2))
     */
    public static double calcEnergy(float[] a, int off, int length) {
        double energy = 0.0;
        int stop = off + length;

        while (off < stop) {
            energy += a[off] * a[off++];
        }

        return energy;
    }

// -------- private methods --------

    protected static double calcBesselZero( double x )
    {
        double	d1;
        double	d2	= 1.0;
        double	sum	= 1.0;
        int		n	= 1;

        x /= 2.0;

        do {
            d1	 = x / n;
            n++;
            d2	*= d1*d1;
            sum	+= d2;
        } while( d2 >= sum*1e-21 );	// precise up to 20 digits

        return sum;
    }
}
// class Filter
