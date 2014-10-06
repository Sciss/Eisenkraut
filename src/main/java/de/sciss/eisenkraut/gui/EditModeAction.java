/*
 *  EditModeAction.java
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
