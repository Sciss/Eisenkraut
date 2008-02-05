/**
 *  StretchedGridLayout.java
 *  de.sciss.gui package
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
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
 *		29-Nov-07	created
 */
package de.sciss.gui;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * 	A variant of GridLayout that doesn't leave
 * 	empty spaces at the bottom or right, because
 * 	it properly stretches the widths and heights of
 *	the components over the available space
 *
 *	@author		Hanns Holger Rutz
 *	@version	0.10, 29-Nov-07
 */
public class StretchedGridLayout
extends GridLayout
{
    public StretchedGridLayout()
    {
    	super();
    }

    public StretchedGridLayout( int rows, int cols )
    {
    	super( rows, cols );
    }

    public StretchedGridLayout( int rows, int cols, int hgap, int vgap )
    {
    	super( rows, cols, hgap, vgap );
    }

    public void layoutContainer( Container parent )
	{
		synchronized( parent.getTreeLock() ) {
			final int		numComp		= parent.getComponentCount();
			if( numComp == 0 ) return;
			final Insets	insets		= parent.getInsets();
			final int		numRows;
			final int		numCols;
        	
			if( getRows() > 0 ) {
				numRows	= getRows();
				numCols	= (numComp + numRows - 1) / numRows;
			} else {
				numCols	= getColumns(); 
				numRows	= (numComp + numCols - 1) / numCols;
			}

			final int		hGap		= getHgap();
			final int		vGap		= getVgap();
			final int		w			= parent.getWidth()  - (insets.left + insets.right);
			final int		h			= parent.getHeight() - (insets.top + insets.bottom);

			int cx1, cy1, cx2, cy2 = 0;
			for( int row = 0, idx = 0; row < numRows; row++ ) {
				cy1	= cy2;
				cy2	= h * (row + 1) / numRows;
				cx2	= 0;
				for( int col = 0; col < numCols; col++, idx++ ) {
					cx1	= cx2;
					cx2	= w * (col + 1) / numCols;
					if( idx < numComp ) {
						parent.getComponent( idx ).setBounds(
							cx1 + insets.left, cy1 + insets.top, cx2 - cx1 - hGap, cy2 - cy1 - vGap );
					}
				}
			}
		}
	}
}
