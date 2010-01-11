/*
 *  ToolListener.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		12-May-05	created from de.sciss.meloncillo.gui.ToolListener
 */

package de.sciss.eisenkraut.gui;

/**
 *  Interface for listening
 *  to switches of the GUI tools
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see		ToolBar#addToolActionListener( ToolActionListener )
 *  @see		ToolActionEvent
 */
public interface ToolActionListener
{
	/**
	 *  Notifies the listener that
	 *  a tool changed occured.
	 *
	 *  @param  e   the event describing
	 *				the tool switch
	 */
	public void toolChanged( ToolActionEvent e );
}