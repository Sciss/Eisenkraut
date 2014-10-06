/*
 *  ToolListener.java
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