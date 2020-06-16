/*
 *  FadeOut.java
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

// @version	0.56, 15-Sep-05
public class FadeOut
extends FadePlugIn
{
    public FadeOut()
    {
        super( false );
    }

    public String getName()
    {
        return getResourceString( "plugInFadeOut" );
    }
}