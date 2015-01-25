/*
 *  MarkerStake.java
 *  de.sciss.timebased package
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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

@SuppressWarnings("serial")
public class MarkerStake
		extends Marker
		implements Stake {

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
	
	public Enumeration<?> children()
	{
		return null;
	}
}