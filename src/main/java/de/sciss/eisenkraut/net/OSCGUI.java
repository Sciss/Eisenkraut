/*
 *  OSCGUI.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.KeyStroke;

import de.sciss.app.AbstractApplication;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.BooleanPrefsMenuAction;
import de.sciss.gui.KeyStrokeTextField;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuCheckItem;
import de.sciss.gui.MenuGroup;
import de.sciss.gui.MenuItem;
import de.sciss.gui.MenuNode;
import de.sciss.gui.MenuRoot;
import de.sciss.gui.MenuSeparator;
import de.sciss.net.OSCChannel;
import de.sciss.net.OSCMessage;
import de.sciss.util.Disposable;
import de.sciss.util.DynamicURLClassLoader;

/**
 * 	A basic management for custom menus via OSC and access to SwingOSC
 */
public class OSCGUI
        implements OSCRouter, Disposable {

    private static final String		OSC_GUI			= "gui";

    private static final int		SWING_PORT		= 12345;

    public static final String		KEY_SWINGAPP	= "swingapp";

    private final OSCRouterWrapper	osc;
    private final MenuBuilder		mb;

    private Object					swingOSC		= null;

    public OSCGUI() {
        osc = new OSCRouterWrapper(OSCRoot.getInstance(), this);
        mb = new MenuBuilder(this);
    }

    // ------------ Disposable interface ------------

    public void dispose() {
        mb.dispose();
        osc.remove();
    }

    public Object getSwingOSC()
    {
        return swingOSC;
    }

    // ------------ OSCRouter interface ------------

    public String oscGetPathComponent()
    {
        return OSC_GUI;
    }

    public void oscRoute( RoutedOSCMessage rom )
    {
//		if( rom.hasNext() ) {
//			oscRouteNext( rom.next() );
//		} else {
            osc.oscRoute( rom );
//		}
    }

    public void oscAddRouter(OSCRouter subRouter) {
        osc.oscAddRouter(subRouter);
    }

    public void oscRemoveRouter(OSCRouter subRouter) {
        osc.oscRemoveRouter(subRouter);
    }

    public void oscCmd_initSwing(RoutedOSCMessage rom) {
        if (swingOSC != null) return;

        final DynamicURLClassLoader cl = new DynamicURLClassLoader(getClass().getClassLoader());
        final Class<?> clz;

        try {
            cl.addURL( new File( OSCRoot.getInstance().getPreferences().get( KEY_SWINGAPP, null )).toURI().toURL() );
            clz = Class.forName( "de.sciss.swingosc.SwingOSC", true, cl );
//			swingOSC = new SwingOSC();
            swingOSC = clz.newInstance();
            // start( String protocol, int port, boolean loopBack, int bufSize, boolean initSwing, SocketAddress helloAddr)
//			swingOSC.start( OSCChannel.TCP, 12345, true, 8192, false, null );
            final Method m = clz.getMethod( "start", String.class, Integer.TYPE, Boolean.TYPE, Integer.TYPE,
                    Boolean.TYPE, InetSocketAddress.class);
            m.invoke(swingOSC, OSCChannel.TCP, SWING_PORT,
                    Boolean.TRUE, 65536, Boolean.FALSE, null);
        }
        catch( MalformedURLException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
        catch( ClassNotFoundException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
        catch( IllegalAccessException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
        catch( InstantiationException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
        catch( NoSuchMethodException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
        catch( InvocationTargetException e1 ) {
            OSCRoot.failed( rom, e1 );
        }
    }

    public Object oscQuery_swingPort() {
        return swingOSC == null ? 0 : SWING_PORT;
    }

    public Object oscQuery_swingProtocol() {
        return (swingOSC == null ? Integer.valueOf(0) : (Object) OSCChannel.TCP);
    }

    public Object oscQuery_swingRunning()
    {
        return swingOSC != null ? 1 : 0;
    }

    // ===================================================================================================
    // --------------- internal classes ---------------

    protected static class MenuBuilder
            implements OSCRouter, Disposable {

        private static final String		OSC_MENU		= "menu";

        private static final Pattern	ptrnPeriod		= Pattern.compile( "\\." );

        private final OSCRouterWrapper	osc;
        private final MenuRoot			mr;
        private final Map<Object, OSCMenuNode> mapNodes		= new HashMap<Object, OSCMenuNode>();

        protected MenuBuilder(OSCRouter superRouter) {
            osc = new OSCRouterWrapper(superRouter, this);
            mr = ((BasicApplication) AbstractApplication.getApplication()).getMenuBarRoot();
        }

        // ------------ Disposable interface ------------

        public void dispose() {
            for (OSCMenuNode oscMenuNode : mapNodes.values()) {
                (oscMenuNode).dispose();
            }
            mapNodes.clear();
            osc.remove();
        }

        // ------------ OSCRouter interface ------------

        public String oscGetPathComponent()
        {
            return OSC_MENU;
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

        // add <(int) id> <(String) type> <(String) menuHierarchy> [<(String) text> <(String) keyStroke|(int) 0>]
        // where type is one of "item", "check" (n.y.i.), "separator"
        public void oscCmd_add( RoutedOSCMessage rom )
        {
            final int			id;
            final Object		idObj;
            final String		type;
            final int			typeIdx;
            final String[]		hierarchy;
            final String		text;
            final KeyStroke		stroke;
            final OSCMenuNode	n;
            MenuGroup			mg, parent;
            int					argIdx		= 1;

            try {
                id			= ((Number) rom.msg.getArg( argIdx )).intValue();
                idObj		= id;
                if( mapNodes.containsKey( idObj )) {
                    OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCNodeExists" ));
                    return;
                }
                argIdx++;
                typeIdx		= argIdx;
                type		= rom.msg.getArg( typeIdx ).toString();
                argIdx++;
                hierarchy	= ptrnPeriod.split( rom.msg.getArg( argIdx ).toString() );
                if( hierarchy.length < 1 ) {
                    OSCRoot.failedArgValue( rom, argIdx );
                    return;
                }
                parent	= mr;
                for (String aHierarchy : hierarchy) {
                    mg = (MenuGroup) parent.get(aHierarchy);
//					if( mg == null ) {
//						mg = new MenuGroup( "oscAuto_" + (uniqueID++), hierarchy[ i ]);
//						parent.add( mg );
//					}
                    if (mg == null) {
                        OSCRoot.failed(rom.msg, OSCRoot.getResourceString("errOSCParentNotFound") + " (" + idObj + ")");
                        return;
                    }
                    parent = mg;
                }

                if( rom.msg.getArgCount() > (argIdx + 1) ) {
                    argIdx++;
                    text	= rom.msg.getArg( argIdx ).toString();
                    if( rom.msg.getArgCount() > (argIdx + 1) ) {
                        argIdx++;
                        stroke	= KeyStrokeTextField.prefsToStroke( rom.msg.getArg( argIdx ).toString() );
                    } else {
                        stroke	= null;
                    }
                } else {
                    text	= null;
                    stroke	= null;
                }

                switch (type) {
                    case "item":
                        n = new OSCMenuItem(this, id, rom.addr, parent, text, stroke);
                        break;
                    case "group":
                        n = new OSCMenuGroup(this, id, rom.addr, parent, text, stroke);
                        break;
                    case "separator":
                        n = new OSCMenuSeparator(this, id, rom.addr, parent);
                        break;
                    case "check":
                        n = new OSCMenuCheck(this, id, rom.addr, parent, text, stroke);
                        break;
                    default:
                        OSCRoot.failedArgValue(rom, typeIdx);
                        return;
                }
                n.install();
                mapNodes.put( idObj, n );
            }
            catch( ClassCastException e1 ) {
                OSCRoot.failedArgValue( rom, argIdx );
            }
            catch( IndexOutOfBoundsException e1 ) {
                OSCRoot.failedArgCount( rom );
            }
        }

        // remove <(int) id>
        public void oscCmd_remove(RoutedOSCMessage rom) {
            final Object		idObj;
            final OSCMenuNode	n;
            int					argIdx		= 1;

            try {
                idObj		= ((Number) rom.msg.getArg(argIdx)).intValue();
                n			= mapNodes.remove( idObj );
                if( n == null ) {
                    OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCNodeNotFound" ) + " (" + idObj + ")" );
                    return;
                }
                n.dispose();
            }
            catch( IndexOutOfBoundsException e1 ) {
                OSCRoot.failedArgCount( rom );
            }
        }

        protected void reply(OSCMenuNode n) {
            reply(n, null);
        }

        protected void reply(OSCMenuNode n, Object[] extraArgs) {
            final OSCRoot server = OSCRoot.getInstance();

            if (!server.isRunning()) return;

            final Object[] args = new Object[extraArgs == null ? 2 : extraArgs.length + 2];

            args[0] = n.getID();
            args[1] = "action";
            for (int i = 0, j = 2; j < args.length; i++, j++) {
                args[j] = extraArgs[i];
            }

            try {
                server.send(new OSCMessage("/gui/menu", args), n.getAddress());
            } catch (IOException e1) {
                BasicWindowHandler.showErrorDialog(null, e1, n.getText());
            }
        }
    }

    private static interface OSCMenuNode extends Disposable {
        public abstract void install();
        public abstract int getID();
        public abstract String getText();
        public abstract SocketAddress getAddress();
    }

    private static class OSCMenuItem extends MenuAction implements OSCMenuNode {
        private final MenuBuilder	mb;
        private final int			id;
        protected final MenuGroup	parent;
        private final SocketAddress	addr;
        protected MenuNode			n;

        protected OSCMenuItem( MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent, String text, KeyStroke stroke )
        {
            super( text, stroke );
            this.mb		= mb;
            this.id		= id;
            this.addr	= addr;
            this.parent	= parent;
        }

        public void install()
        {
            n = new MenuItem( "osc_" + id, this );
            parent.add( n );
        }

        public void dispose()
        {
            parent.remove( n );
        }

        public int getID()
        {
            return id;
        }

        public String getText()
        {
            return getValue( NAME ).toString();
        }

        public SocketAddress getAddress()
        {
            return addr;
        }

        public void actionPerformed( ActionEvent e )
        {
            mb.reply( this );
        }
    }

    private static class OSCMenuGroup
            extends OSCMenuItem {
        protected OSCMenuGroup(MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent, String text, KeyStroke stroke) {
            super(mb, id, addr, parent, text, stroke);
        }

        // override superclass
        public void install() {
            n = new MenuGroup("osc_" + getID(), this);
            parent.add(n);
        }
    }

    private static class OSCMenuSeparator
    implements OSCMenuNode
    {
//		private final MenuBuilder	mb;
        private final int			id;
        private final SocketAddress	addr;
        private final MenuGroup		parent;
        private MenuNode			n;

        protected OSCMenuSeparator( MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent )
        {
//			this.mb		= mb;
            this.id		= id;
            this.addr	= addr;
            this.parent	= parent;
        }

        public void install()
        {
            n = new MenuSeparator();
            parent.add( n );
        }

        public void dispose()
        {
            parent.remove( n );
        }

        public int getID()
        {
            return id;
        }

        public String getText()
        {
            return null;
        }

        public SocketAddress getAddress()
        {
            return addr;
        }
    }

    private static class OSCMenuCheck
            extends BooleanPrefsMenuAction
            implements OSCMenuNode {

        private final MenuBuilder	mb;
        private final int			id;
//		private MenuNode			n;
        private MenuCheckItem		n;
        private final MenuGroup		parent;
        private final SocketAddress	addr;

        protected OSCMenuCheck(MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent, String text, KeyStroke stroke) {
            super(text, stroke);
            this.mb		= mb;
            this.id		= id;
            this.addr	= addr;
            this.parent	= parent;
        }

        public void install() {
            n = new MenuCheckItem("osc_" + id, this);
            parent.add(n);
            setCheckItem(n);
        }

        public void dispose()
        {
            parent.remove( n );
        }

        public int getID()
        {
            return id;
        }

        public String getText() {
            return getValue(NAME).toString();
        }

        public SocketAddress getAddress() {
            return addr;
        }

        public void actionPerformed(ActionEvent e) {
            super.actionPerformed(e);

            mb.reply(this, new Object[]{"selected", n.isSelected() ? 1 : 0});
        }
    }
}