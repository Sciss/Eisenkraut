/*
 *  Reverse.java
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

package de.sciss.eisenkraut.render;

import de.sciss.io.Span;
import de.sciss.timebased.MarkerStake;
import de.sciss.timebased.Stake;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Reverse extends AbstractRenderPlugIn {

    private Span			prTotalSpan;
    private RenderConsumer	prConsumer;

    public int getMarkerPolicy() {
        return POLICY_MODIFY;
    }

    public int getUnselectedAudioPolicy() {
        return POLICY_MODIFY;
    }

    public boolean producerBegin(RenderSource source)
            throws IOException {
        // random read access not necessary any more since the consumer
        // automatically handles random write access!
//		source.context.setOption( RenderContext.KEY_PREF_BLOCK_SIZE, new Integer( BLOCK_SIZE ));
//		source.context.setOption( RenderContext.KEY_RANDOM_ACCESS, this );
        prTotalSpan	= source.context.getTimeSpan();
        // request last block
//		prNextSpan	= new Span( Math.max( prTotalSpan.start, prTotalSpan.stop - BLOCK_SIZE ), prTotalSpan.stop );
        prConsumer	= source.context.getConsumer();

        // flip markers at once
        if (source.validMarkers) {
            final int numMarkers = source.markers.getNumStakes();
            final List<Stake> collNew = new ArrayList<Stake>(numMarkers);
            MarkerStake m;
            for (int i = 0; i < numMarkers; i++) {
                m = (MarkerStake) source.markers.get(i, true);
                collNew.add(m.replaceStart(prTotalSpan.stop - m.pos + prTotalSpan.start));
            }
            source.markers.clear(this);
            source.markers.addAll(this, collNew);
        }

        return prConsumer.consumerBegin(source);
    }

    public boolean producerRender(RenderSource source)
            throws IOException {

        float temp;
        float[] chBuf;

        // reverse each block
        for (int ch = 0; ch < source.numAudioChannels; ch++) {
            if (!source.audioTrackMap[ch]) continue;
            chBuf = source.audioBlockBuf[ch];
            for (int i = source.audioBlockBufOff, j = i + source.audioBlockBufLen - 1; i < j; i++, j--) {
                temp = chBuf[i];
                chBuf[i] = chBuf[j];
                chBuf[j] = temp;
            }
        }

        // pseudo code:
        // blockStart --> totalStop - (blockStart - totalStart) - blockLen
        // blockStart --> totalStop + totalStart - blockStop
        // shift = totalStop + totalStart - blockStop - blockStart
        final long delta = prTotalSpan.stop + prTotalSpan.start - source.blockSpan.stop - source.blockSpan.start;
        source.blockSpan = source.blockSpan.shift(delta);

        return prConsumer.consumerRender(source);
    }

    public String getName() {
        return getResourceString("plugInReverse");
    }
}