/*
 *  OSCRouter.java
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

package de.sciss.eisenkraut.net;

public interface OSCRouter {
    public String oscGetPathComponent();

    public void oscRoute(RoutedOSCMessage rom);

    public void oscAddRouter(OSCRouter subRouter);

    public void oscRemoveRouter(OSCRouter subRouter);
}
