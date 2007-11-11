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
		return new actionUndoProcClass();
	}
	
	protected AbstractAction createRedoAction()
	{
		return new actionRedoProcClass();
	}
	
	private class actionUndoProcClass
	extends actionUndoClass
	{
		public void actionPerformed( ActionEvent e )
		{
			if( !((Session) doc).checkProcess() ) return;
			super.actionPerformed( e );
		}
	}

	private class actionRedoProcClass
	extends actionRedoClass
	{
		public void actionPerformed( ActionEvent e )
		{
			if( !((Session) doc).checkProcess() ) return;
			super.actionPerformed( e );
		}
	}
}
