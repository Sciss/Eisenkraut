/*
 *  UndoManager.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.eisenkraut.edit;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

import de.sciss.eisenkraut.session.Session;

public class UndoManager
extends de.sciss.app.UndoManager
{
	public UndoManager( Session doc )
	{
		super( doc );
	}

	protected AbstractAction createUndoAction()
	{
		return new ActionUndoProc();
	}
	
	protected AbstractAction createRedoAction()
	{
		return new ActionRedoProc();
	}
	
	private class ActionUndoProc
	extends ActionUndo
	{
		protected ActionUndoProc() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			if( !((Session) getDocument()).checkProcess() ) return;
			super.actionPerformed( e );
		}
	}

	private class ActionRedoProc
	extends ActionRedo
	{
		protected ActionRedoProc() { /* empty */ }

		public void actionPerformed( ActionEvent e )
		{
			if( !((Session) getDocument()).checkProcess() ) return;
			super.actionPerformed( e );
		}
	}
}
