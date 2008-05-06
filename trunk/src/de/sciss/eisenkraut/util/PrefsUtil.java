/*
 *  PrefsUtil.java
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
 *      25-Jan-05	created from de.sciss.meloncillo.util.PrefsUtil
 *		13-May-05	updated from Meloncillo
 *		22-Jul-05	suggests 57109 as default scsynth port ; no longer
 *					uses 'Built-in Audio' device on Mac (sc will use
 *					default system output instead) ; TEMPDIR refers to IOUtil
 */
 
package de.sciss.eisenkraut.util;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.roydesign.mac.MRJAdapter;

import de.sciss.app.AbstractApplication;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.math.ConstQ;
import de.sciss.gui.CoverGrowBox;
import de.sciss.io.IOUtil;
import de.sciss.net.OSCChannel;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *	A helper class for programme preferences. It
 *	contains the globally known preferences keys,
 *	adds support for default preferences generation
 *	and some geometric object (de)serialization.
 *	Has utility methods for clearing preferences trees
 *	and importing and exporting from/to XML.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 21-Apr-08
 */
public class PrefsUtil
{
	// ------------ root node level ------------

	/**
	 *  Value: Double representing the application
	 *  version of Meloncillo last time prefs was saved.<br>
	 *  Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_VERSION	= "version";	// double : app version

	/**
	 *  Value: String representing the path list of
	 *  a user's set favourite directories. See PathList
	 *  and PathField documentation.<br>
	 *  Has default value: no!<br>
	 *  Node: root
	 */
	public static final String KEY_USERPATHS= "usrpaths";	// string : path list

	/**
	 *  Value: Boolean stating whether frame bounds
	 *  should be recalled a session file when it's
	 *  loaded. Has default value: yes!<br>
	 *  Node: root
	 */
	public static final String KEY_LOOKANDFEEL = "lookandfeel";	

	/**
	 *  Value: Boolean indicating whether timeline position catch
	 *  is active or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: shared
	 */
	public static final String KEY_CATCH		= "catch";		// boolean : catch on/off
	/**
	 *  Value: Boolean indicating whether timeline position is
	 *  adjusted after transport stop or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_INSERTIONFOLLOWSPLAY	= "insertionfollowsplay";		// boolean
	/**
	 *  Value: Boolean indicating whether a null-line
	 *  should be painted in the overviews or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWNULLLINIE	= "viewnulllinie";		// boolean
	/**
	 *  Value: Boolean indicating whether a vertical
	 *  (amplitude range) ruler should be display for each channel or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWVERTICALRULERS	= "viewverticalrulers";		// boolean
	/**
	 *  Value: Boolean indicating whether a marker
	 *	axis and flagsticks should be painted in the overviews or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWMARKERS	= "viewmarkers";		// boolean
	/**
	 *  Value: Boolean indicating whether level meters
	 *	per channel should be displayed or not.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VIEWCHANMETERS	= "viewchanmeters";		// boolean
	/**
	 *  Value: Integer indicating the dislayed time format.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_TIMEUNITS	= "timeunits";		// integer (TIME_SAMPLES, TIME_MINSECS)
	/**
	 *  Value: Integer indicating the dislayed amplitude scaling.<br>
	 *  Has default value: yes!<br>
	 *  Node: 
	 */
	public static final String KEY_VERTSCALE	= "vertscale";		// integer (VSCALE_AMP_LIN etc.)

	public static final int TIME_SAMPLES		= 0;
	public static final int TIME_MINSECS		= 1;

	public static final int VSCALE_AMP_LIN		= 0;
	public static final int VSCALE_AMP_LOG		= 1;
	public static final int VSCALE_FREQ_SPECT	= 2;

	// ------------ audio node level ------------

	/**
	 *  Child node of global prefs
	 */
	public static final String NODE_AUDIO	= "audio";

	/**
	 *  Value: String representing the osc port
	 *  of the supercollider application.<br>
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
//	public static final String KEY_SUPERCOLLIDEROSC	= "supercolliderosc";	// string : "ip:port"

	public static final String KEY_SCPROTOCOL		= "scprotocol";			// string : osc protocol
	public static final String KEY_SCPORT			= "scport";				// param : osc port

	/**
	 *  Value: String representing the pathname
	 *  of the supercollider application.<br>
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_SUPERCOLLIDERAPP	= "supercolliderapp";	// string : pathname

//	/**
//	 *  Value: String representing the name
//	 *  of the audio hardware to use by supercollider.
//	 *	On Mac OS X, the internal hardware is known
//	 *	as 'Built-in Audio'. Another example is 'Mobile I/O 2882 [2600]'
//	 *  Has default value: no!<br>
//	 *  Node: audio
//	 */
//	public static final String KEY_AUDIODEVICE = "audiodevice";		// string : name of audio driver
//	/**
//	 *  Value: Param representing the number
//	 *  of input channels supplied by the audio hardware
//	 *  Has default value: yes!<br>
//	 *  Node: audio
//	 */
//	public static final String KEY_AUDIOINPUTS = "audioinputs";		// Param : number of audio interface input ch
//	/**
//	 *  Value: Param representing the number
//	 *  of output channels supplied by the audio hardware
//	 *  Has default value: yes!<br>
//	 *  Node: audio
//	 */
//	public static final String KEY_AUDIOOUTPUTS = "audiooutputs";	// Param : number of audio interface output ch
	/**
	 *  Value: Param representing the sample rate
	 *  at which supercollider shall run (0 = default system rate)
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_AUDIORATE	= "audiorate";		// Param : sample rate for sc
	/**
	 *  Value: Param representing the number of
	 *  internal supercollider busses
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_AUDIOBUSSES	= "audiobusses";	// Param : integer number of busses
	/**
	 *  Value: Param representing the amount of
	 *  realtime memory for supercollider (in MB)
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_SCMEMSIZE	= "scmemsize";		// Param : integer number MB realtime mem for supercollider
	/**
	 *  Value: Param representing the number of
	 *  audio samples per control period in supercollider
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_SCBLOCKSIZE	= "scblocksize";	// Param : integer number samples per control period in supercollider
	/**
	 *  Value: Boolean determining whether scsynth should be booted
	 *  with Rendezvous enabled or not.
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_SCRENDEZVOUS	= "screndezvous";	// Boolean
	/**
	 *  Value: Boolean indicating whether scsynth shall
	 *  be booted automatically when Eisenkraut launched or not
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_AUTOBOOT		= "autoboot";	// boolean : automatic scsynth booting
	/**
	 *  Value: String representing the name
	 *  of the currently active output routine configuration
	 *  Has default value: yes!<br>
	 *  Node: audio
	 */
	public static final String KEY_OUTPUTCONFIG	= "outputconfig";	// string : active output configuration
	/**
	 *  Value: String representing the name
	 *  of the currently active audio box
	 *  Has default value: ???<br>
	 *  Node: audio
	 */
	public static final String KEY_AUDIOBOX		= "audiobox";	// string : active audio box configuration

	// ------------ audio->inputconfigs node level ------------

	/**
	 *  Child node of audio prefs
	 */
	public static final String NODE_INPUTCONFIGS	= "inputconfigs";

	// ------------ audio->outputconfigs node level ------------

	/**
	 *  Child node of audio prefs
	 */
	public static final String NODE_OUTPUTCONFIGS	= "outputconfigs";

	// ------------ audio->outputconfigs node level ------------

	/**
	 *  Child node of audio prefs
	 */
	public static final String NODE_AUDIOBOXES		= "audioboxes";

	// ------------ plugin node level ------------

	/**
	 *  Child node of global prefs
	 */
	public static final String NODE_PLUGINS	= "plugins";

	// ------------ view node level ------------

	/**
	 *  Child node of global prefs
	 */
	public static final String NODE_VIEW	= "view";

	/**
	 *  Child node of view prefs
	 */
	public static final String NODE_SONAGRAM	= "sonagram";

	public static List createDefaults( Preferences mainPrefs, double lastVersion )
	{
		File			f;
		String			value;
		Preferences		childPrefs, childPrefs2;
		final String	fs			= File.separator;
		final boolean	isMacOS		= System.getProperty( "os.name" ).indexOf( "Mac OS" ) >= 0;
        final boolean	isWindows	= System.getProperty( "os.name" ).indexOf( "Windows" ) >= 0;
		final List		warnings	= new ArrayList();
	
		putDontOverwrite( IOUtil.getUserPrefs(), IOUtil.KEY_TEMPDIR, System.getProperty( "java.io.tmpdir" ));

		// general
//		putDontOverwrite( GUIUtil.getUserPrefs(), HelpGlassPane.KEY_KEYSTROKE_HELP, strokeToPrefs(
//			KeyStroke.getKeyStroke( KeyEvent.VK_H, MenuFactory.MENU_SHORTCUT + KeyEvent.SHIFT_MASK )));

		putDontOverwrite( mainPrefs, KEY_LOOKANDFEEL, UIManager.getSystemLookAndFeelClassName() );
		putBooleanDontOverwrite( mainPrefs, CoverGrowBox.KEY_INTRUDINGSIZE, isMacOS );
		putBooleanDontOverwrite( mainPrefs, KEY_INSERTIONFOLLOWSPLAY, true );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWNULLLINIE, false );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWVERTICALRULERS, false );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWMARKERS, false );
		putBooleanDontOverwrite( mainPrefs, KEY_VIEWCHANMETERS, false );
		
		putIntDontOverwrite( mainPrefs, KEY_TIMEUNITS, TIME_MINSECS );
		putIntDontOverwrite( mainPrefs, KEY_VERTSCALE, VSCALE_AMP_LIN );

		// audio
		childPrefs  = mainPrefs.node( NODE_AUDIO );
		childPrefs.remove( "boottimeout" );		// not used any more
		childPrefs.remove( "audiodevice" );		// not used any more
		childPrefs.remove( "audioinputs" );		// not used any more
		childPrefs.remove( "audiooutputs" );	// not used any more

		// audioboxes
		value = AudioBoxConfig.ID_DEFAULT;
		putDontOverwrite( childPrefs, KEY_AUDIOBOX, value );
		try {
			if( !childPrefs.nodeExists( NODE_AUDIOBOXES )) {
				childPrefs2	= childPrefs.node( NODE_AUDIOBOXES );
				new AudioBoxConfig( value, "Default", 8, 8, true ).toPrefs( childPrefs2.node( value ));
//				if( isMacOS ) {
//					value = "builtin";
//					new AudioBoxConfig( value, "Built-in Audio", 2, 2 ).toPrefs( childPrefs2.node( value ));
//				}
			}
		}
		catch( BackingStoreException e1 ) {
			warnings.add( e1.toString() );
		}

		putDontOverwrite( childPrefs, KEY_AUDIORATE, new Param( 0, ParamSpace.FREQ | ParamSpace.HERTZ ).toString() );
		putDontOverwrite( childPrefs, KEY_AUDIOBUSSES, new Param( 128, ParamSpace.NONE ).toString() );
		putDontOverwrite( childPrefs, KEY_SCMEMSIZE, new Param( 8, ParamSpace.NONE ).toString() );
		putDontOverwrite( childPrefs, KEY_SCBLOCKSIZE, new Param( 64, ParamSpace.NONE ).toString() );
		putBooleanDontOverwrite( childPrefs, KEY_SCRENDEZVOUS, true );
		putBooleanDontOverwrite( childPrefs, KEY_AUTOBOOT, false );

		// outputconfigs
		value = "out1to2";
		putDontOverwrite( childPrefs, KEY_OUTPUTCONFIG, value );
		try {
			if( !childPrefs.nodeExists( NODE_OUTPUTCONFIGS )) {
				childPrefs2 = childPrefs.node( NODE_OUTPUTCONFIGS );
				new RoutingConfig( value, "Out 1-2", new int[] { 0, 1 }, -90f ).toPrefs(
					childPrefs2.node( value ));
				value = "out1to8";
				new RoutingConfig( value, "Out 1-8", new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }, -45f ).toPrefs(
					childPrefs2.node( value ));
//				// ok dis wan is rather for demonstration purpose
//				new RoutingConfig( value, 2, new int[] { 1, 0 }, -90f ).toPrefs(
//					childPrefs2.node( "Reversed Stereo" ));
			}
		}
		catch( BackingStoreException e1 ) {
			warnings.add( e1.toString() );
		}

		// inputconfigs
		try {
			if( !childPrefs.nodeExists( NODE_INPUTCONFIGS )) {
				childPrefs2 = childPrefs.node( NODE_INPUTCONFIGS );
				value = "in1";
				new RoutingConfig( value, "In 1", new int[] { 0 }, 0f ).toPrefs(
					childPrefs2.node( value ));
				value = "in1to2";
				new RoutingConfig( value, "In 1-2", new int[] { 0, 1 }, 0f ).toPrefs(
					childPrefs2.node( value ));
				value = "in1to8";
				new RoutingConfig( value, "In 1-8", new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }, 0f ).toPrefs(
					childPrefs2.node( value ));
//				// ok dis wan is rather for demonstration purpose
//				new RoutingConfig( value, 2, new int[] { 1, 0 }, -90f ).toPrefs(
//					childPrefs2.node( "Reversed Stereo" ));
			}
		}
		catch( BackingStoreException e1 ) {
			warnings.add( e1.toString() );
		}

//		try {
//			value   = InetAddress.getLocalHost().getHostName() + ":57110";
//		}
//		catch( IOException e1 ) {
//			System.err.println( e1.toString() );
//			value   = "127.0.0.1:57109";	// not 57110 which may interfere with a running copy of sclang
//		}
//		putDontOverwrite( childPrefs, KEY_SUPERCOLLIDEROSC, value );
		putDontOverwrite( childPrefs, KEY_SCPROTOCOL, OSCChannel.TCP );
		putDontOverwrite( childPrefs, KEY_SCPORT, new Param( 0, ParamSpace.NONE | ParamSpace.ABS ).toString() );
		
		// sc app
		if( childPrefs.get( KEY_SUPERCOLLIDERAPP, null ) == null ) {
			f	= findFile( isWindows ? "scsynth.exe" : "scsynth", new String[] {
				fs + "Applications" + fs + "SuperCollider_f",
				fs + "usr" + fs + "local" + fs + "bin",
				fs + "usr" + fs + "bin",
				"C:\\Program Files\\SuperCollider_f"
			});
			if( f == null ) {
				if( isMacOS ) {
					try {
						f = MRJAdapter.findApplication( "SCjm" );
						if( f != null ) f = new File( f.getParentFile(), "scsynth" );
					}
					catch( IOException e1 ) { /* ignore */ }
				}
				if( f == null ) {
					warnings.add( AbstractApplication.getApplication().getResourceString( "errSCSynthAppNotFound" ));
				}
			}
			if( f != null ) putDontOverwrite( childPrefs, KEY_SUPERCOLLIDERAPP, f.getAbsolutePath() );
		}
		
		// view
		childPrefs  = mainPrefs.node( NODE_VIEW );
		try {
			if( !childPrefs.nodeExists( NODE_SONAGRAM )) {
				childPrefs2 = childPrefs.node( NODE_SONAGRAM );
				new ConstQ().writePrefs( childPrefs2 );
			}
		}
		catch( BackingStoreException e1 ) {
			warnings.add( e1.toString() );
		}
			
		// save current version
		mainPrefs.putDouble( KEY_VERSION, AbstractApplication.getApplication().getVersion() );

		return warnings;
	}

	private static File findFile( String fileName, String[] folders )
	{
		File f;
	
		for( int i = 0; i < folders.length; i++ ) {
			f = new File( folders[ i ], fileName );
			if( f.exists() ) return f;
		}
		return null;
	}

	// --- custom put/get methods ---

	private static boolean putDontOverwrite( Preferences prefs, String key, String value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.put( key, value );
		}
		
		return overwrite;
	}
	
	private static boolean putIntDontOverwrite( Preferences prefs, String key, int value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.putInt( key, value );
		}
		
		return overwrite;
	}
	
	private static boolean putBooleanDontOverwrite( Preferences prefs, String key, boolean value )
	{
		boolean overwrite = prefs.get( key, null ) == null;
		
		if( overwrite ) {
			prefs.putBoolean( key, value );
		}
		
		return overwrite;
	}

//	private static boolean putDoubleDontOverwrite( Preferences prefs, String key, double value )
//	{
//		boolean overwrite = prefs.get( key, null ) == null;
//		
//		if( overwrite ) {
//			prefs.putDouble( key, value );
//		}
//		
//		return overwrite;
//	}
	
	public static Rectangle stringToRectangle( String value )
	{
		Rectangle				rect	= null;
		final StringTokenizer	tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				rect	= new Rectangle( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ),
										 Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return rect;
	}

	public static Point stringToPoint( String value )
	{
		Point					pt	= null;
		final StringTokenizer	tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				pt		= new Point( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return pt;
	}

	public static Dimension stringToDimension( String value )
	{
		Dimension				dim	= null;
		final StringTokenizer	tok;
		
		if( value != null ) {
			try {
				tok		= new StringTokenizer( value );
				dim		= new Dimension( Integer.parseInt( tok.nextToken() ), Integer.parseInt( tok.nextToken() ));
			}
			catch( NoSuchElementException e1 ) { e1.printStackTrace(); }
			catch( NumberFormatException e2 ) { e2.printStackTrace(); }
		}
		return dim;
	}
	
	/**
	 *  Rectangle, z.B. von Frame.getBounds() in
	 *  einen String konvertieren, der als Prefs
	 *  gespeichert werden kann. Bei Fehler wird
	 *  null zurueckgeliefert. 'value' darf null sein.
	 */
	public static String rectangleToString( Rectangle value )
	{
		return( value != null ? (value.x + " " + value.y + " " + value.width + " " + value.height) : null );
	}
	
	public static String pointToString( Point value )
	{
		return( value != null ? (value.x + " " + value.y) : null );
	}
	
	public static String dimensionToString( Dimension value )
	{
		return( value != null ? (value.width + " " + value.height) : null );
	}
	
//	public static String classToNodeName( Class c )
//	{
//		return( "/" + c.getName().replace( '.', '/' ));
//	}

	/**
	 *  Converts a a key stroke's string representation as
	 *	from preference storage into a KeyStroke object.
	 *
	 *  @param		prefsValue		a string representation of the form &quot;modifiers keyCode&quot;
	 *								or <code>null</code>
	 *	@return		the KeyStroke parsed from the prefsValue or null if the string was
	 *				invalid or <code>null</code>
	 */
	public static final KeyStroke prefsToStroke( String prefsValue )
	{
		if( prefsValue == null ) return null;
		int i = prefsValue.indexOf( ' ' );
		KeyStroke prefsStroke = null;
		try {
			if( i < 0 ) return null;
			prefsStroke = KeyStroke.getKeyStroke( Integer.parseInt( prefsValue.substring( i+1 )),
												  Integer.parseInt( prefsValue.substring( 0, i )));
		}
		catch( NumberFormatException e1 ) { e1.printStackTrace(); }

		return prefsStroke;
	}
	
	/**
	 *  Converts a KeyStroke into a string representation for
	 *	preference storage.
	 *
	 *  @param		prefsStroke	the KeyStroke to convert
	 *	@return		a string representation of the form &quot;modifiers keyCode&quot;
	 *				or <code>null</code> if the prefsStroke is invalid or <code>null</code>
	 */
	public static final String strokeToPrefs( KeyStroke prefsStroke )
	{
		if( prefsStroke == null ) return null;
		else return String.valueOf( prefsStroke.getModifiers() ) + ' ' +
					String.valueOf( prefsStroke.getKeyCode() );
	}

	/**
	 *  Traverse a preference node and optionally all
	 *  children nodes and remove any keys found.
	 */
	public static void removeAll( Preferences prefs, boolean deep )
	throws BackingStoreException
	{
		String[]	keys;
		String[]	children;
		int			i;

		keys = prefs.keys();
		for( i = 0; i < keys.length; i++ ) {
			prefs.remove( keys[i] );
		}
		if( deep ) {
			children	= prefs.childrenNames();
			for( i = 0; i < children.length; i++ ) {
				removeAll( prefs.node( children[i] ), deep );
			}
		}
	}

	/**
	 *  Get an Action object that will dump the
	 *  structure of the MultiTrackEditors of
	 *  all selected transmitters
	 */
	public static Action getDebugDumpAction()
	{
		AbstractAction a = new AbstractAction( "Dump preferences tree" ) {
			public void actionPerformed( ActionEvent e )
			{
				debugDump( AbstractApplication.getApplication().getUserPrefs() );
			}
			
			private void debugDump( Preferences prefs )
			{
				System.err.println( "------- debugDump prefs : "+prefs.name()+" -------" );
				String[]	keys;
				String[]	children;
				String		value;
				int			i;
				
				try {
					keys		= prefs.keys();
					for( i = 0; i < keys.length; i++ ) {
						value   = prefs.get( keys[i], null );
						System.err.println( "  key = '"+keys[i]+"' ; value = '"+value+"'" );
					}
					children	= prefs.childrenNames();
					for( i = 0; i < children.length; i++ ) {
						debugDump( prefs.node( children[i] ));
					}
				} catch( BackingStoreException e1 ) {
					System.err.println( e1.getLocalizedMessage() );
				}
			}
		};
		return a;
	}

	/**
	 *  Similar to the XMLRepresentation interface,
	 *  this method will append an XML representation
	 *  of some preferences to an existing node.
	 *
	 *  @param  prefs   the preferences node to write out.
	 *  @param  deep	- true to include a subtree with all
	 *					child preferences nodes.
	 *  @param  domDoc  the document in which the node will reside.
	 *  @param  node	the node to which a child is applied.
	 */
	public static void toXML( Preferences prefs, boolean deep, org.w3c.dom.Document domDoc,
							  Element node, Map options )
	throws IOException
	{
		String[]	keys;
		String[]	children;
		Element		childElement, entry;
		String		value;
		int			i;

//System.err.println( "node = "+prefs.name() );
		try {
			keys			= prefs.keys();
			childElement	= (Element) node.appendChild( domDoc.createElement( "map" ));
			for( i = 0; i < keys.length; i++ ) {
				value   = prefs.get( keys[i], null );
//System.err.println( "  key = "+keys[i]+"; value = "+value );
				if( value == null ) continue;
				entry = (Element) childElement.appendChild( domDoc.createElement( "entry" ));
				entry.setAttribute( "key", keys[i] );
				entry.setAttribute( "value", value );
			}
			if( deep ) {
				children	= prefs.childrenNames();
				for( i = 0; i < children.length; i++ ) {
					childElement = (Element) node.appendChild( domDoc.createElement( "node" ));
					childElement.setAttribute( "name", children[i] );
					toXML( prefs.node( children[i] ), deep, domDoc, childElement, options );
				}
			}
		} catch( DOMException e1 ) {
			throw IOUtil.map( e1 );
		} catch( BackingStoreException e2 ) {
			throw IOUtil.map( e2 );
		}
	}

	/**
	 *  Similar to the XMLRepresentation interface,
	 *  this method will parse an XML representation
	 *  of some preferences and restore it's values.
	 *
	 *  @param  prefs		the preferences node to import to.
	 *  @param  domDoc		the document in which the node resides.
	 *  @param  rootElement	the node whose children to parse.
	 */
	public static void fromXML( Preferences prefs, org.w3c.dom.Document domDoc,
								Element rootElement, Map options )
	throws IOException
	{
		NodeList	nl, nl2;
		Element		childElement, entry;
		Node		node;
		int			i, j;

		try {
			nl	= rootElement.getChildNodes();
			for( i = 0; i < nl.getLength(); i++ ) {
				node			= nl.item( i );
				if( !(node instanceof Element) ) continue;
				childElement	= (Element) node;
				nl2				= childElement.getElementsByTagName( "entry" );
				for( j = 0; j < nl2.getLength(); j++ ) {
					entry		= (Element) nl2.item( j );
					prefs.put( entry.getAttribute( "key" ), entry.getAttribute( "value" ));
//System.err.println( "auto : node = "+(prefs.name() )+"; key = "+entry.getAttribute( "key" )+"; val = "+entry.getAttribute( "value" ) );

				}
				break;
			}
			for( ; i < nl.getLength(); i++ ) {
				node			= nl.item( i );
				if( !(node instanceof Element) ) continue;
				childElement	= (Element) node;
				fromXML( prefs.node( childElement.getAttribute( "name" )), domDoc, childElement, options );
			}
		} catch( DOMException e1 ) {
			throw IOUtil.map( e1 );
		}
	}
}