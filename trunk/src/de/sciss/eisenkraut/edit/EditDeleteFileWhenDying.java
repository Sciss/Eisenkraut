/*
 *  EditDeleteFileWhenDying.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2010 Hanns Holger Rutz. All rights reserved.
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
 *		20-Nov-05	created
 */

package de.sciss.eisenkraut.edit;

import java.io.File;
import java.io.IOException;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.io.InterleavedStreamFile;

/**
 *  An <code>UndoableEdit</code> that
 *  closes and deletes an <code>InterleavedStreamFile</code> (or <code>AudioFile</code>)
 *	when it's <code>die()</code> method is called.
 *	Alternatively, using the constructor with a plain <code>File</code>
 *	object will simply delete that file.
 *	Usefull for undoing recordings for example.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-May-06
 */
public class EditDeleteFileWhenDying
extends BasicUndoableEdit
{
	private final InterleavedStreamFile isf;
	private final File					f;

	/**
	 *  @param isf	the file to close and delete when <code>die()</code> is called
	 */
	public EditDeleteFileWhenDying( InterleavedStreamFile isf )
	{
		super();
		this.isf	= isf;
		f			= isf.getFile();
	}

	/**
	 *  @param f	the file to delete when <code>die()</code> is called
	 */
	public EditDeleteFileWhenDying( File f )
	{
		super();
		isf			= null;
		this.f		= f;
	}
	
	public PerformableEdit perform() { return this; }

	public void die()
	{
//System.err.println( "die!" );
		if( isf != null ) {
			try {
				isf.close();
			}
			catch( IOException e1 ) {
				System.err.println( "File not closed : " + f.getAbsolutePath() );
			}
		}
		if( f != null ) {
			if( !f.delete() ) {
				System.err.println( "File not deleted : " + f.getAbsolutePath() );
			}
		}
		super.die();
	}
}