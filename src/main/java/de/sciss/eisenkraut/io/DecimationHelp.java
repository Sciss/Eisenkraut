/*
 *  DecimationHelp.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.io;

public class DecimationHelp {

    public final double	rate;
    public final int	shift;
    public final int	factor;
    public final int	roundAdd;
    public final long	mask;

    public DecimationHelp(double fullRate, int shift) {
        this.shift		= shift;
        factor			= 1 << shift;
        this.rate		= fullRate / factor;
        roundAdd		= factor >> 1;
        mask			= -factor;
    }

    /**
     *  Converts a frame length from full rate to
     *  decimated rate and rounds to nearest integer.
     *
     *  @param  full	number of frame at full rate
     *  @return number of frames at this editor's decimated rate
     */
    public long fullRateToSubsample(long full) {
        return ((full + roundAdd) >> shift);
    }
}
