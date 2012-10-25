/*
 *  ToolActionEvent.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *		12-May-05	created from de.sciss.meloncillo.gui.ToolActionEvent
 */

package de.sciss.eisenkraut.gui;

import de.sciss.app.BasicEvent;

/**
 *  This kind of event is fired
 *  from a <code>ToolBar</code> when
 *  the user switched to a different tool.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.56, 05-May-06
 *
 *  @see		ToolBar#addToolActionListener( ToolActionListener )
 *  @see		ToolActionListener
 *  @see		ToolAction
 */
public class ToolActionEvent
extends BasicEvent
{
	// --- ID values ---
	/**
	 *  returned by getID() : the tool was changed
	 */
	public static final int CHANGED		= 0;

	private final ToolAction toolAction;

	/**
	 *  Constructs a new <code>ToolActionEvent</code>
	 *
	 *  @param  source		who originated the action
	 *  @param  ID			<code>CHANGED</code>
	 *  @param  when		system time when the event occured
	 *  @param  toolAction	the new ToolAction to which was switched
	 */
	public ToolActionEvent( Object source, int ID, long when, ToolAction toolAction )
	{
		super( source, ID, when );
	
		this.toolAction = toolAction;
	}
	
	
	/**
	 *  Queries the new tool
	 *
	 *  @return the new tool action of the <code>ToolBar</code>
	 *			or <code>ToolPalette</code>.
	 */
	public ToolAction getToolAction()
	{
		return toolAction;
	}

	public boolean incorporate( BasicEvent oldEvent )
	{
		if( oldEvent instanceof ToolActionEvent &&
			getSource() == oldEvent.getSource() &&
			getID() == oldEvent.getID() ) {
			
			return true;

		} else return false;
	}
}