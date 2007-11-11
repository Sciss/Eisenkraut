//
//  RandomAccessRequester.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 15.07.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.eisenkraut.render;

import de.sciss.io.Span;

public interface RandomAccessRequester
{
	public Span getNextSpan();
}
