/*
 *  Invert.java
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

package de.sciss.eisenkraut.render;

import java.io.IOException;

public class Invert
		extends AbstractRenderPlugIn {

	public boolean producerRender(RenderSource source)
			throws IOException {
		for (int ch = 0; ch < source.numAudioChannels; ch++) {
			if (!source.audioTrackMap[ch]) continue;
			for (int i = 0, j = source.audioBlockBufOff; i < source.audioBlockBufLen; i++, j++) {
				source.audioBlockBuf[ch][j] *= -1;
			}
		}
		return super.producerRender(source);
	}

	public String getName() {
		return getResourceString("plugInInvert");
	}
}
