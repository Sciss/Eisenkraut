/*
 *  Stake.java
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
 *		24-Jan-06	extends TreeNode
 */

package de.sciss.timebased;

import javax.swing.tree.TreeNode;

import de.sciss.io.Span;
import de.sciss.util.Disposable;

/**
 *	@author		Hanns Holger Rutz
 *	@version	0.12, 24-Jan-06
 */
public interface Stake
extends Disposable, TreeNode
{
	public Span	getSpan();
	public Stake duplicate();
	public void dispose();
	public Stake replaceStart( long newStart );
	public Stake replaceStop( long newStop );
	public Stake shiftVirtual( long delta );
	
	// like MutableTreeNode
	public void setTrail( Trail t );
}
