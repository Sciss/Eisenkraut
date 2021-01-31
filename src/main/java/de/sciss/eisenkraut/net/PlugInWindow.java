/*
 *  PlugInWindow.java
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

import java.awt.Component;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JComponent;

import de.sciss.app.AbstractApplication;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.ShowWindowAction;

public class PlugInWindow extends AppWindow {
    public static final int	FLAG_UNDECORATED    = 0x01;
    public static final int FLAG_SCROLL_PANE    = 0x02;
    public static final int FLAG_NO_RESIZE 	    = 0x04;

    private final ShowWindowAction	actionShowWindow;
    private final BasicMenuFactory	mf;

    private final Map<WindowListener, Listener> winL		= new HashMap<WindowListener, Listener>();

    public PlugInWindow(String title, Rectangle cocoaBounds, int flags) {
        super( SUPPORT );
        actionShowWindow	= new ShowWindowAction( this );
        mf					= ((BasicApplication) AbstractApplication.getApplication()).getMenuFactory();
        mf.addToWindowMenu( actionShowWindow );	// MUST BE BEFORE INIT()!!
        init();
        setTitle( title );	// needs to be after init. WHY?

//		if( (flags & FLAG_UNDECORATED) != 0 ) {
//			setUndecorated( true );
//		}
        if( (flags & FLAG_NO_RESIZE) != 0 ) {
            setResizable( false );
        }
        JComponent topView;
        try {
            final ClassLoader cl = OSCRoot.getInstance().getGUI().getSwingOSC().getClass().getClassLoader();
            topView = (JComponent) Class.forName( "de.sciss.swingosc.ContentPane", true, cl ).getConstructor(Boolean.TYPE).newInstance((flags & FLAG_SCROLL_PANE) == 0);
            if( (flags & FLAG_SCROLL_PANE) != 0 ) {
//				topView		= new ContentPane( false );
//				final JScrollPane scrollPane = new ScrollPane( topView ); // ...SCROLLBAR_AS_NEEDED
                final JComponent scrollPane = (JComponent) Class.forName( "de.sciss.swingosc.ScrollPane", true, cl ).getConstructor(Component.class).newInstance(topView);
//				scrollPane.setViewportBorder( null );
//				scrollPane.setBorder( null );
                setContentPane( scrollPane );
            } else {
//				topView		= new ContentPane( true );
                setContentPane(topView);
            }
        }
        catch( Exception e ) {
            mf.removeFromWindowMenu( actionShowWindow );
            e.printStackTrace();
            throw new IllegalStateException();
        }

        topView.setPreferredSize( cocoaBounds.getSize() );
        pack();	// frame is made displayable
        final Rectangle screenBounds = getWindow().getGraphicsConfiguration().getBounds();
        final Insets insets = getInsets();
        setLocation( new Point(
            screenBounds.x + cocoaBounds.x - insets.left,
            (screenBounds.y + screenBounds.height) - (cocoaBounds.y + cocoaBounds.height) - insets.top ));
    }

//	public JComponent getTopView()
//	{
//		return topView;
//	}

    public void setCocoaBounds(Rectangle r) {
        final Rectangle screenBounds = getWindow().getGraphicsConfiguration().getBounds();
        final Insets insets = getInsets();

        setBounds(new Rectangle(
                screenBounds.x + r.x - insets.left,
                (screenBounds.y + screenBounds.height) - (r.y + r.height) - insets.top,
                r.width + (insets.left + insets.right),
                r.height + (insets.top + insets.bottom)));
    }

    public void addComponentListener(ComponentListener l) {
        getWindow().addComponentListener(l);
    }

    public void removeComponentListener(ComponentListener l) {
        getWindow().removeComponentListener(l);
    }

    protected boolean alwaysPackSize() {
        return false;
    }

    public void setTitle(String title) {
        super.setTitle(title);
        actionShowWindow.putValue(Action.NAME, title);
    }

    public void dispose() {
        mf.removeFromWindowMenu(actionShowWindow);
        actionShowWindow.dispose();
        super.dispose();
    }

    protected WindowEvent windowEvent( Event e )
    {
//		return new WindowEvent( e.getWindow(), e.getID() );
// THROWS NULL SOURCE:
//		return new WindowEvent( null, e.getID() );	// dirty

        return null;	// extra cheesy
    }

    public void addWindowListener( final WindowListener wl )
    {
        final Listener l;

        l = new Adapter() {
            public void windowOpened( Event e )
            {
                wl.windowOpened( windowEvent( e ));
            }

            public void windowClosing( Event e )
            {
                wl.windowClosing( windowEvent( e ));
            }

            public void windowClosed( Event e )
            {
                wl.windowClosed( windowEvent( e ));
            }

            public void windowIconified( Event e )
            {
                wl.windowIconified( windowEvent( e ));
            }

            public void windowDeiconified( Event e )
            {
                wl.windowIconified( windowEvent( e ));
            }

            public void windowActivated( Event e )
            {
                wl.windowIconified( windowEvent( e ));
            }

            public void windowDeactivated( Event e )
            {
                wl.windowIconified( windowEvent( e ));
            }
        };

        addListener(l);
        winL.put(wl, l);
    }

    public void removeWindowListener(WindowListener wl) {
        final Listener l = winL.remove(wl);
        removeListener(l);
    }

    public void addWindowFocusListener(WindowFocusListener l) {
        // XXX nothing
    }

    public void removeWindowFocusListener(WindowFocusListener l) {
        // XXX nothing
    }
}