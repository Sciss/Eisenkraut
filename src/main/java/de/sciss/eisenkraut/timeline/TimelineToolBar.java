/*
 *  TimelineToolBar.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
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
import javax.swing.SwingConstants;

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
 *	objects in the timeline frame. Window
 *	key commands are installed: F1 to F6
 *	for the tools and caps lock for toggling
 *	the blending option.
 */
@SuppressWarnings("serial")
public class TimelineToolBar
		extends ToolBar
		implements Disposable {

	private final Map<Integer, JToggleButton> mapToolButtons	= new HashMap<Integer, JToggleButton>();
	
	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the timeline frame.
	 */
	public TimelineToolBar( Session doc )
	{
		super( SwingConstants.HORIZONTAL );

		final Preferences		prefs = AbstractApplication.getApplication().getUserPrefs();
		final CatchAction		actionCatch;
		final EditModeAction	actionEditMode;
		final AbstractButton	button;
		final BlendingAction	actionBlending;
		ToolAction				toolAction;
		JToggleButton			toggle;
		ButtonGroup				bg;
		Enumeration<AbstractButton> en;

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
		mapToolButtons.put(toolAction.getID(), toggle );
        
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
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F6, 0 ));
//        HelpGlassPane.setHelp( toggle, "TimelineToolZoom" );
  		addToggleButton( toggle, 0 );
		mapToolButtons.put(toolAction.getID(), toggle );
      
		addSeparator();
		actionBlending  = doc.getBlendingAction();
		button			= actionBlending.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 )));
GUIUtil.createKeyAction( button, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 ));

// ... DOESN'T WORK
//		try {
//			final Robot r = new Robot();
//			button.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put( KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 ), "shortcut" );
//			button.getActionMap().put( "shortcut", new DoClickAction( button ) {
//				public void validActionPerformed( ActionEvent e )
//				{
//					super.validActionPerformed( e );
//					r.keyRelease( KeyEvent.VK_CAPS_LOCK );
//					r.keyPress( KeyEvent.VK_CAPS_LOCK );
//					r.keyRelease( KeyEvent.VK_CAPS_LOCK );
//				}
//			});
//		}
//		catch( AWTException e1 ) { /* ignored */ }

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
		final AbstractButton b = mapToolButtons.get(toolID);
		if( b != null ) b.doClick();
	}
	
	public void dispose()
	{
		/* empty */ 
	}
}