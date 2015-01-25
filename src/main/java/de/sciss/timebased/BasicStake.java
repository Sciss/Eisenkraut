/*
 *  BasicStake.java
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
 *		24-Feb-06	created
 */

package de.sciss.timebased;

import java.util.Enumeration;
import javax.swing.tree.TreeNode;

import de.sciss.io.Span;

public abstract class BasicStake
implements Stake
{
	protected final Span	span;
	protected Trail			trail	= null;

	protected BasicStake( Span span )
	{
		this.span	= span;
	}

	public Span	getSpan()
	{
		return span;
	}
	
	public void dispose()
	{
		trail	= null;
	}

	public void setTrail( Trail trail )
	{
		this.trail	= trail;
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
		return trail;
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
