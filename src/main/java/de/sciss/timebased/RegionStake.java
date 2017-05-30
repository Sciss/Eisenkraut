/*
 *  RegionStake.java
 *  de.sciss.timebased package
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
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

@SuppressWarnings("serial")
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
	
	public Enumeration<?> children()
	{
		return null;
	}
}