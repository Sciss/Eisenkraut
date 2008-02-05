/*
 *  TimelineToolBar.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		12-May-05	created from de.sciss.meloncillo.timeline.TimelineToolBar
 *		08-Sep-05	selectTool method
 */

package de.sciss.eisenkraut.timeline;

import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;

import de.sciss.app.AbstractApplication;

import de.sciss.eisenkraut.gui.BlendingAction;
import de.sciss.eisenkraut.gui.CatchAction;
import de.sciss.eisenkraut.gui.EditModeAction;
import de.sciss.eisenkraut.gui.ToolAction;
import de.sciss.eisenkraut.gui.ToolBar;
import de.sciss.eisenkraut.session.Session;
import de.sciss.gui.GUIUtil;

import de.sciss.util.Disposable;

/**
 *	A palette of tools for editing
 *	objects in the timline frame. Window
 *	key commands are installed: F1 to F6
 *	for the tools and capslock for toggling
 *	the blending option.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class TimelineToolBar
extends ToolBar
implements Disposable
{
	private final Map					mapToolButtons	= new HashMap();

	private final BlendingAction		actionBlending;
	
	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the timeline frame.
	 */
	public TimelineToolBar( Session doc )
	{
		super( ToolBar.HORIZONTAL );

		final Preferences		prefs = AbstractApplication.getApplication().getUserPrefs();
		final CatchAction		actionCatch;
		final EditModeAction	actionEditMode;
		final AbstractButton	button;
		ToolAction				toolAction;
		JToggleButton			toggle;
		ButtonGroup				bg;
		Enumeration				en;

		actionCatch		= new CatchAction( prefs ); // .node( PrefsUtil.NODE_SHARED ));
		toggle			= actionCatch.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_V, 0 )));
GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_V, 0 ));
//        HelpGlassPane.setHelp( toggle, "ToolCatch" );
        addToggleButton( toggle, 2 );
		addSeparator();

		actionEditMode	= new EditModeAction( doc );
		bg				= actionEditMode.getButtons();
		en				= bg.getElements();
		for( int i = 0; en.hasMoreElements(); i++ ) {
			toggle		= (JToggleButton) en.nextElement();
GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1 + i, 0 ));
			addToggleButton( toggle, 3 );
		}
		addSeparator();

		toolAction		= new ToolAction( ToolAction.POINTER );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolPointer" );
  		addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
        
//		toolAction		= new ToolAction( ToolAction.LINE );
//        toggle			= new JToggleButton( toolAction );
//		toolAction.setIcons( toggle );
//		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F6, 0 ));
////        HelpGlassPane.setHelp( toggle, "TimelineToolLine" );
//toolAction.setEnabled( false );	// XXX not yet implemented
//  		addToggleButton( toggle, 0 );
//		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
//
//		toolAction		= new ToolAction( ToolAction.PENCIL );
//        toggle			= new JToggleButton( toolAction );
//		toolAction.setIcons( toggle );
//		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F7, 0 ));
////        HelpGlassPane.setHelp( toggle, "TimelineToolPencil" );
//toolAction.setEnabled( false );	// XXX not yet implemented
//  		addToggleButton( toggle, 0 );
//		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
      
		toolAction		= new ToolAction( ToolAction.ZOOM );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F8, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolZoom" );
  		addToggleButton( toggle, 0 );
		mapToolButtons.put( new Integer( toolAction.getID() ), toggle );
      
		addSeparator();
		actionBlending  = doc.getBlendingAction();
		button			= actionBlending.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 )));
GUIUtil.createKeyAction( button, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 ));
//        HelpGlassPane.setHelp( toggle, "ToolBlending" );
//        addToggleButton( toggle, 1 );

//final JComboBox ggCombo = new JComboBox() {
//	public void setBackground( Color c )
//	{
//		setOpaque( (c != null) && (c.getAlpha() == 0xFF) );
//		super.setBackground( c );
//	}
//	
//	public void paintComponent( Graphics g )
//	{
////		final Color bg = getBackground();
//		final Color bg = Color.red;
////		getParent().paint( g );
////		((JComponent) getParent()).paintComponent( g );
//		if( (bg != null) && (bg.getAlpha() > 0) ) {
//			g.setColor( bg );
//			g.fillRect( 0, 0, getWidth(), getHeight() );
//		}
//		super.paintComponent( g );
//	}
//};
//final JComboBox ggCombo = new JComboBox();

		add( actionBlending.getComboBox() );

		
//		final MultiStateButton ggBlendHisto = new MultiStateButton();
//		ggBlendHisto.setNumColumns( 3 );
////		ggBlendHisto.addItem( "V", Color.black, new Color( 0xA3, 0xB6, 0xCC ));	// Hue: 0.5952 = graphite
//		ggBlendHisto.addItem( "V", Color.black, new Color( 0xAD, 0xBA, 0xCC ));	// Hue: 0.595 = graphite
//		ggBlendHisto.setFocusable( false );
//		add( ggBlendHisto );
	}
	
//	private void updateRecentBlends()
//	{
//		System.err.println( "updateRecentBlends" );
//		
//		final Preferences	cPrefs;
//		Preferences			prefs;
//		
//		ggBlend.removeAllItems();
//		try {
//			cPrefs = actionBlending.getRecentPreferences();
//			final String[] names = cPrefs.childrenNames();
//			for( int j = 0; j < names.length; j++ ) {
//				prefs = cPrefs.node( names[ j ]);
//System.err.println( " ... add " + prefs.get( BlendingAction.KEY_DURATION, null ));
//				ggBlend.addItem( prefs.get( BlendingAction.KEY_DURATION, null ));
//			}
//		}
//		catch( BackingStoreException e1 ) {
//			System.err.println( e1 );
//		}
//	}
	
	public void selectTool( int toolID )
	{
		final AbstractButton b = (AbstractButton) mapToolButtons.get( new Integer( toolID ));
		if( b != null ) b.doClick();
	}
	
	public void dispose()
	{
	
	}
}