/*
 *  ComponentBoundsRestrictor.java
 *  de.sciss.gui
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
 *		05-Feb-08	created
 */
package de.sciss.gui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 05-Feb-08
 */
public class ComponentBoundsRestrictor
extends ComponentAdapter
{
	public static int		NO_MIN		= Integer.MIN_VALUE;
	public static int		NO_MAX		= Integer.MAX_VALUE;
	
//	public static int		MOVE		= 0;	// left/top/right/bottom policy
//	public static int		RESIZE		= 1;	// left/top/right/bottom policy
//	public static int		MOVE_LEFT	= 0;	// width policy
//	public static int		MOVE_RIGHT	= 1;	// width policy
//	public static int		MOVE_TOP	= 0;	// height policy
//	public static int		MOVE_BOTTOM	= 1;	// height policy

	private int				minLeft			= NO_MIN;
	private int				maxLeft			= NO_MAX;
	private int				minTop			= NO_MIN;
	private int				maxTop			= NO_MAX;
	private int				minRight		= NO_MIN;
	private int				maxRight		= NO_MAX;
	private int				minBottom		= NO_MIN;
	private int				maxBottom		= NO_MAX;
	private int				minWidth		= NO_MIN;
	private int				maxWidth		= NO_MAX;
	private int				minHeight		= NO_MIN;
	private int				maxHeight		= NO_MAX;
	
//	private int				minLeftPolicy	= MOVE;
//	private int				maxLeftPolicy	= MOVE;
//	private int				minTopPolicy	= NO_MIN;
//	private int				maxTopPolicy	= NO_MAX;
//	private int				minRightPolicy	= NO_MIN;
//	private int				maxRightPolicy	= NO_MAX;
//	private int				minBottomPolicy	= NO_MIN;
//	private int				maxBottomPolicy	= NO_MAX;
//	private int				minWidthPolicy	= NO_MIN;
//	private int				maxWidthPolicy	= NO_MAX;
//	private int				minHeightPolicy	= NO_MIN;
//	private int				maxHeightPolicy	= NO_MAX;

	private final Map		map				= new HashMap();
	private final Rectangle	r				= new Rectangle();
	
	public ComponentBoundsRestrictor()
	{
		/* empty */ 
	}
	
	public void add( Component c )
	{
		final Rectangle rc = c.getBounds();
		if( map.put( c, rc ) != null ) {
			throw new IllegalArgumentException( "Component was already added" );
		}
		c.addComponentListener( this );
		restrict( c, rc );
	}
	
	public void remove( Component c )
	{
		if( map.remove( c ) == null ) {
			throw new IllegalArgumentException( "Component was not added" );
		}
		c.removeComponentListener( this );
	}
	
	public void setMinimumLeft( int value )
	{
		minLeft = value;
		restrict();
	}
	
	public void setMaximumLeft( int value )
	{
		maxLeft = value;
		restrict();
	}

	public void setMinimumTop( int value )
	{
		minTop = value;
		restrict();
	}

	public void setMaximumTop( int value )
	{
		maxTop = value;
		restrict();
	}

	public void setMinimumBottom( int value )
	{
		minBottom = value;
		restrict();
	}

	public void setMaximumBottom( int value )
	{
		maxBottom = value;
		restrict();
	}

	public void setMinimumRight( int value )
	{
		minRight = value;
		restrict();
	}

	public void setMaximumRight( int value )
	{
		maxRight = value;
		restrict();
	}

	public void setMinimumWidth( int value )
	{
		minWidth = value;
		restrict();
	}

	public void setMaximumWidth( int value )
	{
		maxWidth = value;
		restrict();
	}

	public void setMinimumHeight( int value )
	{
		minHeight = value;
		restrict();
	}

	public void setMaximumHeight( int value )
	{
		maxHeight = value;
		restrict();
	}

	public void componentResized( ComponentEvent e )
	{
		final Component c = e.getComponent();
		restrict( c, (Rectangle) map.get( c ));
	}
	
	public void componentMoved( ComponentEvent e )
	{
		final Component c = e.getComponent();
		restrict( c, (Rectangle) map.get( c ));
	}

	private void restrict()
	{
		Map.Entry me;
		for( Iterator iter = map.entrySet().iterator(); iter.hasNext(); ) {
			me = (Map.Entry) iter.next();
			restrict( (Component) me.getKey(), (Rectangle) me.getValue() );
		}
	}
	
	private void restrict( Component c, Rectangle rc )
	{
		c.getBounds( r );
		boolean b = false;
		if( r.x < minLeft ) {
			b			= true;
			if( (r.x + r.width) == (rc.x + rc.width) ) {	// keep right side constant
				r.width	-= minLeft - r.x;
			}
			r.x			= minLeft;
		} else if( r.x > maxLeft ) {
			b			= true;
			if( (r.x + r.width) == (rc.x + rc.width) ) {	// keep right side constant
				r.width	-= maxLeft - r.x;
			}
			r.x			= maxLeft;
		}
		if( r.y < minTop ) {
			b			= true;
			if( (r.y + r.height) == (rc.y + rc.height) ) {	// keep bottom side constant
				r.height -= minTop - r.y;
			}
			r.y			= minTop;
		} else if( r.y > maxTop ) {
			b			= true;
			if( (r.y + r.height) == (rc.y + rc.height) ) {	// keep bottom side constant
				r.height -= maxTop - r.y;
			}
			r.y			= maxTop;
		}
		if( (r.x + r.width) < minRight ) {
			b			= true;
			if( r.x == rc.x ) {								// keep left side constant
				r.width	= minRight - r.x;
			} else {
				r.x		= minRight - r.width;
			}
		} else if( (r.x + r.width) > maxRight ) {
			b			= true;
			if( r.x == rc.x ) {								// keep left side constant
				r.width	= maxRight - r.x;
			} else {
				r.x		= maxRight - r.width;
			}
		}
		if( (r.y + r.height) < minBottom ) {
			b			= true;
			if( r.y == rc.y ) {								// keep top side constant
				r.height= minBottom - r.y;
			} else {
				r.y		= minBottom - r.height;
			}
		} else if( (r.y + r.height) > maxBottom ) {
			b			= true;
			if( r.y == rc.y ) {								// keep top side constant
				r.height= maxBottom - r.y;
			} else {
				r.y		= maxBottom - r.height;
			}
		}
		if( r.width < minWidth ) {
			b			= true;
			if( (r.x + r.width) == (rc.x + rc.width) ) {	// keep right side constant
				r.x	-= minWidth - r.width;
			}
			r.width		= minWidth;
		} else if( r.width > maxWidth ) {
			b			= true;
			if( (r.x + r.width) == (rc.x + rc.width) ) {	// keep right side constant
				r.x	-= maxWidth - r.width;
			}
			r.width		= maxWidth;
		}
		if( r.height < minHeight ) {
			b			= true;
			if( (r.y + r.height) == (rc.y + rc.height) ) {	// keep right side constant
				r.y	-= minHeight - r.height;
			}
			r.height	= minHeight;
		} else if( r.height > maxHeight ) {
			b			= true;
			if( (r.y + r.height) == (rc.y + rc.height) ) {	// keep right side constant
				r.y	-= maxHeight - r.height;
			}
			r.height	= maxHeight;
		}
		rc.setBounds( r );
		if( b ) {
//			System.out.println( "setBounds( " + r + ")" );
			c.setBounds( r );
		}
	}
	
	public static void test()
	{
		final JFrame					f	= new JFrame( "Test" );
		final JLabel					lb	= new JLabel( "", SwingConstants.CENTER );
		final Rectangle					r	= new Rectangle();
		final ComponentBoundsRestrictor	cbr = new ComponentBoundsRestrictor();
		f.getContentPane().add( lb );
		f.addComponentListener( new ComponentAdapter() {
			private void update()
			{
				f.getBounds( r );
				lb.setText( "L " + r.x + ", T " + r.y + ", R " + (r.x + r.width) +
				            ", B " + (r.y + r.height) + ", W " + r.width +
				            ", H " + r.height );
			}
			
			public void componentResized( ComponentEvent e )
			{
				update();
			}

			public void componentMoved( ComponentEvent e )
			{
				update();
			}
		});
		cbr.setMinimumLeft( 100 );
		cbr.setMinimumTop( 200 );
		cbr.setMinimumWidth( 300 );
		cbr.setMaximumWidth( 330 );
		cbr.setMinimumHeight( 120 );
		cbr.setMaximumHeight( 150 );
		cbr.setMaximumRight( 700 );
		cbr.setMaximumBottom( 500 );
		cbr.add( f );
		f.setVisible( true );
	}
}
