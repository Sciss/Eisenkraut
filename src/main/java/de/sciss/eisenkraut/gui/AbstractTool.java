/*
 *  AbstractTool.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2013 Hanns Holger Rutz. All rights reserved.
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
 *		12-May-05	created from de.sciss.meloncillo.gui.AbstractTool
 *		24-Sep-05	implements KeyListener and requests focus on mouse press
 *		01-Oct-05	removed KeyListener, uses Input/ActionMap now because
 *					making the component focusable was destroying other keyboard bindings
 */

package de.sciss.eisenkraut.gui;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import de.sciss.gui.MenuAction;
import de.sciss.gui.TopPainter;

/**
 *  This class describes a generic GUI tool
 *  that can be aquired and dismissed by
 *  a <code>Component</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 28-Sep-07
 */
public abstract class AbstractTool
implements MouseListener, MouseMotionListener, TopPainter // KeyListener
{
	private Component comp;
	private MenuAction actionCancel	= null;

	protected AbstractTool()
	{
		/* empty */ 
	}

	/**
	 *  Makes this tool a component's active
	 *  tool. Subclasses should override this
	 *  method to perform additional initializations
	 *  but are obliged to call the super method,
	 *  which will store the component for the
	 *  <code>getComponent</code> method and will
	 *  activate Mouse and MouseMotion listening.
	 *
	 *  @param  c							the <code>Component</code> on which
	 *										this tool will operate.
	 *  @throws IllegalArgumentException	if the passed <code>Component</code>
	 *										is <code>null</code>
	 *  @throws IllegalStateException		if the tool was aquired before.
	 *  @synchronization	the method must be called in the event thread
	 */
	public void toolAcquired( Component c )
	{
		if( comp != null ) throw new IllegalStateException();
		if( c == null ) throw new IllegalArgumentException();

		comp = c;
		c.addMouseListener( this );
		c.addMouseMotionListener( this );
//		c.addKeyListener( this );

		if( comp instanceof JComponent ) {
			if( actionCancel == null ) actionCancel = new ActionCancel();
			actionCancel.installOn( (JComponent) comp, JComponent.WHEN_IN_FOCUSED_WINDOW );
		}
	}

	/**
	 *  Makes this tool inactive. This usually
	 *  happens when the user switches to
	 *  a different tool. Any ongoing drag- and
	 *  drop actions or the like are to be
	 *  cancelled upon invocation of this method.
	 *  Subclasses should override this
	 *  method to perform additional cleanup operations
	 *  but are obliged to call the super method,
	 *  which will forget the component so
	 *  <code>getComponent</code> will return
	 *  <code>null</code> afterwards, also it will
	 *  deactivate Mouse and MouseMotion listening
	 *
	 *  @param  c							the <code>Component</code> from
	 *										which the tool is removed.
	 *  @throws IllegalArgumentException	if the passed <code>Component</code>
	 *										is <code>null</code> or if the
	 *										tool was attached to a different
	 *										component.
	 *  @throws IllegalStateException		if the tool was not aquired before.
	 *  @synchronization	the method must be called in the event thread
	 */
	public void toolDismissed( Component c )
	{
		if( comp == null ) throw new IllegalStateException();
		if( c == null || c != comp ) throw new IllegalArgumentException();
	
		c.removeMouseMotionListener( this );
		c.removeMouseListener( this );
//		c.removeKeyListener( this );
		if( (actionCancel != null) && (comp instanceof JComponent) ) {
			actionCancel.deinstallFrom( (JComponent) comp, JComponent.WHEN_IN_FOCUSED_WINDOW );
		}
		
		cancelGesture();
		
		comp = null;
	}
	
	protected abstract void cancelGesture();

	/**
	 *  Paint the current (possibly volatile) state
	 *  of the tool performance. The should be called
	 *  at the end of the component's <code>paintComponent</code>
	 *  method. The tool is allowed to modify the <code>Stroke</code>
	 *  and the <code>Paint</code> of the <code>Graphics2D</code>
	 *  object without needing to restore it. The tool is responsible
	 *  for undoing any changes to the <code>AffineTransform</code>
	 *  of the <code>Graphics2D</code> object however.
	 *
	 *  @param  g   a <code>Graphics2D</code> object to paint on.
	 *				Initial <code>Stroke</code> and <code>Paint</code>
	 *				are undefinied. Initial transform should
	 *				be negotiated between the component
	 *				and the tool, e.g. the <code>Surface</code> object
	 *				may garantee to scale the graphics
	 *				to the virtual space (0, 0 ... 1, 1)
	 *  @see		javax.swing.JComponent#paintComponent( Graphics )
	 */
	public abstract void paintOnTop( Graphics2D g );

	/**
	 *  Returns the component on which the
	 *  tool operates.
	 *
	 *  @return the tool's <code>Component</code> or
	 *			<code>null</code> if the tool was dismissed.
	 */
	protected Component getComponent()
	{
		return comp;
	}
	
	public void mousePressed( MouseEvent e )
	{
//		final Component c = getComponent();	// needn't be the one from e.getComponent() !
//	
//		if( c != null ) c.requestFocus();

//System.err.println( "requesting focus on "+e.getComponent().getClass().getName() );
	}

	public void mouseReleased( MouseEvent e ) { /* empty */ }
	public void mouseClicked( MouseEvent e ) { /* empty */ }
	public void mouseDragged( MouseEvent e ) { /* empty */ }
	public void mouseEntered( MouseEvent e ) { /* empty */ }
	public void mouseExited( MouseEvent e ) { /* empty */ }
	public void mouseMoved( MouseEvent e ) { /* empty */ }

//	public void keyPressed( KeyEvent e )
//	{
//		boolean	consume	= true;
//	
//		switch( e.getKeyCode() ) {
//		case KeyEvent.VK_ESCAPE: // abort
//			cancelGesture();
//			break;
//			
//		default:
//			consume	= false;
//			break;
//		}
//			
//		if( consume ) e.consume();
//	}
//
//	public void keyReleased( KeyEvent e ) {}
//	public void keyTyped( KeyEvent e ) {}

	private class ActionCancel
	extends MenuAction
	{
		protected ActionCancel()
		{
			super( "tool-cancel", KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ));
		}
			
		public void actionPerformed( ActionEvent e ) {
			cancelGesture();
		}
	}
}