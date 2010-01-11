/*
 *  RegionStake.java
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

import de.sciss.io.Region;
import de.sciss.io.Span;

public class RegionStake
extends Region
implements Stake
{
	private Trail	t	= null;

	public RegionStake( Span span, String name )
	{
		super( span, name );
	}
	
	public RegionStake( Region orig )
	{
		super( orig );
	}

	public Stake duplicate()
	{
		return new RegionStake( this );
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
		return new RegionStake( new Span( newStart, span.stop ), name );
	}
	
	public Stake replaceStop( long newStop )
	{
		return new RegionStake( new Span( span.start, newStop ), name );
	}
	
	public Stake shiftVirtual( long delta )
	{
		return new RegionStake( span.shift( delta ), name );
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