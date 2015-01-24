/*
 *  PrefComboBoxModel.java
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

package de.sciss.eisenkraut.gui;

import javax.swing.ComboBoxModel;

public abstract class PrefComboBoxModel
extends PrefListModel
implements ComboBoxModel
{
	private Object selectedItem = null;
	
	public void setSelectedItem( Object anItem )
	{
		selectedItem = anItem;
	}
	
	public Object getSelectedItem()
	{
		return selectedItem;
	}
}
