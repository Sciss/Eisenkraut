/*
 *  Stake.java
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
