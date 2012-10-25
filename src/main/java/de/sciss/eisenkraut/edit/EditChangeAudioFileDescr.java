/*
 *  EditChangeAudioFileDescr.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *		17-Sep-05	copied from de.sciss.inertia.edit.EditPutMapValue
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.io.AudioFileDescr;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 */
public class EditChangeAudioFileDescr
extends BasicUndoableEdit
{
	private Object					source;
	private final AudioFileDescr	afd;
	private final String			key;
	private final Object			oldValue, newValue;
	private final String			name;

	/**
	 */
	public EditChangeAudioFileDescr( Object source, AudioFileDescr afd, String key, Object value, String name )
	{
		super();
		this.source			= source;
		this.afd			= afd;
		this.key			= key;
		newValue			= value;
		oldValue			= afd.getProperty( key );
		this.name			= name;
	}

	public EditChangeAudioFileDescr( Object source, AudioFileDescr afd, String key, Object value )
	{
		this( source, afd, key, value, null );
	}

	public PerformableEdit perform()
	{
		afd.setProperty( source, key, newValue );
		source = this;
		return this;
	}

	public void undo()
	{
		super.undo();
		afd.setProperty( source, key, oldValue );
	}
	
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return name == null ? getResourceString( "editChangeAFD" ) : name;
	}
}