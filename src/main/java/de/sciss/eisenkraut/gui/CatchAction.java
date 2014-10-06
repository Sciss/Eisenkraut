/*
 *  CatchAction.java
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
 *		12-May-05	created from de.sciss.meloncillo.gui.CatchAction
 */

package de.sciss.eisenkraut.gui;

import java.awt.event.ActionEvent;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JToggleButton;

import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicPrefChangeManager;

/**
 *	A class implementing the <code>Action</code> interface
 *	which deals with the catch (timeline position) setting. Each instance
 *	generates a toggle button suitable for attaching to a tool bar;
 *	this button reflects the catch preferences settings.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 20-Mar-08
 */
public class CatchAction
extends AbstractAction
implements PreferenceChangeListener
{
	private final JToggleButton b;
	private final Preferences	prefs;

	/**
	 *	Creates a new instance of an action
	 *	that tracks blending changes
	 */
	public CatchAction( Preferences prefs )
	{
		super();
		this.prefs	= prefs;
		b			= new JToggleButton( this );
		GraphicsUtil.setToolIcons( b, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_CATCH ));
		new DynamicAncestorAdapter( new DynamicPrefChangeManager( prefs,
			new String[] { PrefsUtil.KEY_CATCH }, this )).addTo( b );
	}
	
	/**
	 *	Returns the toggle button
	 *	which is connected to this action.
	 *
	 *	@return	a toggle button which is suitable for tool bar display
	 */
	public JToggleButton getButton()
	{
		return b;
	}

	private void updateButtonState()
	{
		b.setSelected( prefs.getBoolean( PrefsUtil.KEY_CATCH, false ));
	}
	
	public void actionPerformed( ActionEvent e )
	{
		prefs.putBoolean( PrefsUtil.KEY_CATCH, b.isSelected() );
	}

	public void preferenceChange( PreferenceChangeEvent e )
	{
		updateButtonState();
	}
}
