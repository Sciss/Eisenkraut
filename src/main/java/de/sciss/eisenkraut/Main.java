/*
 *  Main.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import com.alee.laf.WebLookAndFeel;
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
import de.sciss.io.CacheManager;
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
 *  @version	0.74, 02-Nov-10
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
	private static final double APP_VERSION		= 1.0;

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

		oscServer			= new OSCRoot( prefs.node( OSCRoot.DEFAULT_NODE ), 0x4549 );
		prefsVersion = prefs.getDouble( PrefsUtil.KEY_VERSION, 0.0 );
		if( prefsVersion < APP_VERSION ) {
			warnings = PrefsUtil.createDefaults( prefs, prefsVersion );
		} else {
			warnings = null;
		}
		
		// ---- check commandline options ----

        // WebLookAndFeel.install();
        // System.out.println(UIManager.getLookAndFeel().getName());
        UIManager.installLookAndFeel("WebLookAndFeel", "com.alee.laf.WebLookAndFeel");

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

		System.setProperty( "swing.aatext", "true" );
		lookAndFeelUpdate( lafName );

//		JFrame.setDefaultLookAndFeelDecorated( true );

		// ---- init infrastructure ----
		// warning : reihenfolge is crucial
//		oscServer			= new OSCRoot( prefs.node( OSCRoot.DEFAULT_NODE ), 0x4549 );
		osc					= new OSCRouterWrapper( oscServer, this );
		final CacheManager cache = new PrefCacheManager( prefs.node( PrefCacheManager.DEFAULT_NODE ));
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
			BasicWindowHandler.showErrorDialog( null, e1, "SuperColliderClient Initialization" );
			System.exit( 1 );
			return;
		}

//		scPlugInManager		= new SCPlugInManager( this );

		// ---- component views ----

		mainFrame		= new MainFrame();
		((BasicWindowHandler) getWindowHandler()).setDefaultBorrower( mainFrame );
		final AppWindow ctrlRoom	= new ControlRoomFrame();
		final AppWindow observer	= new ObserverPalette();

		// means no preferences found, so
		// do some more default initializations
		// and display splash screen
		if( prefsVersion == 0.0 ) {
			ctrlRoom.setVisible( true );
			observer.setVisible( true );
			if( cache.getFolder().isDirectory() ) {
				cache.setActive( true );
			}
    		new WelcomeScreen( this );
		}

		if( warnings != null ) {
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

//		java.util.Random rnd = new java.util.Random();
//		long t1 = System.currentTimeMillis();
//		for( int i = 0; i < 100000; i++ ) {
//			final float x = rnd.nextFloat() * 3.1415925f;
//			float a = (float) Math.cos( x );
//			float b = (float) Math.sin( x );
//		}
//		long t2 = System.currentTimeMillis();
//		for( int i = 0; i < 100000; i++ ) {
//			final float x = rnd.nextFloat() * 3.1415925f;
//			float a = (float) Math.cos( x );
//			float b = (float) Math.sqrt( 1 - a * a );
//		}
//		long t3 = System.currentTimeMillis();
//		System.out.println( "v1 took " + (t2-t1)+"; v2 took " + (t3-t2) );
		
//		final float[] fftBuf = {
//			1.5477e-08f, -6.2643e-09f, 1.5654e-08f, -5.8078e-09f, 1.5818e-08f, -5.3462e-09f,
//			1.5968e-08f, -4.8801e-09f, 1.6104e-08f, -4.4097e-09f, 1.6226e-08f, -3.9355e-09f,
//			1.6335e-08f, -3.4580e-09f, 1.6429e-08f, -2.9775e-09f, 1.6509e-08f, -2.4944e-09f,
//			1.6575e-08f, -2.0092e-09f, 1.6627e-08f, -1.5223e-09f, 1.6664e-08f, -1.0340e-09f,
//			1.6687e-08f, -5.4489e-10f, 1.6696e-08f, -5.5293e-11f, 1.6691e-08f,  4.3435e-10f,
//			1.6671e-08f,  9.2361e-10f };
//		Fourier.complexTransform( fftBuf, fftBuf.length >> 1, Fourier.FORWARD );
//		for( int j = 0; j < fftBuf.length; j += 2 ) {
//			System.out.println( fftBuf[j] + " " + (fftBuf[j+1]>=0 ? "+ " : "- ") + Math.abs(fftBuf[j+1]) + "i" );
//		}
		
		// loge(val) = (exp + log2(man)) * loge(2)
		// logx(val) = (exp + log2(man)) * log(2)/log(x)
/*
		float korr = (float) Math.log( 2 ); // / Math.log( 10 );
		double korr2 = 1.0 / Math.log( 2 );
		int q = 11;
		int mantBits = 24 - q;
		int tabSize = 1 << mantBits;
		float[] lookUp = new float[ tabSize ];
		for( int i = 0; i < tabSize; i++ ) {
			lookUp[ i ] = (float) (Math.log( i << q ) * korr2);
		}
		
		final java.util.Random rnd = new java.util.Random( System.currentTimeMillis() );
		final long t1 = System.currentTimeMillis();
		for( int i = 0; i < 10000000; i++ ) {
			float value = rnd.nextFloat();
		}
		final long t2 = System.currentTimeMillis();
		for( int i = 0; i < 10000000; i++ ) {
			float value = (float) Math.log( rnd.nextFloat() );
		}
		final long t3 = System.currentTimeMillis();
		for( int i = 0; i < 10000000; i++ ) {
			int x = Float.floatToRawIntBits( rnd.nextFloat() );
			int exp = (x >> 23) & 0xFF;
			int mant = (x & 0x7FFFFF);
			float value = (exp + lookUp[ mant >> q ]) * korr;
		}
		final long t4 = System.currentTimeMillis();
		double error = 0;
		for( int i = 0; i < 10000000; i++ ) {
			float f = rnd.nextFloat();
			int x = Float.floatToRawIntBits( f );
			int exp = (x >> 23) & 0xFF;
			int mant = (x & 0x7FFFFF);
			if( exp == 0 ) mant <<= 1; else mant |= 0x800000;
			exp -= 150;
			float value = (exp + lookUp[ mant >> q ]) * korr;
			float value2 = (float) Math.log( f );
			float err = (value - value2) / value2;
			if( !Float.isInfinite( err )) error += err*err;
			
//			System.out.println( "for " + f + " -> norm = " + value2 + "; fast = " + value + "; x = " + x+ "; lookUp = " + lookUp[ mant >> q ]);
		}
		System.out.println( "rnd " + (t2-t1) + " ; (rnd+log) " + (t3-t2) + " ; log " + (t3-t2-(t2-t1)) +
		                    "; fast " + (t4-t3-(t2-t1)) + " ; error (RMS) = " + Math.sqrt( error/10000000 ));
*/
//		final int fftSize = 128;
//		final float[] fftBuf = new float[ fftSize + 2 ];
//		final float[] gaga = new float[ fftSize + 2 ];
//		for( int i = 0; i < fftSize; i++ ) {
//			fftBuf[ i ] = (float) Math.cos( de.sciss.eisenkraut.math.MathUtil.PI2 * i / 4 ) / fftSize;
//		}
//		de.sciss.eisenkraut.math.Fourier.realTransform( fftBuf, fftSize, de.sciss.eisenkraut.math.Fourier.FORWARD );
//		de.sciss.eisenkraut.util.DebugView.fftPhase( fftBuf, (fftSize >> 1) + 1, true, "FFT" );
//		final de.sciss.eisenkraut.math.SlidingDFT slide = new de.sciss.eisenkraut.math.SlidingDFT( fftSize, 1 );
//		for( int i = 0; i < fftSize; i++ ) {
//			gaga[ i ] = (float) Math.cos( de.sciss.eisenkraut.math.MathUtil.PI2 * i / 4 ) / fftSize;
//		}
//		slide.next( gaga, 0, fftSize, 0, fftBuf );
//		de.sciss.eisenkraut.util.DebugView.fftPhase( fftBuf, (fftSize >> 1) + 1, true, "Slide1" );
//		final de.sciss.eisenkraut.math.SlidingDFT slide2 = new de.sciss.eisenkraut.math.SlidingDFT( fftSize, 1 );
//		for( int i = 0; i < fftSize; i++ ) {
//			gaga[ i ] = (float) Math.cos( de.sciss.eisenkraut.math.MathUtil.PI2 * i / 4 ) / fftSize;
//		}
//		for( int i = 0; i < fftSize; i += 1 ) {
//			slide2.next( gaga, i, 1, 0, fftBuf );
//		}
//		de.sciss.eisenkraut.util.DebugView.fftPhase( fftBuf, (fftSize >> 1) + 1, true, "Slide2" );
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