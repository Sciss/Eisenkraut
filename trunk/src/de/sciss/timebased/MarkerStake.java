/*
 *  MarkerStake.java
 *  de.sciss.timebased package
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
 *		06-Jan-06	created
 */

package de.sciss.timebased;

import java.util.Enumeration;
import javax.swing.tree.TreeNode;

import de.sciss.io.Marker;
import de.sciss.io.Span;

public class MarkerStake
extends Marker
implements Stake
{
	private final Span	span;
	private Trail		t		= null;

	public MarkerStake( long pos, String name )
	{
		super( pos, name );
		span = new Span( pos, pos );
	}
	
	public MarkerStake( Marker orig )
	{
		super( orig );
		span = new Span( pos, pos );
	}
	
	public Stake duplicate()
	{
		return new MarkerStake( this );
	}

	public Span	getSpan()
	{
		return span;
	}
	
	public void dispose()
	{
		t	= null;
	}
	
	public Stake replaceStart( long newStart )
	{
		return new MarkerStake( newStart, name );
	}
	
	public Stake replaceStop( long newStop )
	{
		return new MarkerStake( newStop, name );
	}
	
	public Stake shiftVirtual( long delta )
	{
		return new MarkerStake( pos + delta, name );
	}

	public void setTrail( Trail t )
	{
		this.t	= t;
	}

// ---------------- TreeNode interface ---------------- 

	public TreeNode getChildAt( int childIndex )
	{
		return null;
	}
	
	public int getChildCount()
	{
		return 0;
	}
	
	public TreeNode getParent()
	{
		return t;
	}
	
	public int getIndex( TreeNode node )
	{
		return -1;
	}
	
	public boolean getAllowsChildren()
	{
		return false;
	}
	
	public boolean isLeaf()
	{
		return true;
	}
	
	public Enumeration children()
	{
		return null;
	}
}