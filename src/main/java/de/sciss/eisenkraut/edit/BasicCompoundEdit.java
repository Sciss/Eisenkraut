/*
 *  BasicSyncCompoundEdit.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.AbstractCompoundEdit;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is 
 *  the most basic extension of the abstract class
 *  which simply puts empty bodies for the abstract methods.
 */
@SuppressWarnings("serial")
public class BasicCompoundEdit
		extends AbstractCompoundEdit {
	private boolean significant = true;

	/**
	 *  Creates a <code>CompoundEdit</code> object, whose Undo/Redo
	 *  actions are synchronized.
	 */
	public BasicCompoundEdit() {
		super();
	}

	/**
	 * Creates a <code>CompoundEdit</code> object with a given name, whose Undo/Redo
	 * actions are synchronized.
	 *
	 * @param    presentationName    text describing the compound edit
	 */
	public BasicCompoundEdit(String presentationName) {
		super(presentationName);
	}

	public boolean isSignificant() {
		return significant && super.isSignificant();
	}

	public void setSignificant(boolean b) {
		significant = b;
	}

	/**
	 * Does nothing
	 */
	protected void undoDone() { /* empty */ }

	/**
	 * Does nothing
	 */
	protected void redoDone() { /* empty */ }

	/**
	 * Does nothing
	 */
	protected void cancelDone() { /* empty */ }
}