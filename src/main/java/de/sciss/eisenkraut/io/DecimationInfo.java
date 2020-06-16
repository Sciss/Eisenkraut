/*
 *  DecimationInfo.java
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

import de.sciss.io.Span;

/**
 *  A context object for GUI elements
 *  that wish to load sense data at an
 *  economic data rate, i.e. by using
 *  an appropriate subsampled version of
 *  the sense data. This object is returned
 *  by the <code>getBestSubsample</code> method
 *  of <code>MultirateTrackEditor</code>. The
 *  calling instance should read the <code>sublength</code>
 *  field to determine the required buffer size
 *  and then pass this object to the <code>read</code> methods
 *  in <code>MultirateTrackEditor</code>.
 *
 *  TODO: this class could be internal to mte
 */
public class DecimationInfo
{
    /**
     *  Internal index for MultirateTrackEditor
     */
    public final int	idx;
    protected final int	shift;
    protected final int	inlineDecim;
    /**
     *  Time span (in full-rate frames) covered by this subsample
     */
    public final Span	span;
    /**
     *  Length (rounded) of the time span decimated through sub-sampling
     */
    public final long	subLength;
    public final int	model;
    public final int	channels;

    /**
     *  Creates a new <code>DecimationInfo</code>
     *  data structure with the given decimation.
     *
     *  @param  idx			internal index for <code>MultirateTrackEditor</code>
     *  @param  span		the originally covered time span
     *  @param  subLength   the translated span length in decimated
     *						frames (rounded to integer)
     */
    protected DecimationInfo(Span span, long subLength, int channels,
                             int idx, int shift, int inlineDecim, int model )
    {
        this.span			= span;
        this.subLength = subLength;
        this.channels		= channels;
        this.idx			= idx;
        this.shift			= shift;
        this.inlineDecim	= inlineDecim;
        this.model			= model;
    }

    /**
     *  Returns the decimation
     *  rate factor.
     *
     *  @return the factor by which the full rate is decimated,
     *			that is, <code>decimatedRate = fullRate / returnedFactor</code>
     */
    public int getDecimationFactor()
    {
        return( (1<<shift) * inlineDecim );
    }

    public long getTotalLength()
    {
        return( (subLength * inlineDecim) << shift );
    }
}