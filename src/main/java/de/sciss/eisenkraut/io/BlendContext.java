/*
 *  BlendContext.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;

/**
 *  Object describing the
 *  type of cross-fading used
 *  in vector operations. Methods
 *	for calculating the fades are provided.
 */
public class BlendContext
{
    /*
     *  The length of the
     *  blending cross-fade in
     *  sense rate frames
     */
    private final long		left, right;
    private final double[]	eqn			= new double[ 4 ];
//	private final double	yInA, yInB, yInD;
    private final double	yOutA, yOutB, yOutD, yXInA, yXInB, yXInD;
    private final double	wFact;
    private final double	yIn0 = 0.0, yOut0 = 1.0;

    /**
     *  Create a new BlendContext with
     *  the given length
     */
    public BlendContext(long left, long right, Point2D[] ctrlPt) {
        this.left		= left;
        this.right		= right;

        eqn[ 1 ]		=  3 * ctrlPt[0].getX();
        eqn[ 2 ]		= -6 * ctrlPt[0].getX() + 3 * ctrlPt[1].getX();
        eqn[ 3 ]		=  3 * ctrlPt[0].getX() - 3 * ctrlPt[1].getX() + 1;

//		yInD			=  3 * ctrlPt[0].getY() - 3 * ctrlPt[1].getY() + 1;
//		yInA			= -6 * ctrlPt[0].getY() + 3 * ctrlPt[1].getY();
//		yInB			=  3 * ctrlPt[0].getY();

//		y = t^3 (3 * ctrlPt1_y - 3 * ctrlPt2_y + 1) + t^2 (-6 * ctrlPt1_y + 3 * ctrlPt2_y) + t (3 * ctrlPt1_y)
//						D											A							B

        yXInD			=  3 * ctrlPt[1].getY() - 3 * ctrlPt[0].getY() + 1;
        yXInA			= -6 * ctrlPt[1].getY() + 3 * ctrlPt[0].getY();
        yXInB			=  3 * ctrlPt[1].getY();

        yOutD			=  3 * ctrlPt[0].getY() - 3 * ctrlPt[1].getY() - 1;
        yOutA			= -6 * ctrlPt[0].getY() + 3 * ctrlPt[1].getY() + 3;
        yOutB			=  3 * ctrlPt[0].getY() - 3;

        wFact			= 1.0 / (left + right);
    }

/*
        P(t) = B(3,0)*startPt + B(3,1)*ctrlPt1 + B(3,2)*ctrlPt2 + B(3,3)*endPt
          0 <= t <= 1

        B(n,m) = C(n,m) * t^(m) * (1 - t)^(n-m)
        C(n,m) = n! / (m! * (n-m)!)

        C(3,0) = 1; C(3,1) = 3; C(3,2) = 3; C(3,3) = 1

        B(3,0) = C(3,0) * t^0 * (1-t)^3 = (1-t)^3			=  -t^3 + 3t^2 - 3t + 1
        B(3,1) = C(3,1) * t^1 * (1-t)^2 = 3 * t * (1-t)^2	=  3t^3 - 6t^2 + 3t
        B(3,2) = C(3,2) * t^2 * (1-t)^1 = 3 * t^2 * (1-t)	= -3t^3 + 3t^2
        B(3,3) = C(3,3) * t^3 * (1-t)^0 = t^3				=   t^3

        t^3 (3 * ctrlPt1_x - 3 * ctrlPt2_x + 1) + t^2 (-6 * ctrlPt1_x + 3 * ctrlPt2_x) + t (3 * ctrlPt1_x) - x = 0
                        D											A							B			 C

        y = t^3 (3 * ctrlPt1_y - 3 * ctrlPt2_y + 1) + t^2 (-6 * ctrlPt1_y + 3 * ctrlPt2_y) + t (3 * ctrlPt1_y)

        y	= t^3 (-startPt_y + 3 * ctrlPt1_y - 3 * ctrlPt2_y + endPt_y) +
              t^2 (3 * startPt_y - 6 * ctrlPt1_y + 3 * ctrlPt2_y) +
              t   (-3 * startPt_y + 3 * ctrlPt1_y) +
              1   (startPt_y)

        um t aus x auszurechnen:
        eqn		= {c, b, a, d};
        eqn[0]	= -x
        eqn[1]	= 3 * ctrlPt1_x
        eqn[2]	= -6 * ctrlPt1_x + 3 * ctrlPt2_x
        eqn[3]	= 3 * ctrlPt1_x - 3 * ctrlPt2_x + 1

        um y fuer fade-in auszurechnen: (startPt_y := 0, endPt_y := 1)

        y = t^3 (3 * ctrlPt1_y - 3 * ctrlPt2_y + 1) + t^2 (-6 * ctrlPt1_y + 3 * ctrlPt2_y) + t (3 * ctrlPt1_y)
                        D											A							B

        um y fuer fade-out auszurechnen: (startPt_y := 1, endPt_y := 0)

        y = t^3 (3 * ctrlPt1_y - 3 * ctrlPt2_y - 1) + t^2 (-6 * ctrlPt1_y + 3 * ctrlPt2_y + 3) + t (3 * ctrlPt1_y - 3) + startPt_y
                        D											A							B

*/

//	public long getBlendLen()
//	{
//		return pre + post;
//	}

    public long getLen()
    {
        return left + right;
    }

    public long getLeftLen()
    {
        return left;
    }

    public long getRightLen()
    {
        return right;
    }

    /**
     *	@param targetBuf	single channels can be <code>null</code>
     */
    public void fadeIn( long blendOff, float[][] sourceBuf, int sourceOff, float[][] targetBuf, int targetOff, int length )
    {
        fade( blendOff, sourceBuf, sourceOff, targetBuf, targetOff, length, yXInD, yXInB, yXInA, yIn0 );	// yX !!
    }

    public void fadeOut( long blendOff, float[][] sourceBuf, int sourceOff, float[][] targetBuf, int targetOff, int length )
    {
        fade( blendOff, sourceBuf, sourceOff, targetBuf, targetOff, length, yOutD, yOutB, yOutA, yOut0 );
    }

    private void fade( long blendOff, float[][] sourceBuf, int sourceOff,
                       float[][] targetBuf, int targetOff, int length, double yD, double yB, double yA, double y0 )
    {
        final int		numCh	= sourceBuf.length;
        final double	wOff	= blendOff * wFact;
        final double[]	res		= new double[ 3 ];
        double			t, tt;
        int				ch;
        float			w;

        for( int i = 0; i < length; i++, sourceOff++, targetOff++ ) {
            eqn[ 0 ]	= -(wOff + wFact * i);		// C = -x
            CubicCurve2D.solveCubic( eqn, res );
            t			= res[ 0 ];
            if( t < 0.0 || t > 1.0 ) {
                t		= res[ 1 ];
                if( t < 0.0 || t > 1.0 ) {
                    t	= res[ 2 ];
//					if( t < 0.0 || t > 1.0 ) {
//						System.err.println( "oh dear. res[0] = "+res[0]+"; res[1] = "+res[1]+"; res[2] = "+res[2] );
//					}
                }
            }
            tt			= t * t;
            w			= (float) (tt * t * yD + tt * yA + t * yB + y0);

            for( ch = 0; ch < numCh; ch++ ) {
                if( targetBuf[ ch ] != null ) {
                    targetBuf[ ch ][ targetOff ] = sourceBuf[ ch ][ sourceOff ] * w;
                }
            }
        }
    }

    /**
     *	@param	sourceBufA	fading out
     *	@param	sourceBufB	fading in
     */
    public void blend( long blendOff, float[][] sourceBufA, int sourceOffA,
                                      float[][] sourceBufB, int sourceOffB,
                                      float[][] targetBuf, int targetOff, int length )
    {
//System.err.println( "kieka! "+blendLen );
        final int		numCh	= sourceBufA.length;
        final double	wOff	= blendOff * wFact;
        final double[]	res		= new double[ 3 ];
        int				len2;
        double			t, tt, ttt;
        int				i		= 0;
        int				ch;
        float			wIn, wOut;

        // plain A
        len2 = (int) Math.min( length, -blendOff );
        if( len2 > 0 ) {
            for( ch = 0; ch < numCh; ch++ ) {
                if( targetBuf[ ch ] != null ) {
                    System.arraycopy( sourceBufA[ ch ], sourceOffA, targetBuf[ ch ], targetOff, len2 );
                }
            }
            i		   += len2;
            sourceOffA += len2;
            sourceOffB += len2;
            targetOff  += len2;
        }

        // xfade
        len2 = (int) Math.min( length, left + right - blendOff );
        for( ; i < len2; i++, sourceOffA++, sourceOffB++, targetOff++ ) {
            eqn[ 0 ]	= -(wOff + wFact * i);		// C = -x
            CubicCurve2D.solveCubic( eqn, res );
            t			= res[ 0 ];
            if( t < 0.0 || t > 1.0 ) {
                t		= res[ 1 ];
                if( t < 0.0 || t > 1.0 ) {
                    t	= res[ 2 ];
//					if( t < 0.0 || t > 1.0 ) {
//						System.err.println( "oh dear. res[0] = "+res[0]+"; res[1] = "+res[1]+"; res[2] = "+res[2] );
//					}
                }
            }
            tt			= t * t;
            ttt			= tt * t;
            wIn			= (float) (ttt * yXInD + tt * yXInA + t * yXInB + yIn0);
            wOut		= (float) (ttt * yOutD + tt * yOutA + t * yOutB + yOut0);

            for( ch = 0; ch < numCh; ch++ ) {
                if( targetBuf[ ch ] != null ) {
                    targetBuf[ ch ][ targetOff ] = sourceBufB[ ch ][ sourceOffB ] * wIn +
                                                   sourceBufA[ ch ][ sourceOffA ] * wOut;
                }
            }
        }

        // plain B
        len2 = length - i;
        if( len2 > 0 ) {
            for( ch = 0; ch < numCh; ch++ ) {
                if( targetBuf[ ch ] != null ) {
                    System.arraycopy( sourceBufB[ ch ], sourceOffB, targetBuf[ ch ], targetOff, len2 );
                }
            }
        }
    }
}
