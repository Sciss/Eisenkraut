/*
 *  TransferableCollection.java
 *  Eisenkraut
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
 *		07-Feb-05	created from de.sciss.meloncillo.util.Meloncillo
 */

package de.sciss.eisenkraut.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**
 *  A special transferable object which
 *  wraps a collection of individual transferable
 *  objects. For example, if the user selects
 *  a collection of receivers and chooses Cut from
 *  the edit menu, a <code>TransferableCollection</code>
 *  is created containing these receivers.
 *  <p>
 *  In the a pasting situation, the clipboard managing
 *  object obtains a <code>java.awt.List</code> object
 *  through the <code>getTransferData</code> method.
 *  It should traverse this list and check if an element
 *  is instanceof <code>Transferable</code> (this is guaranteed but
 *  checking maintains cleaner code). It can then check
 *  if the individual <code>Transferable</code> can
 *  provide a requested data format.
 */
public class TransferableCollection
		implements Transferable {

	/**
	 *  The flavor used to
	 *  identify a <code>TransferableCollection</code>
	 */
	public static final DataFlavor collectionFlavor = new DataFlavor( List.class, null );

	private final Vector<Transferable> collTransferables;
	private final DataFlavor[]	collFlavors;

	/**
	 *  Constructs a new <code>TransferableCollection</code>
	 *  backup'ed by a list of <code>Transferable</code> objects.
	 *  The list elements are copied to a new list, hence it's safe
	 *  to modify the list afterwards.
	 *
	 *  @param  coll	a list containing elements which implement the
	 *					<code>Transferable</code> interface. Each element's
	 *					flavor is added to the flavor list which is checked
	 *					in a call to <code>isDataFlavorSupported</code>!
	 *
	 *  @throws IllegalArgumentException	if the list contains an object
	 *										which does not implement the Transferable
	 *										interface
	 */
	public TransferableCollection(List<Transferable> coll) {
		collTransferables = new Vector<Transferable>();

		int i, j, k;
		Object o;
		DataFlavor[] flavorArray;
		DataFlavor flavor;
		Vector<DataFlavor> v = new Vector<DataFlavor>();

		for (i = 0; i < coll.size(); i++) {
			o = coll.get(i);
			if (!(o instanceof Transferable)) throw new IllegalArgumentException();
			Transferable t = (Transferable) o;
			flavorArray = ((Transferable) o).getTransferDataFlavors();
		collFlavLp:
			for (j = 0; j < flavorArray.length; j++) {
				flavor = flavorArray[j];
				for (k = 0; k < v.size(); k++) {
					if (v.get(k).equals(flavor)) continue collFlavLp;
				}
				v.add(flavor);
			}
			collTransferables.add(t);
		}
		v.add( collectionFlavor );

		collFlavors = new DataFlavor[v.size()];
		for (i = 0; i < v.size(); i++) {
			collFlavors[i] = v.get(i);
		}
	}

// ---------------- Transferable interface ---------------- 

	/**
	 *  Queries the available flavors.
	 *
	 *  @return an array of available flavors. This is a sum
	 *			of <code>collectionFlavor</code> and all
	 *			individual flavors of the list
	 */
	public DataFlavor[] getTransferDataFlavors()
	{
		return( collFlavors );
	}
	
	/**
	 *  Checks if a certain flavor is available.
	 *
	 *  @return <code>true</code>, if at least one of the list's
	 *			elements supports the given flavor, or if <code>flavor</code>
	 *			equals <code>collectionFlavor</code>.
	 */
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		for (DataFlavor collFlavor : collFlavors) {
			if (collFlavor.equals(flavor)) return true;
		}
		return false;
	}

	/**
	 *  Returns the transfer data. If <code>flavor</code> equals
	 *  <code>collectionFlavor</code>,
	 *  a <code>java.util.List</code> containing <code>Transferable</code> objects is
	 *  returned. Otherwise the list is traversed until <strong>one</strong>
	 *  transferable is found which supports the given flavor. This object is
	 *  returned.
	 *
	 *  @param  flavor  the transferable object(s) will be returned in this flavor.
	 *  @return either  a list if flavor is collectionFlavor, otherwise the transfer data
	 *					of the first list item supporting the given flavor
	 *  @throws UnsupportedFlavorException  if none of the items supports the given flavor
	 *  @throws IOException					if the data is no longer available in the requested flavor
	 */
	public Object getTransferData(DataFlavor flavor)
			throws UnsupportedFlavorException, IOException {
		if (flavor.equals(collectionFlavor)) return new Vector<Transferable>(collTransferables);

		Object o;

		for (Transferable collTransferable : collTransferables) {
			o = collTransferable;
			if (o instanceof Transferable) {
				if (((Transferable) o).isDataFlavorSupported(flavor)) {
					return (((Transferable) o).getTransferData(flavor));
				}
			}
		}
		throw new UnsupportedFlavorException( flavor );
	}
}