/*
 *  BasicStake.java
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
	
	public Enumeration children()
	{
		return null;
	}
}
