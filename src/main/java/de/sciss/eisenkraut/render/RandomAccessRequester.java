/*
 *  RandomAccessRequester.java
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
 *		15-Jul-05   created
 */

package de.sciss.eisenkraut.render;

import de.sciss.io.Span;

/**
 *	@version	0.70, 07-Dec-07
 */
public interface RandomAccessRequester
{
	public Span getNextSpan();
}
