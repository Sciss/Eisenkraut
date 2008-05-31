/*
 *  MenuFactory.java
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
 *		25-Jan-05	created from de.sciss.meloncillo.gui.MenuFactory
 *		02-Aug-05	confirms to new document handler
 *		15-Sep-05	openDocument checks if file is already open
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioStake;
import de.sciss.eisenkraut.io.PrefCacheManager;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.BooleanPrefsMenuAction;
import de.sciss.gui.IntPrefsMenuAction;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuCheckItem;
import de.sciss.gui.MenuGroup;
import de.sciss.gui.MenuItem;
import de.sciss.gui.MenuRadioGroup;
import de.sciss.gui.MenuRadioItem;
import de.sciss.gui.MenuSeparator;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.AudioFileFormatPane;
import de.sciss.util.Flag;
import de.sciss.util.Param;

import de.sciss.jcollider.Server;

/**
 *  <code>JMenu</code>s cannot be added to more than
 *  one frame. Since on MacOS there's one
 *  global menu for all the application windows
 *  we need to 'duplicate' a menu prototype.
 *  Synchronizing all menus is accomplished
 *  by using the same action objects for all
 *  menu copies. However when items are added
 *  or removed, synchronization needs to be
 *  performed manually. That's the point about
 *  this class.
 *  <p>
 *  There can be only one instance of <code>MenuFactory</code>
 *  for the application, and that will be created by the
 *  <code>Main</code> class.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 31-May-08
 *
 *  @see	de.sciss.eisenkraut.Main#menuFactory
 */
public class MenuFactory
extends BasicMenuFactory
{
	// ---- misc actions ----
	private ActionOpen				actionOpen;
	private ActionOpenMM			actionOpenMM;
	private ActionNewEmpty			actionNewEmpty;

//	private final List					collGlobalKeyCmd	= new ArrayList();
	
//	private static final String			CLIENT_BG	= "de.sciss.gui.BG";	// radio button group

	/**
	 *  The constructor is called only once by
	 *  the <code>Main</code> class and will create a prototype
	 *  main menu from which all copies are
	 *  derived.
	 *
	 *  @param  root	application root
	 */
	public MenuFactory( BasicApplication app )
	{
		super( app );
		createActions();
	}
	
	public ProcessingThread closeAll( boolean force, Flag confirmed )
	{
		final de.sciss.app.DocumentHandler	dh	= AbstractApplication.getApplication().getDocumentHandler();
		Session								doc;
		ProcessingThread					pt;

		while( dh.getDocumentCount() > 0 ) {
			doc	= (Session) dh.getDocument( 0 );
if( doc.getFrame() == null ) {
	System.err.println( "Yukk, no doc frame for "+doc.getDisplayDescr().file );
	try {
		Thread.sleep( 4000 );
	} catch( InterruptedException e1 ) { /* ignore */ }
	confirmed.set( true );
	return null;
}
			pt	= doc.getFrame().closeDocument( force, confirmed );
			if( pt == null ) {
				if( !confirmed.isSet() ) return null;
			} else {
				return pt;
			}
		}
		confirmed.set( true );
		return null;
	}

	/**
	 *  Sets all JMenuBars enabled or disabled.
	 *  When time taking asynchronous processing
	 *  is done, like loading a session or bouncing
	 *  it to disk, the menus need to be disabled
	 *  to prevent the user from accidentally invoking
	 *  menu actions that can cause deadlocks if they
	 *  try to gain access to blocked doors. This
	 *  method traverses the list of known frames and
	 *  sets each frame's menu bar enabled or disabled.
	 *
	 *  @param  enabled		<code>true</code> to enable
	 *						all menu bars, <code>false</code>
	 *						to disable them.
	 *  @synchronization	must be called in the event thread
	 */
//	public void setMenuBarsEnabled( boolean enabled )
//	{
//		MenuHost	host;
//		JMenuBar	mb;
//	
//		for( int i = 0; i < collMenuHosts.size(); i++ ) {
//			host	= (MenuHost) collMenuHosts.get( i );
//			mb		= host.who.getJMenuBar();
//			if( mb != null ) mb.setEnabled( enabled );
//		}
//	}

//	private static int uniqueNumber = 0;	// increased by addGlobalKeyCommand()
	/**
	 *  Adds an action object invisibly to all
	 *  menu bars, enabling its keyboard shortcut
	 *  to be accessed no matter what window
	 *  has the focus.
	 *
	 *  @param  a   the <code>Action</code> whose
	 *				accelerator key should be globally
	 *				accessible. The action
	 *				is stored in the input and action map of each
	 *				registered frame's root pane, thus being
	 *				independant of calls to <code>setMenuBarsEnabled/code>.
	 *
	 *  @throws java.lang.IllegalArgumentException  if the action does
	 *												not have an associated
	 *												accelerator key
	 *
	 *  @see  javax.swing.Action#ACCELERATOR_KEY
	 *  @synchronization	must be called in the event thread
	 */
//	public void addGlobalKeyCommand( Action a )
//	{
//System.err.println( "addGlobalKeyCommand : NOT YET FULLY WORKING" );
//		final KeyStroke		acc		= (KeyStroke) a.getValue( Action.ACCELERATOR_KEY );
//		final String		entry;
//		MenuHost			host;
////		JRootPane			rp;
//		InputMap			imap;
//		ActionMap			amap;
//		
//		if( acc == null ) throw new IllegalArgumentException();
//		
//		entry = "key" + String.valueOf( uniqueNumber++ );
//		a.putValue( Action.NAME, entry );
//
//		for( int i = 0; i < collMenuHosts.size(); i++ ) {
//			host	= (MenuHost) collMenuHosts.get( i );
//			imap	= host.who.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
////			rp		= host.who.getRootPane();
////			imap	= rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
////			amap	= rp.getActionMap();
//			amap	= host.who.getActionMap();
//			imap.put( acc, entry );
//			amap.put( entry, a );
//		}
//		collGlobalKeyCmd.add( a );
//	}

	private void createActions()
	{
		// --- file menu ---
		actionNewEmpty	= new ActionNewEmpty( getResourceString( "menuNewEmpty" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_N, MENU_SHORTCUT ));
		actionOpen		= new ActionOpen(  getResourceString( "menuOpen" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_O, MENU_SHORTCUT ));
		actionOpenMM	= new ActionOpenMM( getResourceString( "menuOpenMM" ),
												KeyStroke.getKeyStroke( KeyEvent.VK_O, MENU_SHORTCUT + InputEvent.SHIFT_MASK ));
	}
	
	// @todo	this should eventually read the tree from an xml file
	protected void addMenuItems()
	{
		final Preferences		prefs = getApplication().getUserPrefs();
		MenuGroup				mg, smg;
		MenuCheckItem			mci;
		MenuRadioGroup			rg;
//		Action					a;
		BooleanPrefsMenuAction	ba;
		IntPrefsMenuAction		ia;
		int						i;
		
		// Ctrl on Mac / Ctrl+Alt on PC
		final int myCtrl = MENU_SHORTCUT == InputEvent.CTRL_MASK ? InputEvent.CTRL_MASK | InputEvent.ALT_MASK : InputEvent.CTRL_MASK;

		// --- file menu ---
		
		mg	= (MenuGroup) get( "file" );
		smg = new MenuGroup( "new", getResourceString( "menuNew" ));
		smg.add( new MenuItem( "empty", actionNewEmpty ));
		smg.add( new MenuItem( "fromSelection", getResourceString( "menuNewFromSelection" )));
		mg.add( smg, 0 );
		i	= mg.indexOf( "open" );
		mg.add( new MenuItem( "openMultipleMono", actionOpenMM ), i + 1 );
		i	= mg.indexOf( "closeAll" );
		mg.add( new MenuSeparator(), i + 3 );
		i = mg.indexOf( "saveCopyAs" );
		mg.add( new MenuItem( "saveSelectionAs", getResourceString( "menuSaveSelectionAs" )), i + 1 );

		// --- timeline menu ---
		i	= indexOf( "edit" );
		mg	= new MenuGroup( "timeline", getResourceString( "menuTimeline" ));
		mg.add( new MenuItem( "trimToSelection", getResourceString( "menuTrimToSelection" ),
							  KeyStroke.getKeyStroke( KeyEvent.VK_F5, MENU_SHORTCUT )));

		mg.add( new MenuItem( "insertSilence", getResourceString( "menuInsertSilence" ),
							  KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + InputEvent.SHIFT_MASK )));
		mg.add( new MenuItem( "insertRecording", getResourceString( "menuInsertRec" )));
		add( mg, i + 1 );

		// --- process menu ---
		mg  = new MenuGroup( "process", getResourceString( "menuProcess" ));
		mg.add( new MenuItem( "again", getResourceString( "menuProcessAgain" ), KeyStroke.getKeyStroke( KeyEvent.VK_F, MENU_SHORTCUT )));
		mg.addSeparator();
		smg  = new MenuGroup( "fscape", getResourceString( "menuFScape" ));
		smg.add( new MenuItem( "needlehole", getResourceString( "menuFScNeedlehole" )));
		mg.add( smg );
		smg = new MenuGroup( "sc", getResourceString( "menuSuperCollider" ));
		mg.add( smg );
		mg.addSeparator();
		mg.add( new MenuItem( "fadeIn", getResourceString( "menuFadeIn" ), KeyStroke.getKeyStroke( KeyEvent.VK_I, myCtrl )));
		mg.add( new MenuItem( "fadeOut", getResourceString( "menuFadeOut" ), KeyStroke.getKeyStroke( KeyEvent.VK_O, myCtrl )));
		mg.add( new MenuItem( "gain", getResourceString( "menuGain" ), KeyStroke.getKeyStroke( KeyEvent.VK_N, myCtrl )));
		mg.add( new MenuItem( "invert", getResourceString( "menuInvert" )));
//		mg.add( new MenuItem( "mix", getResourceString( "menuMix" )));
		mg.add( new MenuItem( "reverse", getResourceString( "menuReverse" )));
		mg.add( new MenuItem( "rotateChannels", getResourceString( "menuRotateChannels" )));
		add( mg, i + 2 );

		// --- operation menu ---
		mg			= new MenuGroup( "operation", getResourceString( "menuOperation" ));
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuInsertionFollowsPlay" ), null );
		mci			= new MenuCheckItem( "insertionFollowsPlay", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_INSERTIONFOLLOWSPLAY );
		mg.add( mci );
		add( mg, i + 3 );

		// --- view menu ---
		mg			= new MenuGroup( "view", getResourceString( "menuView" ));
		smg			= new MenuGroup( "timeUnits", getResourceString( "menuTimeUnits" ));
		ia			= new IntPrefsMenuAction( getResourceString( "menuTimeUnitsSamples" ), null, PrefsUtil.TIME_SAMPLES );
		rg			= new MenuRadioGroup();
		smg.add( new MenuRadioItem( rg, "samples", ia ));	// crucial reihenfolge : erst item erzeugen, dann gruppe setzen, dann prefs
		ia.setRadioGroup( rg );
		ia.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
		ia			= new IntPrefsMenuAction( getResourceString( "menuTimeUnitsMinSecs" ), null, PrefsUtil.TIME_MINSECS );
		smg.add( new MenuRadioItem( rg, "minSecs", ia ));
		ia.setRadioGroup( rg );
		ia.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
		mg.add( smg );

		smg			= new MenuGroup( "vertscale", getResourceString( "menuVertScale" ));
		ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleAmpLin" ), null, PrefsUtil.VSCALE_AMP_LIN );
		rg			= new MenuRadioGroup();
		smg.add( new MenuRadioItem( rg, "amplin", ia ));	// crucial reihenfolge : erst item erzeugen, dann gruppe setzen, dann prefs
		ia.setRadioGroup( rg );
		ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
		ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleAmpLog" ), null, PrefsUtil.VSCALE_AMP_LOG );
		smg.add( new MenuRadioItem( rg, "amplog", ia ));
		ia.setRadioGroup( rg );
		ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
		ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleFreqSpect" ), null, PrefsUtil.VSCALE_FREQ_SPECT );
		smg.add( new MenuRadioItem( rg, "freqspect", ia ));
		ia.setRadioGroup( rg );
		ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
		mg.add( smg );

		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewNullLinie" ), null );
		mci			= new MenuCheckItem( "nullLinie", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWNULLLINIE );
		mg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewVerticalRulers" ), null );
		mci			= new MenuCheckItem( "verticalRulers", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWVERTICALRULERS );
		mg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewChanMeters" ), null );
		mci			= new MenuCheckItem( "channelMeters", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWCHANMETERS );
		mg.add( mci );
		ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewMarkers" ), null );
		mci			= new MenuCheckItem( "markers", ba );
		ba.setCheckItem( mci );
		ba.setPreferences( prefs, PrefsUtil.KEY_VIEWMARKERS );
		mg.add( mci );
		add( mg, i + 4 );

		// --- window menu ---
//		mWindowRadioGroup = new MenuRadioGroup();
//		mgWindow = new MenuGroup( "window", getResourceString( "menuWindow" ));
		mg	= (MenuGroup) get( "window" );
		mg.add( new MenuItem( "ioSetup", new ActionIOSetup( getResourceString( "frameIOSetup" ), null )), 0 );
		mg.add( new MenuSeparator(), 1 );
		mg.add( new MenuItem( "main", new ActionShowWindow( getResourceString( "frameMain" ), null, Main.COMP_MAIN )), 2 );
		mg.add( new MenuItem( "observer", new ActionObserver( getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD3, MENU_SHORTCUT ))), 3 );
		mg.add( new MenuItem( "ctrlRoom", new ActionCtrlRoom( getResourceString( "paletteCtrlRoom" ), KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD2, MENU_SHORTCUT ))), 4 );
//		mg.add( new MenuSeparator(), 5 );
//		mgWindow.add( new MenuItem( "collect", ((WindowHandler) root.getWindowHandler()).getCollectAction() ));
//		mgWindow.addSeparator();
//		add( mgWindow );

		// --- debug menu ---
		mg   = new MenuGroup( "debug", "Debug" );
		mg.add( new MenuItem( "dumpPrefs", PrefsUtil.getDebugDumpAction() ));
		mg.add( new MenuItem( "dumpRegions", "Dump Region Structure" ));
		mg.add( new MenuItem( "verifyRegions", "Verify Regions Consistency" ));
		mg.add( new MenuItem( "dumpCache", PrefCacheManager.getInstance().getDebugDumpAction() ));
		mg.add( new MenuItem( "dumpAudioStakes", AudioStake.getDebugDumpAction() ));
		mg.add( new MenuItem( "dumpNodeTree", SuperColliderClient.getInstance().getDebugNodeTreeAction() ));
		mg.add( new MenuItem( "dumpKillAll", SuperColliderClient.getInstance().getDebugKillAllAction() ));
		i	= indexOf( "help" );
		add( mg, i );

//		// --- help menu ---
//		mg	= new MenuGroup( "help", getResourceString( "menuHelp" ));
//		mg.add( new MenuItem( "manual", new actionURLViewerClass( getResourceString( "menuHelpManual" ), null, "index", false )));
//		mg.add( new MenuItem( "shortcuts", new actionURLViewerClass( getResourceString( "menuHelpShortcuts" ), null, "Shortcuts", false )));
//		mg.addSeparator();
//		mg.add( new MenuItem( "website", new actionURLViewerClass( getResourceString( "menuHelpWebsite" ), null, getResourceString( "appURL" ), true )));
//		a = new actionAboutClass( getResourceString( "menuAbout" ), null );
//		if( AboutJMenuItem.isAutomaticallyPresent() ) {
//			root.getAboutJMenuItem().setAction( a );
//		} else {
//			mg.addSeparator();
//			mg.add( new MenuItem( "about", a ));
//		}
//
//		add( mg );
	}
	
	public void showPreferences()
	{
		PrefsFrame prefsFrame = (PrefsFrame) getApplication().getComponent( Main.COMP_PREFS );
	
		if( prefsFrame == null ) {
			prefsFrame = new PrefsFrame();
		}
		prefsFrame.setVisible( true );
		prefsFrame.toFront();
	}
	
	protected Action getOpenAction()
	{
		return actionOpen;
	}

	protected ActionOpenRecent createOpenRecentAction( String name, File path )
	{
		return new ActionEisKOpenRecent( name, path );
	}

	public void openDocument( File f )
	{
		actionOpen.perform( f );
	}

	public void openDocument( File[] fs )
	{
		actionOpenMM.perform( fs );
	}

	public Session newDocument( AudioFileDescr afd )
	{
		return actionNewEmpty.perform( afd );
	}

	public void addSCPlugIn( Action a, String[] hierarchy )
	{
System.err.println( "addSCPlugIn : NOT YET WORKING" );
//		final JMenuItem mi = new JMenuItem( a );
////		sma.setProtoType( rbmi );
////		rbmi.putClientProperty( CLIENT_BG, CLIENT_BG + "window" );
//		addMenuItem( mSuperCollider, mi );	// XXX traverse hierarchy
	}

	public void removeSCPlugIn( Action a )
	{
System.err.println( "removeSCPlugIn : NOT YET WORKING" );
//		removeMenuItem( mSuperCollider, a );
	}
	
	protected Session findDocumentForPath( File f )
	{
		final de.sciss.app.DocumentHandler	dh	= AbstractApplication.getApplication().getDocumentHandler();
		Session								doc;
		AudioFileDescr[]					afds;
	
		for( int i = 0; i < dh.getDocumentCount(); i++ ) {
			doc		= (Session) dh.getDocument( i );
			afds	= doc.getDescr();
			for( int j = 0; j < afds.length; j++ ) {
				if( (afds[ j ].file != null) && afds[ j ].file.equals( f )) {
					return doc;
				}
			}
		}
		return null;
	}

// ---------------- Action objects for file (session) operations ---------------- 

	// action for the New-Empty Document menu item
	private class ActionNewEmpty
	extends MenuAction
	{
		private JPanel				p	= null;
		private AudioFileFormatPane	affp;
	
		protected ActionNewEmpty( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		public void actionPerformed( ActionEvent e )
		{
			final AudioFileDescr afd = query();
			if( afd != null ) {
//				// XXX there is a bug with
//				// FloatingPaletteHandler
//				// causing infinite mutual window
//				// refresh if we open the new window
//				// straight away. The bug disappears
//				// if we defer the window opening...
//				EventQueue.invokeLater( new Runnable() {
//					public void run()
//					{
						perform( afd );
//					}
//				});
			}
		}
		
		private AudioFileDescr query()
		{
			final AudioFileDescr		afd			= new AudioFileDescr();
//			final JOptionPane			dlg;
			final String[]				queryOptions = { getResourceString( "buttonCreate" ),
														 getResourceString( "buttonCancel" )};
			final int					result;
//			final Object				result;
//			final Component				c			= ((AbstractWindow) root.getComponent( Main.COMP_MAIN )).getWindow();
			final Server.Status			status;
			final double				sampleRate;
			final Param					param;
			final Preferences			audioPrefs;

			if( p == null ) {
				affp		= new AudioFileFormatPane( AudioFileFormatPane.NEW_FILE_FLAGS );
				p			= new JPanel( new BorderLayout() );
				p.add( affp, BorderLayout.NORTH );
				AbstractWindowHandler.setDeepFont( affp );
			}

			status		= SuperColliderClient.getInstance().getStatus();
			if( status != null ) {
				sampleRate	= status.sampleRate;
			} else {
				audioPrefs	= getApplication().getUserPrefs().node( PrefsUtil.NODE_AUDIO );
				param		= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_AUDIORATE, null );
				if( param != null ) {
					sampleRate = param.val;
				} else {
					sampleRate = 0.0;
				}
			}
			
//			System.out.println( "sampleRate " + sampleRate );
			
			if( sampleRate != 0.0 ) {
				affp.toDescr( afd );
				afd.rate = sampleRate;
				affp.fromDescr( afd );
			}
				
//			result		= JOptionPane.showOptionDialog( null, p, getValue( NAME ).toString(),
//					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
//					null, queryOptions, queryOptions[ 0 ]);
			
			//  Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue)
			// (Object message, int messageType, int optionType, Icon icon, Object[] options, Object initialValue) 
			final JOptionPane op = new JOptionPane( p, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, queryOptions, queryOptions[ 0 ]);
//		    final JDialog dlg = op.createDialog( null, getValue( NAME ).toString() );
//		    dlg.show();
//		    result = op.getValue();
			result = BasicWindowHandler.showDialog( op, null, getValue( NAME ).toString() );

//			if( queryOptions[ 0 ].equals( result )) {}
			if( result == 0 ) {
				affp.toDescr( afd );
				return afd;
			} else {
				return null;
			}
		}
		
		protected Session perform( AudioFileDescr afd )
		{
			final Session doc;

			try {
				doc = Session.newEmpty( afd );
				AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
				doc.createFrame();
				return doc;
			}
			catch( IOException e1 ) {	// should never happen
				BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
				return null;
			}
		}
	}
	
	// action for the Open-Session menu item
	private class ActionOpen
	extends MenuAction
	{
//		private String text;
	
		protected ActionOpen( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
			
//			this.text = text;
		}
		
		/*
		 *  Open a Session. If the current Session
		 *  contains unsaved changes, the user is prompted
		 *  to confirm. A file chooser will pop up for
		 *  the user to select the session to open.
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f = queryFile();
			if( f != null ) perform( f );
		}

		private File queryFile()
		{
			final FileDialog		fDlg;
			final String			strFile, strDir;
			final AbstractWindow	w		= (AbstractWindow) getApplication().getComponent( Main.COMP_MAIN );
			final Frame				frame	= (w.getWindow() instanceof Frame) ? (Frame) w.getWindow() : null;
			final Preferences		prefs	= getApplication().getUserPrefs();

//System.err.println( "frame : "+frame );

			fDlg	= new FileDialog( frame, getResourceString( "fileDlgOpen" ), FileDialog.LOAD );
//			fDlg.setFilenameFilter( doc );
			fDlg.setDirectory( prefs.get( PrefsUtil.KEY_FILEOPENDIR, System.getProperty( "user.home" )));
			// fDlg.setFile();
			fDlg.setVisible( true );
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled
			
			// save dir prefs
			prefs.put( PrefsUtil.KEY_FILEOPENDIR, strDir );

			return( new File( strDir, strFile ));
		}
		
		/**
		 *  Loads a new document file.
		 *  a <code>ProcessingThread</code>
		 *  started which loads the new session.
		 *
		 *  @param  path	the file of the document to be loaded
		 *  
		 *  @synchronization	this method must be called in event thread
		 */
		protected void perform( File path )
		{
			Session	doc;
			
			// check if the document is already open
			doc = findDocumentForPath( path );
			if( doc != null ) {
				doc.getFrame().setVisible( true );
				doc.getFrame().toFront();
				return;
			}

			try {
				doc		= Session.newFrom( path );
				addRecent( doc.getDisplayDescr().file );
				AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
				doc.createFrame();	// must be performed after the doc was added
			}
			catch( IOException e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
			}
		}
	}
	
	// action for the Open-Multiple-Mono menu item
	private class ActionOpenMM
	extends MenuAction
	{
		protected ActionOpenMM( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			File[] fs = queryFiles();
			if( fs != null ) {
				if( fs.length == 0 ) {
					final JOptionPane op = new JOptionPane( getResourceString( "errFileSelectionEmpty" ), JOptionPane.ERROR_MESSAGE );
					BasicWindowHandler.showDialog( op, null, getValue( NAME ).toString() );
					return;
				}
				perform( fs );
			}
		}

		private File[] queryFiles()
		{
			final JFileChooser	fDlg	= new JFileChooser();
			final int			result;
			final Component		c		= ((AbstractWindow) getApplication().getComponent( Main.COMP_MAIN )).getWindow();
			final Preferences	prefs	= getApplication().getUserPrefs();
			final File[]		files;

			fDlg.setMultiSelectionEnabled( true );
			fDlg.setDialogTitle( getValue( Action.NAME ).toString() );
			fDlg.setCurrentDirectory( new File( prefs.get( PrefsUtil.KEY_FILEOPENDIR, System.getProperty( "user.home" ))));
			result	= fDlg.showOpenDialog( c );
			
			if( result == JFileChooser.APPROVE_OPTION ) {
				files = fDlg.getSelectedFiles();
				// save dir prefs
				if( files.length > 0 ) {
					prefs.put( PrefsUtil.KEY_FILEOPENDIR, files[ 0 ].getParent() );
				}
				return files;
			} else {
				return null;
			}
		}
		
		/**
		 *  Loads a new document file.
		 *  a <code>ProcessingThread</code>
		 *  started which loads the new session.
		 *
		 *  @param  path	the file of the document to be loaded
		 *  
		 *  @synchronization	this method must be called in event thread
		 */
		protected void perform( File[] paths )
		{
			if( paths.length == 0 ) return;
		
			Session		doc;
//			File		f;
			
			// check if the document is already open
			for( int j = 0; j < paths.length; j++ ) {
				doc = findDocumentForPath( paths[ j ]);
				if( doc != null ) {
					doc.getFrame().setVisible( true );
					doc.getFrame().toFront();
					return;
				}
			}
		
			try {
				doc	= Session.newFrom( paths );
				addRecent( doc.getDisplayDescr().file );
				AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
				doc.createFrame();	// must be performed after the doc was added
			}
			catch( IOException e1 ) {
				BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
			}
		}
	}

	// action for the Open-Recent menu
	private class ActionEisKOpenRecent
	extends ActionOpenRecent
	{
		private File[]	paths;

		// new action with path set to null
		protected ActionEisKOpenRecent( String text, File path )
		{
			super( text, path );
		}
		
		// set the path of the action. this
		// is the file that will be loaded
		// if the action is performed
		protected void setPath( File path )
		{
			paths			= new File[] { path };
			boolean enable	= false;
			try {
				if( path == null ) return;
				if( path.isFile() ) {
					enable	= true;
					return;
				}
				
				final String			name		= path.getName();
				final int				idxOpenBr	= name.indexOf( '[' );
				final int				idxCloseBr	= name.indexOf( ']', idxOpenBr + 1 );
//				System.out.println( "for '" + name + "' idxOpenBr = " + idxOpenBr + "; idxCloseBr = " + idxCloseBr );
				if( (idxOpenBr < 0) || ((idxOpenBr + 1) >= (idxCloseBr - 1)) ) return;
				
				final File				parent		= path.getParentFile();
				final String			pre			= name.substring( 0, idxOpenBr );
				final String			post		= name.substring( idxCloseBr + 1 );
				final StringTokenizer	tok			= new StringTokenizer(
					name.substring( idxOpenBr + 1, idxCloseBr ), "," );
				paths	= new File[ tok.countTokens() ];
				enable	= true;
				for( int i = 0; i < paths.length; i++ ) {
					paths[ i ] = new File( parent, pre + tok.nextToken() + post );
//					System.out.println( "testing path: '" + paths[ i ].getAbsolutePath() + "'" );
					enable   &= paths[ i ].isFile();
				}
			}
			finally {
				setEnabled( enable );
			}
		}
		
		/**
		 *  If a path was set for the
		 *  action and the user confirms
		 *  an intermitting confirm-unsaved-changes
		 *  dialog, the new session will be loaded
		 */
		public void actionPerformed( ActionEvent e )
		{
			if( paths.length == 1 ) {
				if( paths[ 0 ] == null ) return;
				openDocument( paths[ 0 ]);
			} else {
				openDocument( paths );
			}
		}
	} // class actionOpenRecentClass

// ---------------- Action objects for window operations ---------------- 

	// action for the IOSetup menu item
	private class ActionIOSetup
	extends MenuAction
	{
		protected ActionIOSetup( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the IOSetup
		 */
		public void actionPerformed( ActionEvent e )
		{
			IOSetupFrame f = (IOSetupFrame) getApplication().getComponent( Main.COMP_IOSETUP );
		
			if( f == null ) {
				f = new IOSetupFrame();		// automatically adds component
			}
			f.setVisible( true );
			f.toFront();
		}
	}

	// action for the Control Room menu item
	private class ActionCtrlRoom
	extends MenuAction
	{
		protected ActionCtrlRoom( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the IOSetup
		 */
		public void actionPerformed( ActionEvent e )
		{
			ControlRoomFrame f = (ControlRoomFrame) getApplication().getComponent( Main.COMP_CTRLROOM );
		
			if( f == null ) {
				f = new ControlRoomFrame();	// automatically adds component
			}
			f.setVisible( true );
			f.toFront();
		}
	}

	// action for the Observer menu item
	private class ActionObserver
	extends MenuAction
	{
		protected ActionObserver( String text, KeyStroke shortcut )
		{
			super( text, shortcut );
		}

		/**
		 *  Brings up the IOSetup
		 */
		public void actionPerformed( ActionEvent e )
		{
			ObserverPalette f = (ObserverPalette) getApplication().getComponent( Main.COMP_OBSERVER );
		
			if( f == null ) {
				f = new ObserverPalette();	// automatically adds component
			}
			f.setVisible( true );
			f.toFront();
		}
	}
}