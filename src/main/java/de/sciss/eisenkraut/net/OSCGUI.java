/*
 *  OSCGUI.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
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
		return (swingOSC == null ? new Integer(0) : (Object) OSCChannel.TCP);
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
				
				if( type.equals( "item" )) {
					n = new OSCMenuItem( this, id, rom.addr, parent, text, stroke );
				} else if( type.equals( "group" )) {
					n = new OSCMenuGroup( this, id, rom.addr, parent, text, stroke );
				} else if( type.equals( "separator" )) {
					n = new OSCMenuSeparator( this, id, rom.addr, parent );
				} else if( type.equals( "check" )) {
					n = new OSCMenuCheck( this, id, rom.addr, parent, text, stroke );
				} else {
					OSCRoot.failedArgValue( rom, typeIdx );
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

	@SuppressWarnings("serial")
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

	@SuppressWarnings("serial")
	private static class OSCMenuGroup
	extends OSCMenuItem
	{
		protected OSCMenuGroup( MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent, String text, KeyStroke stroke )
		{
			super( mb, id, addr, parent, text, stroke );
		}
		
		// override superclass
		public void install()
		{
			n = new MenuGroup( "osc_" + getID(), this );
			parent.add( n );
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

	@SuppressWarnings("serial")
	private static class OSCMenuCheck
	extends BooleanPrefsMenuAction
	implements OSCMenuNode
	{
		private final MenuBuilder	mb;
		private final int			id;
//		private MenuNode			n;
		private MenuCheckItem		n;
		private final MenuGroup		parent;
		private final SocketAddress	addr;
	
		protected OSCMenuCheck( MenuBuilder mb, int id, SocketAddress addr, MenuGroup parent, String text, KeyStroke stroke )
		{
			super( text, stroke );
			this.mb		= mb;
			this.id		= id;
			this.addr	= addr;
			this.parent	= parent;
		}
		
		public void install()
		{
			n = new MenuCheckItem( "osc_" + id, this );
			parent.add( n );
			setCheckItem( n );
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
	
	// ===================================================================================================

/*
	private static class WindowBuilder
	implements OSCRouter, Disposable
	{
		private static final String		OSC_WINDOW		= "window";

		private final OSCRouterWrapper	osc;
//		private final OSCGUI			gui;
		private final Map				mapNodes		= new HashMap();
	
		private WindowBuilder( OSCRouter superRouter )
		{
//			this.gui	= gui;
			osc			= new OSCRouterWrapper( superRouter, this );
		}

		// ------------ Disposable interface ------------

		public void dispose()
		{
			for( Iterator iter = mapNodes.values().iterator(); iter.hasNext(); ) {
				((OSCWindow) iter.next()).dispose();
			}
			mapNodes.clear();
			osc.remove();
		}
		
		// ------------ OSCRouter interface ------------

		public String oscGetPathComponent()
		{
			return OSC_WINDOW;
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

		// add <(int) id> <(String) type> <(String) title> [<(int) resizable=0|1>]
		// where type is one of "frame", "palette"
		public void oscCmd_add( RoutedOSCMessage rom )
		{
			final int			id;
			final Object		idObj;
			final String		typeStr;
			final int			type;
			final String		title;
			final boolean		resizable;
			final OSCWindow		n;
			int					argIdx		= 1;
			
			try {
				id			= ((Number) rom.msg.getArg( argIdx )).intValue();
				idObj		= new Integer( id );
				if( mapNodes.containsKey( idObj )) {
					OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCNodeExists" ));
					return;
				}
				argIdx++;
				typeStr		= rom.msg.getArg( argIdx ).toString();
				if( typeStr.equals( "frame" )) {
					type	= AbstractWindow.SUPPORT;
				} else if( typeStr.equals( "palette" )) {
					type	= AbstractWindow.PALETTE;
				} else{
					OSCRoot.failedArgValue( rom, argIdx );
					return;
				}
				argIdx++;
				title		= rom.msg.getArg( argIdx ).toString();
	
				if( rom.msg.getArgCount() > (argIdx + 1) ) {
					argIdx++;
					resizable	= ((Number) rom.msg.getArg( argIdx )).intValue() != 0;
				} else {
					resizable	= false;
				}
				
				n	= new OSCWindow( this, id, rom.addr, type, title, resizable );
//				n.install();
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
		public void oscCmd_remove( RoutedOSCMessage rom )
		{
			final Object		idObj;
			final OSCWindow		n;
			int					argIdx		= 1;
			
			try {
				idObj		= new Integer( ((Number) rom.msg.getArg( argIdx )).intValue() );
				n			= (OSCWindow) mapNodes.remove( idObj );
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
	}

	private static class OSCWindow
	extends AppWindow
	implements OSCRouter
	{
		private final WindowBuilder		wb;
		private final int				id;
		private final String			idStr;
		private final String			fullOSCAddr;
		private final SocketAddress		addr;
		private final OSCRouterWrapper	osc;
		private final SpringPanel		panel;
		private final Map				mapNodes	= new HashMap();
		private boolean					layoutDirty	= false;
	
		private OSCWindow( WindowBuilder wb, int id, SocketAddress addr, int type, String title, boolean resizable )
		{
			super( type );
			setTitle( title );
			setResizable( resizable );
			
			this.wb		= wb;
			this.id		= id;
			idStr		= String.valueOf( id );
			fullOSCAddr	= "/gui/window/" + idStr;
			this.addr	= addr;
			osc			= new OSCRouterWrapper( wb, this );
			panel		= new SpringPanel( 2, 1, 2, 1 );
			
//			setSize( new Dimension( 400, 400 ));
			setLocationRelativeTo( null );
			
			getContentPane().add( panel, BorderLayout.CENTER );
			
			init();
		}
	
		public void dispose()
		{
			for( Iterator iter = mapNodes.values().iterator(); iter.hasNext(); ) {
				((OSCGadgetNode) iter.next()).dispose();
			}
			mapNodes.clear();
			osc.remove();
			super.dispose();
		}

		protected boolean restoreVisibility()
		{
			return false;
		}

		private void reply( OSCGadgetNode n )
		{
			reply( n, null );
		}

		private void reply( OSCGadgetNode n, Object[] extraArgs )
		{
			final OSCRoot server	= OSCRoot.getInstance();
			
			if( !server.isRunning() ) return;
			
			final Object[]	args	= new Object[ extraArgs == null ? 2 : extraArgs.length + 2 ];
		
			args[ 0 ] = new Integer( n.getID() );
			args[ 1 ] = "action";
			for( int i = 0, j = 2; j < args.length; i++, j++ ) {
				args[ j ] = extraArgs[ i ];
			}
		
			try {
				server.send( new OSCMessage( fullOSCAddr, args ), n.getAddress() );
			}
			catch( IOException e1 ) {
				GUIUtil.displayError( getWindow(), e1, n.getText() );
			}
		}


		// ------------ OSCRouter interface ------------

		public String oscGetPathComponent()
		{
			return idStr;
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
		
		public void oscCmd_show( RoutedOSCMessage rom )
		{
			if( isVisible() ) return;
			
			if( layoutDirty ) {
				panel.makeCompactGrid();
				pack();
				layoutDirty = false;
			}
			setVisible( true );
			toFront();
		}
		
		public void oscCmd_hide( RoutedOSCMessage rom )
		{
			setVisible( false );
		}

		// add <(int) id> <(String) type> <(int) x> <(int) y> [<(int) w> [<(int) h> [<(int) fill> [<float> alignH> [<float> alignV>]]]]]
		// where type is one of "label", "hslider", "vslider", "knob", "choice", "check", "button", "text", "number", "help"
		// ; where fill is one of 0 (NONE), 1 (BOTH), 2 (HORIZONTAL), 3 (VERTICAL)
		// ; where alignH and alignV = 0 (top/left) ... 1 (bottom/right)
		public void oscCmd_add( RoutedOSCMessage rom )
		{
			final int			id;
			final Object		idObj;
			final String		type;
			final int			typeIdx;
			final int			gridX, gridY, gridW, gridH;
			final boolean		fillH, fillV;
			final float			alignH, alignV;
			final JComponent	jc;
			final OSCGadgetNode	n;
			int					argIdx		= 1;
			
			try {
				id			= ((Number) rom.msg.getArg( argIdx )).intValue();
				idObj		= new Integer( id );
				if( mapNodes.containsKey( idObj )) {
					OSCRoot.failed( rom.msg, OSCRoot.getResourceString( "errOSCNodeExists" ));
					return;
				}
				argIdx++;
				typeIdx		= argIdx;
				type		= rom.msg.getArg( typeIdx ).toString();
				argIdx++;
				gridX		= ((Number) rom.msg.getArg( argIdx )).intValue();
				argIdx++;
				gridY		= ((Number) rom.msg.getArg( argIdx )).intValue();
				if( rom.msg.getArgCount() > (argIdx + 1) ) {
					argIdx++;
					gridW	= ((Number) rom.msg.getArg( argIdx )).intValue();
					if( gridW < 1 ) {
						OSCRoot.failedArgValue( rom, argIdx );
						return;
					}
					if( rom.msg.getArgCount() > (argIdx + 1) ) {
						argIdx++;
						gridH	= ((Number) rom.msg.getArg( argIdx )).intValue();
						if( gridH < 1 ) {
							OSCRoot.failedArgValue( rom, argIdx );
							return;
						}
						if( rom.msg.getArgCount() > (argIdx + 1) ) {
							argIdx++;
							switch( ((Number) rom.msg.getArg( argIdx )).intValue() ) {
							case 0:
								fillH	= false;
								fillV	= false;
								break;
							case 1:
								fillH	= true;
								fillV	= true;
								break;
							case 2:
								fillH	= true;
								fillV	= false;
								break;
							case 3:
								fillH	= false;
								fillV	= true;
								break;
							default:
								OSCRoot.failedArgValue( rom, argIdx );
								return;
							}
							if( rom.msg.getArgCount() > (argIdx + 1) ) {
								argIdx++;
								alignH = ((Number) rom.msg.getArg( argIdx )).floatValue();
								if( alignH < 0f || alignH > 1f ) {
									OSCRoot.failedArgValue( rom, argIdx );
									return;
								}
								if( rom.msg.getArgCount() > (argIdx + 1) ) {
									argIdx++;
									alignV = ((Number) rom.msg.getArg( argIdx )).floatValue();
									if( alignV < 0f || alignV > 1f ) {
										OSCRoot.failedArgValue( rom, argIdx );
										return;
									}
								} else {
									alignV	= 0f;
								}
							} else {
								alignH	= 0f;
								alignV	= 0f;
							}
						} else {
							fillH	= false;
							fillV	= false;
							alignH	= 0f;
							alignV	= 0f;
						}
					} else {
						gridH	= 1;
						fillH	= false;
						fillV	= false;
						alignH	= 0f;
						alignV	= 0f;
					}
				} else {
					gridW	= 1;
					gridH	= 1;
					fillH	= false;
					fillV	= false;
					alignH	= 0f;
					alignV	= 0f;
				}
				
				if( type.equals( "label" )) {
					n = new OSCGadgetLabel( this, id, rom.addr );
				} else if( type.equals( "hslider" )) {
					n = new OSCGadgetSlider( this, id, rom.addr, JSlider.HORIZONTAL );
				} else if( type.equals( "vslider" )) {
					n = new OSCGadgetSlider( this, id, rom.addr, JSlider.VERTICAL );
//				} else if( type.equals( "knob" )) {
//					n = new OSCGadgetKnob( this, id, rom.addr );
				} else if( type.equals( "choice" )) {
					n = new OSCGadgetChoice( this, id, rom.addr );
				} else if( type.equals( "check" )) {
					n = new OSCGadgetCheck( this, id, rom.addr );
				} else if( type.equals( "button" )) {
					n = new OSCGadgetButton( this, id, rom.addr );
//				} else if( type.equals( "text" )) {
//					n = new OSCGadgetText( this, id, rom.addr );
//				} else if( type.equals( "number" )) {
//					n = new OSCGadgetNumber( this, id, rom.addr );
//				} else if( type.equals( "help" )) {
//					n = new OSCGadgetHelp( this, id, rom.addr );
				} else {
					OSCRoot.failedArgValue( rom, typeIdx );
					return;
				}
//				n.install();
				jc = n.createGadget();
				jc.setFont( AbstractApplication.getApplication().getGraphicsHandler().getFont( GraphicsHandler.FONT_SYSTEM | GraphicsHandler.FONT_SMALL ));
jc.setAlignmentX( alignH );
jc.setAlignmentY( alignV );
				panel.gridAdd( jc, gridX, gridY, fillH ? gridW : -gridW, fillV ? gridH : -gridH );
				// XXX deal with alignment
				mapNodes.put( idObj, n );
				if( isVisible() ) {
					panel.makeCompactGrid();
				} else {
					layoutDirty	= true;
				}
			}
			catch( ClassCastException e1 ) {
				OSCRoot.failedArgValue( rom, argIdx );
			}
			catch( IndexOutOfBoundsException e1 ) {
				OSCRoot.failedArgCount( rom );
			}
		}
	}

//	private static interface OSCGadgetNode
//	extends Disposable
//	{
//		public abstract void install();
//		public abstract int getID();
//		public abstract String getText();
//		public abstract SocketAddress getAddress();
//	}
	
	private static abstract class OSCGadgetNode
	implements OSCRouter, Disposable
	{
		protected final OSCWindow		parent;
		private final int				id;
		private final String			idStr;
		private final SocketAddress		addr;
		private final OSCRouterWrapper	osc;
	
		protected OSCGadgetNode( OSCWindow parent, int id, SocketAddress addr )
		{
			this.parent	= parent;
			this.id		= id;
			idStr		= String.valueOf( id );
			this.addr	= addr;
			osc			= new OSCRouterWrapper( parent, this );
		}
	
		public int getID()
		{
			return id;
		}
		
		
		public SocketAddress getAddress()
		{
			return addr;
		}
		
		public void dispose()
		{
			parent.getContentPane().remove( getGadget() );
			osc.remove();
		}

		public abstract JComponent createGadget();
		public abstract String getText();
		protected abstract JComponent getGadget();

		// ------------ OSCRouter interface ------------

		public String oscGetPathComponent()
		{
			return idStr;
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

		public void oscCmd_setEnabled( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				if( rom.msg.getArg( 1 ) instanceof Number ) {
					getGadget().setEnabled( ((Number) rom.msg.getArg( 1 )).intValue() != 0 );
				} else {
					OSCRoot.failedArgType( rom, 1 );
				}
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
	}
	
	private static class OSCGadgetLabel
	extends OSCGadgetNode
	{
		private JLabel	gg	= null;
	
		private OSCGadgetLabel( OSCWindow parent, int id, SocketAddress addr )
		{
			super( parent, id, addr );
		}
		
		public JComponent createGadget()
		{
			if( gg != null ) throw new IllegalStateException();
			gg = new JLabel();
			return gg;
		}
		
		protected JComponent getGadget()
		{
			return gg;
		}

		public String getText()
		{
			return gg.getText();
		}
		
//		public void dispose()
//		{
//			super.dispose();
//		}
		
		public void oscCmd_setText( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				gg.setText( rom.msg.getArg( 1 ).toString() );
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
	}

	private static class OSCGadgetSlider
	extends OSCGadgetNode
	implements ChangeListener
	{
		private JSlider		gg		= null;
		private final int	orient;
	
		private OSCGadgetSlider( OSCWindow parent, int id, SocketAddress addr, int orient )
		{
			super( parent, id, addr );
			this.orient	= orient;
		}
		
		public JComponent createGadget()
		{
			if( gg != null ) throw new IllegalStateException();
			gg = new JSlider( orient );
			gg.setMaximum( 0x10000 );
			gg.addChangeListener( this );
			return gg;
		}
		
		protected JComponent getGadget()
		{
			return gg;
		}

		public String getText()
		{
			return( "Slider (" + getID() + ")" );
		}
		
		public void dispose()
		{
			if( gg != null ) gg.removeChangeListener( this );
			super.dispose();
		}
		
		public void oscCmd_setValue( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				if( rom.msg.getArg( 1 ) instanceof Number ) {
					gg.setValue( (int) (((Number) rom.msg.getArg( 1 )).doubleValue() * 0x10000 + 0.5) );
				} else {
					OSCRoot.failedArgType( rom, 1 );
				}
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
		
		// ---------- ChangeListener interface ----------
		
		public void stateChanged( ChangeEvent e )
		{
			parent.reply( this, new Object[] { "value", new Double( (double) gg.getValue() / 0x10000 ), "adjusting",
				new Integer( gg.getValueIsAdjusting() ? 1 : 0 )});
		}
	}

	private static class OSCGadgetChoice
	extends OSCGadgetNode
	implements ActionListener
	{
		private JComboBox	gg	= null;
	
		private OSCGadgetChoice( OSCWindow parent, int id, SocketAddress addr )
		{
			super( parent, id, addr );
		}
		
		public JComponent createGadget()
		{
			if( gg != null ) throw new IllegalStateException();
			gg = new JComboBox();
			gg.addActionListener( this );
			return gg;
		}
		
		protected JComponent getGadget()
		{
			return gg;
		}

		public String getText()
		{
			return( "Choice (" + getID() + ")" );
		}
		
		public void dispose()
		{
			if( gg != null ) gg.removeActionListener( this );
			super.dispose();
		}
		
		public void oscCmd_setValue( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				if( rom.msg.getArg( 1 ) instanceof Number ) {
					gg.setSelectedIndex( (int) (((Number) rom.msg.getArg( 1 )).intValue()) );
				} else {
					OSCRoot.failedArgType( rom, 1 );
				}
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
		
		public void oscCmd_setItems( RoutedOSCMessage rom )
		{
			gg.removeAllItems();
			for( int i = 1; i < rom.msg.getArgCount(); i++ ) {
				gg.addItem( rom.msg.getArg( i ));
			}
		}
		
		// ---------- ActionListener interface ----------
		
		public void actionPerformed( ActionEvent e )
		{
			parent.reply( this, new Object[] { "value", new Integer( gg.getSelectedIndex() )});
		}
	}

	private static class OSCGadgetCheck
	extends OSCGadgetNode
	implements ActionListener
	{
		private JCheckBox	gg	= null;
	
		private OSCGadgetCheck( OSCWindow parent, int id, SocketAddress addr )
		{
			super( parent, id, addr );
		}
		
		public JComponent createGadget()
		{
			if( gg != null ) throw new IllegalStateException();
			gg = new JCheckBox();
			gg.addActionListener( this );
			return gg;
		}
		
		protected JComponent getGadget()
		{
			return gg;
		}

		public String getText()
		{
			return( gg.getText() );
		}
		
		public void dispose()
		{
			if( gg != null ) gg.removeActionListener( this );
			super.dispose();
		}
		
		public void oscCmd_setValue( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				if( rom.msg.getArg( 1 ) instanceof Number ) {
					gg.setSelected( (int) ((Number) rom.msg.getArg( 1 )).intValue() != 0 );
				} else {
					OSCRoot.failedArgType( rom, 1 );
				}
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}

		public void oscCmd_setText( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				gg.setText( rom.msg.getArg( 1 ).toString() );
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
		
		// ---------- ActionListener interface ----------
		
		public void actionPerformed( ActionEvent e )
		{
			parent.reply( this, new Object[] { "value", new Integer( gg.isSelected() ? 1 : 0 )});
		}
	}

	private static class OSCGadgetButton
	extends OSCGadgetNode
	implements ActionListener
	{
		private JButton	gg	= null;
	
		private OSCGadgetButton( OSCWindow parent, int id, SocketAddress addr )
		{
			super( parent, id, addr );
		}
		
		public JComponent createGadget()
		{
			if( gg != null ) throw new IllegalStateException();
			gg = new JButton();
			gg.addActionListener( this );
			return gg;
		}
		
		protected JComponent getGadget()
		{
			return gg;
		}

		public String getText()
		{
			return( gg.getText() );
		}
		
		public void dispose()
		{
			if( gg != null ) gg.removeActionListener( this );
			super.dispose();
		}
		
		public void oscCmd_setText( RoutedOSCMessage rom )
		{
			if( rom.msg.getArgCount() == 2 ) {
				gg.setText( rom.msg.getArg( 1 ).toString() );
			} else {
				OSCRoot.failedArgCount( rom );
			}
		}
		
		// ---------- ActionListener interface ----------
		
		public void actionPerformed( ActionEvent e )
		{
			parent.reply( this );
		}
	}
*/
}