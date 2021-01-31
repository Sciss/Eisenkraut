/*
 *  FadeIn.java
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

package de.sciss.eisenkraut.render;

public class FadeIn
        extends FadePlugIn {

    public FadeIn() {
        super(true);
    }

    public String getName() {
        return getResourceString("plugInFadeIn");
    }
}
