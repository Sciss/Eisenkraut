/*
 *  ProgressPanel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		14-Apr-06	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.Component;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.ProgressBar;
import de.sciss.gui.ProgressComponent;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class ProgressPanel
extends JPanel
implements ProgressComponent
{
	private final ProgressBar			pb;
	private final JLabel				lb;
	private final ModificationButton	ggCancel;
	
	public ProgressPanel()
	{
		super();
		
		pb			= new ProgressBar();
		lb			= new JLabel( "", SwingConstants.RIGHT );
		lb.setBorder( BorderFactory.createEmptyBorder( 0, 8, 0, 4 )); // T L B R
		ggCancel	= new ModificationButton( ModificationButton.SHAPE_ABORT );
		ggCancel.setEnabled( false );

		setLayout( new BoxLayout( this, BoxLayout.X_AXIS ));
		add( lb );
		add( pb );
		add( ggCancel );
	}
	
// ---------------- ProgressComponent interface ---------------- 

	public void addCancelListener( ActionListener l )
	{
		ggCancel.addActionListener( l );
	}
	
	public void removeCancelListener( ActionListener l )
	{
		ggCancel.removeActionListener( l );
	}

	public Component getComponent()
	{
		return this;
	}
	
	public void resetProgression()
	{
		pb.reset();
		ggCancel.setEnabled( true );
	}
	
	public void setProgression( float p )
	{
		if( p >= 0 ) {
			pb.setProgression( p );
		} else {
			pb.setIndeterminate( true );
		}
	}
	
	public void	finishProgression( int result )
	{
		pb.finish( result );
		ggCancel.setEnabled( false );
	}
	
	public void setProgressionText( String text )
	{
		lb.setText( text );
	}

	public void displayError( Exception e, String processName )
	{
		BasicWindowHandler.showErrorDialog( this, e, processName );
	}

	public void showMessage( int type, String text )
	{
System.out.println( text );
/*
		// potentially condidates of unicodes
		// for the different messages types are:
		// ERROR_MESSAGE		2620  21AF
		// INFORMATION_MESSAGE  24D8'(i)' 2148'i' 2139'i'
		// PLAIN_MESSAGE
		// QUESTION_MESSAGE		2047
		// WARNING_MESSAGE		261D  2297'X'  203C

		// the print stream is using bytes not unicode,
		// therefore the 'icons' are appended directly
		// to the textarea (so they won't appear in a
		// logfile which is quite unnecessary anyway).
		switch( type ) {
		case JOptionPane.ERROR_MESSAGE:
			lta.append( "\u21AF " );		// Blitz
			break;
		case JOptionPane.INFORMATION_MESSAGE:
			lta.append( "\u263C " );		// Sun
			break;
		case JOptionPane.QUESTION_MESSAGE:
			lta.append( "\u2047 " );		// '??'
			break;
		case JOptionPane.WARNING_MESSAGE:
			lta.append( "\u203C " );		// '!!'
			break;
		default:
			lta.append( "   " );
			break;
		}
		// due to inserting unicode characters we have to
		// advance manually to keep the scrollpane working for us.
// 		lta.setCaretPosition( lta.getText().length() );
		logStream.println( text );
*/
	}
}
