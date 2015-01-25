/*
 *	AbstractFlagsPanel.java
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

package de.sciss.eisenkraut.session;

import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import de.sciss.eisenkraut.gui.GraphicsUtil;

public abstract class AbstractFlagsPanel extends JPanel {
	// from de.sciss.meloncillo.session.SessionObject
	public static final int FLAGS_SOLO			= 0x01;
	public static final int FLAGS_MUTE			= 0x02;
	public static final int FLAGS_SOLOSAFE		= 0x04;
	public static final int FLAGS_VIRTUALMUTE	= 0x08;

	private final FlagAction actionSolo, actionMute;
	
	public AbstractFlagsPanel()
	{
		super( new FlowLayout( FlowLayout.RIGHT, 2, 2 ));
		
//		actionSolo	= new FlagAction( GraphicsUtil.ICON_SOLO, SessionObject.FLAGS_SOLO, SessionObject.FLAGS_SOLOSAFE );
//		actionMute	= new FlagAction( GraphicsUtil.ICON_MUTE, SessionObject.FLAGS_MUTE, SessionObject.FLAGS_VIRTUALMUTE );
		actionSolo	= new FlagAction( GraphicsUtil.ICON_SOLO, FLAGS_SOLO, FLAGS_SOLOSAFE );
		actionMute	= new FlagAction( GraphicsUtil.ICON_MUTE, FLAGS_MUTE, FLAGS_VIRTUALMUTE );

		add( actionSolo.getButton() );
		add( actionMute.getButton() );
	}

	protected void updateButtons( int flags )
	{
//		boolean practicallyMuted;
	
		actionSolo.set( flags );
		actionMute.set( flags );
//		if( (flags & SessionObject.FLAGS_SOLO) == 0 && (flags & SessionObject.FLAGS_SOLOSAFE) == 0 ) {
//			practicallyMuted = isAny( SessionObject.FLAGS_SOLO, true );
//		} else {
//			practicallyMuted = false;
//		}
//		actionMute.setThirdState( practicallyMuted );
	}
	
	protected abstract void setFlags( int mask, boolean set );
	protected abstract void broadcastFlags( int mask, boolean set );
	protected abstract boolean isAny( int mask, boolean set );

// ---------------- interne Klassen ---------------- 
	
	private abstract class TriStateAction
	extends AbstractAction
	{
		private final	Icon			normalState, thirdState;
		private final	AbstractButton	ab;
		private boolean					isNormal	= true;
		private final	Icon[]			icns;
	
		protected TriStateAction( int iconID, int normalID, int thirdID )
		{
			super();
			
			icns	= GraphicsUtil.createToolIcons( iconID );
			ab		= new JToggleButton( this );
			ab.setBorderPainted( false );
			ab.setContentAreaFilled( false );
			ab.setMargin( new Insets( 0, 0, 0, 0 ));
			ab.setOpaque( false );
			ab.setFocusable( false );
            ab.setBorder(null);
			GraphicsUtil.setToolIcons( ab, icns );
			
			normalState	= icns[ normalID ];
			thirdState		= icns[ thirdID ];
		}
		
		protected AbstractButton getButton()
		{
			return ab;
		}
		
		protected void setThirdState( boolean b )
		{
			if( b == isNormal ) {
				isNormal = !b;
				ab.setIcon( b ? thirdState : normalState );
			}
		}
		
		protected void setSelected( boolean b )
		{
			if( ab.isSelected() != b ) {
				ab.setSelected( b );
			}
		}
		
//		protected boolean isSelected()
//		{
//			return ab.isSelected();
//		}
	}

	@SuppressWarnings("serial")
	private class FlagAction extends TriStateAction {
		private final int		normalMask;
		private final int		thirdMask;
		private int				flags;
		private final boolean	isSolo;
	
		protected FlagAction( int iconID, int normalMask, int thirdMask )
		{
			super( iconID, 0, 2 );
			
			this.normalMask = normalMask;
			this.thirdMask	= thirdMask;
			isSolo		= iconID == GraphicsUtil.ICON_SOLO;
		}

		protected void set( int flags )
		{
			this.flags = flags;
//			if( thirdMask != 0 ) {
			setThirdState( (flags & thirdMask) != 0 );
//			}
			setSelected( (flags & normalMask) != 0 );
		}

		public void actionPerformed( ActionEvent e )
		{
			boolean		meta	= (e.getModifiers() & ActionEvent.META_MASK) != 0;
			boolean		alt		= (e.getModifiers() & ActionEvent.ALT_MASK) != 0;
			int			mask	= meta && isSolo ? thirdMask : normalMask;
			boolean		set		= alt && isSolo ? !isAny( mask, true ) : (flags & mask) == 0;
				
			if( alt ) {
				broadcastFlags( mask, set );
			} else {
				setFlags( mask, set );
			}
		}
	}
}
