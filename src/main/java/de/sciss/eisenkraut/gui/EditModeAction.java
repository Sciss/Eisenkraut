/*
 *  EditModeAction.java
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
 *		28-Jan-06	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;

import de.sciss.eisenkraut.session.Session;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.65, 28-Jan-06
 */
public class EditModeAction
extends AbstractAction
{
	private final JToggleButton[]	b;
	private final ButtonGroup		bg;
	private final Session			doc;

	/**
	 */
	public EditModeAction( Session doc )
	{
		super();
		this.doc		= doc;

		b				= new JToggleButton[ 3 ];
		bg				= new ButtonGroup();
		for( int i = 0; i < 3; i++ ) {
			b[ i ]		= new JToggleButton( this );
			GraphicsUtil.setToolIcons( b[ i ], GraphicsUtil.createToolIcons( GraphicsUtil.ICON_INSERTMODE + i ));
			bg.add( b[ i ]);
		}
		
		bg.setSelected( b[ doc.getEditMode() ].getModel(), true );
	}
	
	/**
	 */
	public ButtonGroup getButtons()
	{
		return bg;
	}

	public void actionPerformed( ActionEvent e )
	{
		for( int i = 0; i < 3; i++ ) {
			if( e.getSource() == b[ i ]) {
				if( !bg.isSelected( b[ i ].getModel() )) {
					bg.setSelected( b[ i ].getModel(), true );
				}
				doc.setEditMode( i );
				break;
			}
		}
	}
}
