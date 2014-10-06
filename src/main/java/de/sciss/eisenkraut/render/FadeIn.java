/*
 *  FadeIn.java
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
 *		14-Jul-05	created
 */

package de.sciss.eisenkraut.render;

// @version	0.56, 15-Sep-05
public class FadeIn
extends FadePlugIn
{
	public FadeIn()
	{
		super( true );
	}

	public String getName()
	{
		return getResourceString( "plugInFadeIn" );
	}
}
