/*
 *  AudioTrackRowHeader.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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
 *		13-May-05	created from de.sciss.meloncillo.transmitter.TransmitterRowHeader
 *		15-Jul-05	fixes bottom gradient paint for variable row heights
 *		15-Jan-06	renamed from ChannelRowHeader to AudioTrackRowHeader ; moved to session package
 *					; extends TrackRowHeader
 */

package de.sciss.eisenkraut.timeline;

import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.undo.UndoManager;

import de.sciss.eisenkraut.gui.PanoramaButton;
import de.sciss.eisenkraut.session.FlagsPanel;
import de.sciss.eisenkraut.session.SessionCollection;

/**
 *	A row header in Swing's table 'ideology'
 *	is a component left to the leftmost
 *	column of each row in a table. It serves
 *	as a kind of label for that specific row.
 *	This class shows a header left to each
 *	sound file's waveform display, with information
 *	about the channel index, possible selections
 *	and soloing/muting. In the future it could
 *	carry insert effects and the like.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class AudioTrackRowHeader
extends TrackRowHeader
{
	private final PanoramaButton	pan;
	public static final int			ROW_WIDTH	= 64;
	
	/**
	 */
	public AudioTrackRowHeader( final AudioTrack t, final SessionCollection tracks,
								final SessionCollection selectedTracks, UndoManager undo )
	{
		super( t, tracks, selectedTracks, undo );
		
		final JPanel		flags;
		final SpringLayout	lay	= (SpringLayout) getLayout();
		
		pan			= new PanoramaButton( t, tracks );
		flags		= new FlagsPanel( t, tracks );
		add( pan );
		add( flags );
		lay.putConstraint( SpringLayout.EAST, flags, -4, SpringLayout.EAST, this );
		lay.putConstraint( SpringLayout.SOUTH, flags, -8, SpringLayout.SOUTH, this );
		lay.putConstraint( SpringLayout.EAST, pan, -3, SpringLayout.EAST, this );
		lay.putConstraint( SpringLayout.SOUTH, pan, 0, SpringLayout.NORTH, flags );
		setPreferredSize( new Dimension( ROW_WIDTH, 16 )); // XXX
		setMaximumSize( new Dimension( ROW_WIDTH, getMaximumSize().height )); // XXX

//		HelpGlassPane.setHelp( this, "ChannelTrack" );
    }
	
	public void dispose()
	{
		pan.dispose();
		super.dispose();
	}
}