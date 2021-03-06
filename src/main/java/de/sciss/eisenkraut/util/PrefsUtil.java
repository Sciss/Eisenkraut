/*
 *  PrefsUtil.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */
 
package de.sciss.eisenkraut.util;

import de.sciss.app.AbstractApplication;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.math.ConstQ;
import de.sciss.eisenkraut.net.OSCGUI;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.gui.CoverGrowBox;
import de.sciss.io.IOUtil;
import de.sciss.net.OSCChannel;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
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

/**
 *	A helper class for programme preferences. It
 *	contains the globally known preferences keys,
 *	adds support for default preferences generation
 *	and some geometric object (de)serialization.
 *	Has utility methods for clearing preferences trees
 *	and importing and exporting from/to XML.
 */
public class PrefsUtil {

    // ------------ root node level ------------

    /**
     *  Value: Double representing the application
     *  version of Meloncillo last time prefs was saved.<br>
     *  Has default value: no!<br>
     *  Node: root
     */
    public static final String KEY_VERSION	= "version";	// double : app version

//	/**
//	 *  Value: String representing the path list of
//	 *  a user's set favourite directories. See PathList
//	 *  and PathField documentation.<br>
//	 *  Has default value: no!<br>
//	 *  Node: root
//	 */
//	public static final String KEY_USERPATHS= "usrpaths";	// string : path list

    /**
     *  Value: String representing the path of
     *  the last used directory for opening a file.<br>
     *  Has default value: no!<br>
     *  Node: root
     */
    public static final String KEY_FILEOPENDIR = "fileopendir";	// string : path

    /**
     *  Value: String representing the path of
     *  the last used directory for saving a file.<br>
     *  Has default value: no!<br>
     *  Node: root
     */
    public static final String KEY_FILESAVEDIR = "filesavedir";	// string : path

    /**
     *  Value: String representing the path of
     *  the last used directory for saving a selection of a file.<br>
     *  Has default value: no!<br>
     *  Node: root
     */
    public static final String KEY_FILESAVESELDIR = "filesaveseldir";	// string : path

    /**
     *  Value: Boolean stating whether frame bounds
     *  should be recalled a session file when it's
     *  loaded. Has default value: yes!<br>
     *  Node: root
     */
    public static final String KEY_LOOKANDFEEL = "lookandfeel";

    /**
     * Value: String of "native", "metal", "web", "submin"
     */
    public static final String KEY_LAF_TYPE                 = "laf-type";
    public static final String VALUE_LAF_TYPE_NATIVE        = "native";
    public static final String VALUE_LAF_TYPE_METAL         = "metal";
    public static final String VALUE_LAF_TYPE_SUBMIN_LIGHT  = "light";
    public static final String VALUE_LAF_TYPE_SUBMIN_DARK   = "dark";

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
     *  Value: String indicating whether Eisenkraut should start
     *  playing back a file if it is opened by double clicking
     *  in the Finder.
     *  Has default value: yes!<br>
     *  Node: audio
     */
    public static final String KEY_AUTOPLAYFROMFINDER       = "autoplay";

    public static final String AUTOPLAYFROMFINDER_NONE      = "none";
    public static final String AUTOPLAYFROMFINDER_PLAY      = "play";
    public static final String AUTOPLAYFROMFINDER_LOOP      = "loop";

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

    /**
     *  Value: String representing the command
     *  to reveal a file in the desktop manager.
     *  The command will be split on space characters,
     *  where single quotes <tt>'</tt> can be used
     *  to avoid split. Place holders for escape:
     *  <tt>%p</tt> for the file path, <tt>%P</tt> for url-style
     *  <tt>%d</tt> for parent directory, <tt>%D</tt> for url-style,
     *  <tt>%f</tt> for file, <tt>%F</tt> for url-style.
     *  Has default value: ???<br>
     *  Node: general
     */
    public static final String KEY_REVEAL_FILE  = "reveal-file";

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

    public static final String KEY_SONAENABLED	= "sonaenabled";

    private static String[] execDirs() {
        final String fs = File.separator;
        return new String[]{
                fs + "Applications" + fs + "SuperCollider_f",
                fs + "Applications" + fs + "SuperCollider",
                fs + "usr" + fs + "local" + fs + "bin",
                fs + "usr" + fs + "bin",
                "C:\\Program Files\\SuperCollider_f",
                "C:\\Program Files\\PsyCollider"
        };
    }

    public static List<String> createDefaults(Preferences mainPrefs, double lastVersion) {
        File			f;
        String			value;
        Preferences		childPrefs, childPrefs2;
        final String	fs			= File.separator;
        final List<String> warnings	= new ArrayList<String>();

        putDontOverwrite(IOUtil.getUserPrefs(), IOUtil.KEY_TEMPDIR, System.getProperty("java.io.tmpdir"));

        // general
        putDontOverwrite(mainPrefs, KEY_LOOKANDFEEL, UIManager.getSystemLookAndFeelClassName());
        putBooleanDontOverwrite(mainPrefs, CoverGrowBox.KEY_INTRUDINGSIZE, Main.isMac);
        putBooleanDontOverwrite(mainPrefs, KEY_INSERTIONFOLLOWSPLAY, true);
        putBooleanDontOverwrite(mainPrefs, KEY_VIEWCHANMETERS, true);
        putBooleanDontOverwrite(mainPrefs, KEY_VIEWMARKERS, true);
        putBooleanDontOverwrite(mainPrefs, KEY_VIEWNULLLINIE, true);
        putBooleanDontOverwrite(mainPrefs, KEY_VIEWVERTICALRULERS, true);
        putBooleanDontOverwrite(mainPrefs, KEY_CATCH, true);

        if (mainPrefs.get(KEY_REVEAL_FILE, null) == null) {
            if (Main.isMac) {
                final String[] osa = {
                        "osascript", "-e", "tell application \"Finder\"", "-e", "activate",
                        "-e", "open location \"file://%D\"",
                        "-e", "select file \"%f\" of folder of the front window",
                        "-e", "end tell"
                };
                final StringBuilder sb = new StringBuilder();
                for (String part : osa) {
                    sb.append(" ");
                    if (part.contains(" ")) {
                        sb.append("'");
                        sb.append(part);
                        sb.append("'");
                    } else {
                        sb.append(part);
                    }
                }
                final String cmd = sb.toString().trim();
                putDontOverwrite(mainPrefs, KEY_REVEAL_FILE, cmd);

            } else if (Main.isLinux) {
                final String[] dirs = execDirs();
                final boolean useDirs;
                f = findFile("nautilus", dirs);
                if (f == null) {
                    f = findFile("xdg-open", dirs);
                    useDirs = true;
                } else {
                    useDirs = false;
                }
                if (f != null) {
                    final String cmd = f.getAbsolutePath();
                    final boolean escape = cmd.contains(" ") || useDirs;
                    final String cmd1 = escape ? "'" + cmd + "'" : cmd;
                    final String cmd2 = useDirs ? cmd1 + " '%d'" : cmd1;
                    putDontOverwrite(mainPrefs, KEY_REVEAL_FILE, cmd2);
                }
            } else {
                // todo: Windows - no idea
            }
        }

        putIntDontOverwrite(mainPrefs, KEY_TIMEUNITS, TIME_MINSECS);
        putIntDontOverwrite(mainPrefs, KEY_VERTSCALE, VSCALE_AMP_LIN);

        // audio
        childPrefs = mainPrefs.node(NODE_AUDIO);
        childPrefs.remove("boottimeout");       // not used any more
        childPrefs.remove("audiodevice");       // not used any more
        childPrefs.remove("audioinputs");       // not used any more
        childPrefs.remove("audiooutputs");      // not used any more

        putDontOverwrite( childPrefs, KEY_AUTOPLAYFROMFINDER, AUTOPLAYFROMFINDER_NONE );

        // audio boxes
        value = AudioBoxConfig.ID_DEFAULT;
        putDontOverwrite( childPrefs, KEY_AUDIOBOX, value );
        try {
            if (!childPrefs.nodeExists(NODE_AUDIOBOXES)) {
                childPrefs2 = childPrefs.node(NODE_AUDIOBOXES);
                new AudioBoxConfig(value, Main.isLinux ? "Eisenkraut" : "Default", 8, 8, true).toPrefs(childPrefs2.node(value));
            }
        } catch (BackingStoreException e1) {
            warnings.add(e1.toString());
        }

        putDontOverwrite( childPrefs, KEY_AUDIORATE, new Param( 0, ParamSpace.FREQ | ParamSpace.HERTZ ).toString() );
        putDontOverwrite( childPrefs, KEY_AUDIOBUSSES, new Param( 256, ParamSpace.NONE ).toString() );
        putDontOverwrite( childPrefs, KEY_SCMEMSIZE, new Param( 8, ParamSpace.NONE ).toString() );
        putDontOverwrite( childPrefs, KEY_SCBLOCKSIZE, new Param( 64, ParamSpace.NONE ).toString() );
        putBooleanDontOverwrite(childPrefs, KEY_SCRENDEZVOUS, false);
//		putBooleanDontOverwrite( childPrefs, KEY_AUTOBOOT, false );

        // output configs
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
        } catch (BackingStoreException e1) {
            warnings.add(e1.toString());
        }

        // input configs
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
        } catch (BackingStoreException e1) {
            warnings.add(e1.toString());
        }

//		try {
//			value   = InetAddress.getLocalHost().getHostName() + ":57110";
//		}
//		catch( IOException e1 ) {
//			System.err.println( e1.toString() );
//			value   = "127.0.0.1:57109";	// not 57110 which may interfere with a running copy of sclang
//		}
//		putDontOverwrite( childPrefs, KEY_SUPERCOLLIDEROSC, value );

        putDontOverwrite(childPrefs, KEY_SCPROTOCOL, OSCChannel.TCP);
        putDontOverwrite(childPrefs, KEY_SCPORT, new Param(0, ParamSpace.ABS).toString());

        // sc app
        if (childPrefs.get(KEY_SUPERCOLLIDERAPP, null) == null) {
            f = findFile(Main.isWindows ? "scsynth.exe" : "scsynth", execDirs());
            if (f == null) {
                warnings.add(AbstractApplication.getApplication().getResourceString("errSCSynthAppNotFound"));
            }
            if (f != null) putDontOverwrite(childPrefs, KEY_SUPERCOLLIDERAPP, f.getAbsolutePath());
        }

        value = childPrefs.get( KEY_SUPERCOLLIDERAPP, null );
        putBooleanDontOverwrite( childPrefs, KEY_AUTOBOOT, (value != null) && new File( value ).isFile() );

        // OSC
        childPrefs  = OSCRoot.getInstance().getPreferences();
        putDontOverwrite   	   (childPrefs, OSCRoot.KEY_PROTOCOL, OSCChannel.TCP      );
        putIntDontOverwrite	   (childPrefs, OSCRoot.KEY_PORT    , OSCRoot.DEFAULT_PORT);
        putBooleanDontOverwrite(childPrefs, OSCRoot.KEY_ACTIVE  , Main.isLinux	      ); // used to enforce MDA

        if (childPrefs.get(OSCGUI.KEY_SWINGAPP, null) == null) {
            final String[] folders = new String[value == null ? 2 : 4];
            folders[0] = fs + "Applications" + fs + "SwingOSC";
            folders[1] = "C:\\Program Files\\SwingOSC";
            if (value != null) {
                final File f2 = new File(value).getParentFile();
                folders[2] = f2 == null ? null : f2.getAbsolutePath();
                folders[3] = new File(new File(new File(value).getParentFile(), "SwingOSC"), "build").getAbsolutePath();
            }
            f = findFile("SwingOSC.jar", folders);
            if (f != null) putDontOverwrite(childPrefs, OSCGUI.KEY_SWINGAPP, f.getAbsolutePath());
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

    private static File findFile(String fileName, String[] folders) {
        File f;

        for (String folder : folders) {
            f = new File(folder, fileName);
            if (f.exists()) return f;
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
    public static KeyStroke prefsToStroke(String prefsValue) {
        if (prefsValue == null) return null;
        int i = prefsValue.indexOf(' ');
        KeyStroke prefsStroke = null;
        try {
            if (i < 0) return null;
            prefsStroke = KeyStroke.getKeyStroke(Integer.parseInt(prefsValue.substring(i + 1)),
                    Integer.parseInt(prefsValue.substring(0, i)));
        } catch (NumberFormatException e1) {
            e1.printStackTrace();
        }

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
    public static String strokeToPrefs(KeyStroke prefsStroke) {
        if (prefsStroke == null) return null;
        else return String.valueOf(prefsStroke.getModifiers()) + ' ' +
                String.valueOf(prefsStroke.getKeyCode());
    }

    /**
     *  Traverse a preference node and optionally all
     *  children nodes and remove any keys found.
     */
    public static void removeAll(Preferences prefs, boolean deep)
            throws BackingStoreException {
        String[]	keys;
        String[]	children;
        int			i;

        keys = prefs.keys();
        for (i = 0; i < keys.length; i++) {
            prefs.remove(keys[i]);
        }
        if (deep) {
            children = prefs.childrenNames();
            for (i = 0; i < children.length; i++) {
                removeAll(prefs.node(children[i]), true);
            }
        }
    }

    /**
     *  Get an Action object that will dump the
     *  structure of the MultiTrackEditors of
     *  all selected transmitters
     */
    public static Action getDebugDumpAction() {
        return new DebugDumpAction();
    }

    @SuppressWarnings("serial")
    private static class DebugDumpAction extends AbstractAction {
        public DebugDumpAction() {
            super("Dump preferences tree");
        }

        public void actionPerformed(ActionEvent e) {
            debugDump(AbstractApplication.getApplication().getUserPrefs());
        }

        private void debugDump(Preferences prefs) {
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
                              Element node, Map<Object, Object> options )
    throws IOException
    {
        String[]	keys;
        String[]	children;
        Element		childElement, entry;
        String		value;
        int			i;

        try {
            keys = prefs.keys();
            childElement = (Element) node.appendChild(domDoc.createElement("map"));
            for (i = 0; i < keys.length; i++) {
                value = prefs.get(keys[i], null);
                if (value == null) continue;
                entry = (Element) childElement.appendChild(domDoc.createElement("entry"));
                entry.setAttribute("key", keys[i]);
                entry.setAttribute("value", value);
            }
            if (deep) {
                children = prefs.childrenNames();
                for (i = 0; i < children.length; i++) {
                    childElement = (Element) node.appendChild(domDoc.createElement("node"));
                    childElement.setAttribute("name", children[i]);
                    toXML(prefs.node(children[i]), true, domDoc, childElement, options);
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
                                Element rootElement, Map<Object, Object> options )
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