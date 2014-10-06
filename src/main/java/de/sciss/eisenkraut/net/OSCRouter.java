/*
 *  OSCRouter.java
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
 *		19-Jan-06	created
 */

package de.sciss.eisenkraut.net;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 10-Sep-06
 */
public interface OSCRouter
{
	public String oscGetPathComponent();
	public void oscRoute( RoutedOSCMessage rom );
	public void oscAddRouter( OSCRouter subRouter );
	public void oscRemoveRouter( OSCRouter subRouter );
}
