/*
 *  Main.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2007 Hanns Holger Rutz. All rights reserved.
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
 *		25-Jan-05	created
 *		22-Jul-05	redirects system output to the supercollider frame for now
 *		02-Aug-05	registers open-doc listener ; conforms with new document handler
 */

package de.sciss.eisenkraut;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import de.sciss.eisenkraut.gui.AudioFileInfoPalette;
import de.sciss.eisenkraut.gui.ControlRoomFrame;
import de.sciss.eisenkraut.gui.IOSetupFrame;
import de.sciss.eisenkraut.gui.MainFrame;
import de.sciss.eisenkraut.gui.MenuFactory;
import de.sciss.eisenkraut.gui.ObserverPalette;
import de.sciss.eisenkraut.gui.PrefsFrame;
import de.sciss.eisenkraut.gui.WelcomeScreen;
import de.sciss.eisenkraut.io.PrefCacheManager;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
//import de.sciss.eisenkraut.net.SCPlugInManager;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.render.FilterDialog;
//import de.sciss.eisenkraut.session.DocumentHandler;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.DocumentHandler;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicDocument;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpFrame;
import de.sciss.io.IOUtil;
import de.sciss.util.Flag;

/**
 *  The <code>Main</code> class contains the java VM
 *  startup static <code>main</code> method which
 *  creates a new instance of <code>Main</code>. This instance
 *  will initialize localized strings (ResourceBundle),
 *  Preferences, the <code>transport</code>, the <code>menuFactory</code>
 *  object (a prototype of the applications menu and its
 *  actions).
 *  <p>
 *  Common components are created and registered:
 *  <code>SuperColliderFrame</code>, <code>TransportPalette</code>,
 *  <code>ObserverPalette</code>, and <code>DocumentFrame</code>.
 *  <p>
 *  The <code>Main</code> class extends the <code>Application</code>
 *  class from the <code>de.sciss.app</code> package.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 24-Mar-08
 *
 *	@todo		OSC /main/quit doesn't work repeatedly
 *				; seems to be a problem of menuFactory.closeAll!
 */
public class Main
extends BasicApplication
implements OSCRouter // ProgressComponent // , PreferenceChangeListener
{
	private static final String APP_NAME	= "Eisenkraut";

	/*
	 *  Current version of the application. This is stored
	 *  in the preferences file.
	 *
	 *  @todo   should be saved in the session file as well
	 */
	private static final double APP_VERSION		= 0.70;

	/**
	 *  Enables / disables event dispatching debugging
	 */
//	public static final boolean DEBUG_EVENTS	= false;
	
	/*
	 *  The MacOS file creator string.
	 */
	private static final String					CREATOR			= "EisK";

	/**
	 *  Value for add/getComponent(): the preferences frame
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_PREFS		= PrefsFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the observer palette
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_OBSERVER	= ObserverPalette.class.getName();
	/**
	 *  Value for add/getComponent(): input/output setup
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_IOSETUP	= IOSetupFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the filter-process dialog
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_FILTER		= FilterDialog.class.getName();

	/**
	 *  Value for add/getComponent(): the main log frame
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_MAIN		= MainFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the control room frame
	 *
	 *  @see	#getComponent( Object )
	 *  @see	de.sciss.eisenkraut.gui.ControlRoomFrame
	 */
	public static final Object					COMP_CTRLROOM	= ControlRoomFrame.class.getName();
	/**
	 *  Value for add/getComponent(): the online help display frame
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_HELP  		= HelpFrame.class.getName();

	/**
	 *  Value for add/getComponent(): audio file header information
	 *
	 *  @see	#getComponent( Object )
	 */
	public static final Object					COMP_AUDIOINFO	= AudioFileInfoPalette.class.getName();
	
//	/*
//	 *  Clipboard (global, systemwide)
//	 */
//	public static final Clipboard				clipboard		= Toolkit.getDefaultToolkit().getSystemClipboard();

	/**
	 */
//	public final SCPlugInManager				scPlugInManager;


	private final OSCRouterWrapper				osc;
	private static final String					OSC_MAIN		= "main";

	private final ProcessingThread.Listener		quitAfterSaveListener;
	
	/**
	 *	The arguments may contain the following options:
	 *	<UL>
	 *	<LI>-laf &lt;screenName&gt; &lt;className&gt; : set the default look-and-feel</LI>
	 *	</UL>
	 *
	 *	All other arguments not starting with a hyphen are considered to be paths to documents
	 *	that will be opened after launch.
	 */
	public Main( String[] args )
	{
		super( Main.class, APP_NAME );

		final List					warnings;
		final Preferences			prefs			= getUserPrefs();
		final double				prefsVersion;
//		final ObserverPalette		frameObserver;
//		final ControlRoomFrame		paletteCtrlRoom;
		final MainFrame				mainFrame;
//		final CacheManager			cacheManager;
		final OSCRoot				oscServer;
		final SuperColliderClient	superCollider;
		String						lafName;
		List						openDoc			= null;

		// ---- init prefs ----

		prefsVersion = prefs.getDouble( PrefsUtil.KEY_VERSION, 0.0 );
		if( prefsVersion < APP_VERSION ) {
			warnings = PrefsUtil.createDefaults( prefs, prefsVersion );
		} else {
			warnings = null;
		}
		
		// ---- check commandline options ----

		lafName = prefs.get( PrefsUtil.KEY_LOOKANDFEEL, null );
		for( int i = 0; i < args.length; i++ ) {
			if( args[ i ].startsWith( "-" )) {
				if( args[ i ].equals( "-laf" )) {
					if( (i + 2) < args.length ) {
						UIManager.installLookAndFeel( args[ i + 1 ], args[ i + 2 ]);
						if( lafName == null ) lafName = args[ i + 2 ];
						i += 2;
					} else {
						System.err.println( "Option -laf requires two additional arguments (screen-name and class-name)." );
						System.exit( 1 );
					}
				} else {
					System.err.println( "Unknown option " + args[ i ]);
					System.exit( 1 );
				}
			} else {
				if( openDoc == null ) openDoc = new ArrayList();
				openDoc.add( args[ i ]);
			}
		}
        
		// ---- init look-and-feel ----

		lookAndFeelUpdate( lafName );

//		JFrame.setDefaultLookAndFeelDecorated( true );

		// ---- init infrastructure ----
		// warning : reihenfolge is crucial
		oscServer			= new OSCRoot( prefs.node( OSCRoot.DEFAULT_NODE ), 0x4549 );
		osc					= new OSCRouterWrapper( oscServer, this );
//		cacheManager		= 
		new PrefCacheManager( prefs.node( PrefCacheManager.DEFAULT_NODE ));
		superCollider		= new SuperColliderClient();

		init();

		// ---- listeners ----

		quitAfterSaveListener = new ProcessingThread.Listener() {
			public void processStarted( ProcessingThread.Event e ) { /* empty */ }

			// if the saving was successfull, we will call closeAll again
			public void processStopped( ProcessingThread.Event e )
			{
				if( e.isDone() ) {
					quit();
				}
			}
		};

		try {
			superCollider.init();
		}
		catch( IOException e1 ) {
			GUIUtil.displayError( null, e1, "SuperColliderClient Initialization" );
			System.exit( 1 );
			return;
		}

//		scPlugInManager		= new SCPlugInManager( this );

		// ---- component views ----

		mainFrame		= new MainFrame();
		((BasicWindowHandler) getWindowHandler()).setDefaultBorrower( mainFrame );
//		paletteCtrlRoom =
		new ControlRoomFrame();
// 		frameObserver =
        new ObserverPalette();

		if( prefsVersion == 0.0 ) { // means no preferences found, so display splash screen
    		new WelcomeScreen( this );
		}

		if( warnings != null ) {
// ZZZ
//			superCollider.setVisible( true );
//			superCollider.toFront();
			for( int i = 0; i < warnings.size(); i++ ) {
				System.err.println( warnings.get( i ));
			}
		}

		oscServer.init();
		
		if( prefs.node( PrefsUtil.NODE_AUDIO ).getBoolean( PrefsUtil.KEY_AUTOBOOT, false )) {
			superCollider.boot();
		}
		
//		de.sciss.gui.MultiStateButton.makeTestFrame( javax.swing.SwingConstants.CENTER, javax.swing.SwingConstants.CENTER );
		
		if( openDoc != null ) {
			for( int i = 0; i < openDoc.size(); i++ ) {
				getMenuFactory().openDocument( new File( openDoc.get( i ).toString() ));
			}
		}
		
//		final javax.swing.JFrame test = new javax.swing.JFrame( "SCHOKO" );
//		test.setSize( 200, 200 );
//		GUIUtil.setAlwaysOnTop( test, true );
//		test.setVisible( true );
//		final JDialog test2 = new JDialog( test );
//		test2.add( new javax.swing.JLabel( "1111" ));
//		test2.pack();
//		test2.setVisible( true );
//		final JDialog test3 = new JDialog( test );
//		test3.add( new javax.swing.JLabel( "2222" ));
//		test3.pack();
//		test3.setVisible( true );
//		final JDialog test4 = new JDialog( test3 );
//		test4.add( new javax.swing.JLabel( "3333" ));
//		test4.pack();
//		test4.setVisible( true );
//		final JDialog test5 = new JDialog( test3 );
//		test5.add( new javax.swing.JLabel( "4444" ));
//		test5.pack();
//		test5.setVisible( true );
	}

	protected BasicMenuFactory createMenuFactory()
	{
		return new MenuFactory( this );
	}
	
	protected DocumentHandler createDocumentHandler()
	{
		return new de.sciss.eisenkraut.session.DocumentHandler( this );
	}
	
	protected BasicWindowHandler createWindowHandler()
	{
		return new BasicWindowHandler( this );
	}
	
	private boolean forcedQuit = false;

	public synchronized void quit()
	{
		final Flag				confirmed	= new Flag( false );
		final ProcessingThread	pt			= getMenuFactory().closeAll( forcedQuit, confirmed );

		if( pt != null ) {
			pt.addListener( quitAfterSaveListener );
			((BasicDocument) pt.getClientArg( "doc" )).start( pt );
		} else if( confirmed.isSet() ) {
			OSCRoot.getInstance().quit();
			SuperColliderClient.getInstance().quit();
			super.quit();
		}
	}

	public void forceQuit()
	{
		forcedQuit = true;
		quit();
	}

    private void lookAndFeelUpdate( String className )
    {
        if( className != null ) {
            try {
                UIManager.setLookAndFeel( className );
				AppWindow.lookAndFeelUpdate();
            }
            catch( Exception e1 ) {
				GUIUtil.displayError( null, e1, null );
            }
        }
    }

	/**
	 *  java VM starting method. does some
	 *  static initializations and then creates
	 *  an instance of <code>Main</code>.
	 *
	 *  @param  args	are not parsed.
	 */
	public static void main( final String args[] )
	{
		// --- run the main application ---
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		EventQueue.invokeLater( new Runnable() {
			public void run()
			{
				new Main( args );
			}
		});
	}

// ------------ Application interface ------------
	
	public String getMacOSCreator()
	{
		return CREATOR;
	}

	public double getVersion()
	{
		return APP_VERSION;
	}
	
// ---------------- ProgressComponent interface ---------------- 

//	public Component getComponent()
//	{
//		return mainFrame.getComponent();
//	}
//	
//	public void resetProgression()
//	{
//		mainFrame.resetProgression();
//	}
//	
//	public void setProgression( float p )
//	{
//		mainFrame.setProgression( p );
//	}
//	
//	public void	finishProgression( boolean success )
//	{
//		mainFrame.finishProgression( success );
//	}
//	
//	public void setProgressionText( String text )
//	{
//		mainFrame.setProgressionText( text );
//	}
//	
//	public void showMessage( int type, String text )
//	{
//		mainFrame.showMessage( type, text );
//	}
//	
//	public void displayError( Exception e, String processName )
//	{
//		mainFrame.displayError( e, processName );
//	}

// ---------------- PreferenceChangeListener interface ---------------- 
	
//	public void preferenceChange( PreferenceChangeEvent e )
//	{
//		String key = e.getKey();
//        String val;
//
//		if( key.equals( PrefsUtil.KEY_OUTPUTCONFIG )) {
//			outputConfig = null;
//		}
//
////		if( key.equals( PrefsUtil.KEY_LOOKANDFEEL )) {
////            lookAndFeelUpdate( e.getNewValue() );
////		}
// 	}

// ---------------- OSCRouter interface ---------------- 

	public String oscGetPathComponent()
	{
		return OSC_MAIN;
	}
	
	public void oscRoute( RoutedOSCMessage rom )
	{
		osc.oscRoute( rom );
	}
	
	public void oscAddRouter( OSCRouter subRouter )
	{
		osc.oscAddRouter( subRouter );
	}
	
	public void oscRemoveRouter( OSCRouter subRouter )
	{
		osc.oscRemoveRouter( subRouter );
	}

	public void oscCmd_quit( RoutedOSCMessage rom )
	{
		try {
			if( rom.msg.getArgCount() > 1 ) {
				if( ((Number) rom.msg.getArg( 1 )).intValue() != 0 ) {
					forceQuit();
					return;
				}
			}
			quit();
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, 1 );
		}
	}

	public void oscCmd_createTempFile( RoutedOSCMessage rom )
	{
		try {
			final File f = IOUtil.createTempFile();
			try {
				rom.replyDone( 1, new Object[] { f.getAbsolutePath() });
			}
			catch( IOException e11 ) {
				OSCRoot.failed( rom, e11 );
			}
		}
		catch( IOException e1 ) {
			OSCRoot.failed( rom, e1 );
		}
	}
	
	public Object oscQuery_version()
	{
		return new Float( getVersion() );
	}
}