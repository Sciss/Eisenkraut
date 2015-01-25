/*  PrefListModel.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

 package de.sciss.eisenkraut.gui;

import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
//import java.util.prefs.NodeChangeEvent;
//import java.util.prefs.NodeChangeListener;
import java.util.prefs.Preferences;

import javax.swing.AbstractListModel;
//import javax.swing.ListModel;
//import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import de.sciss.app.PreferenceNodeSync;
import de.sciss.util.Disposable;

/**
 *	Warning: Because java.util.Preferences is broken (childAdded never called)
 *				it is forbidden to change the preferences concurrently, since it's
 *				not monitored. Only commit changes by calling any of the method
 *				of the list model
 */
public abstract class PrefListModel
		extends AbstractListModel
		implements PreferenceNodeSync, Disposable {

	private Preferences prefs;
	private final List<Object> collData = new ArrayList<Object>();

	public PrefListModel() {
		 /* empty */
	}

	public void dispose() {
		if (prefs != null) {
			prefs = null;
		}
		collData.clear();
		final ListDataListener[] l = getListDataListeners();
		for (int i = l.length - 1; i >= 0; i--) {
			removeListDataListener(l[i]);
		}
	}

	public void setPreferences(Preferences prefs) {
		collData.clear();
		if (prefs != null) {
			final String[] names;
			try {
				names = prefs.childrenNames();
			} catch (BackingStoreException e1) {
				e1.printStackTrace();
				return;
			}
			Arrays.sort(names);
			for (int i = 0; i < names.length; i++) {
				if (!names[i].equals(String.valueOf(i))) {
					collData.clear();
					new IllegalStateException("Illegal preferences node '" + names[i] + "'").printStackTrace();
					return;
				}
				collData.add(dataFromNode(prefs.node(names[i])));
			}
//System.err.println( "addNodeChangeListener: " + prefs );
//			prefs.addNodeChangeListener( this );
		}
		this.prefs = prefs;
//System.err.println( "setPreferences. size = " + this.getSize() );
//		dispatch( new ListDataEvent( this, ListDataEvent.CONTENTS_CHANGED, 0, collData.size() ));
		fireContentsChanged(this, 0, collData.size());
	}
	
//	private void dispatch( final ListDataEvent e )
//	{
//		EventQueue.invokeLater( new Runnable() {
//			public void run()
//			{
//				ListDataListener l;
//				for( Iterator iter = collListeners.iterator(); iter.hasNext(); ) {
//					l = (ListDataListener) iter.next();
//					switch( e.getType() ) {
//					case ListDataEvent.CONTENTS_CHANGED:
//						l.contentsChanged( e );
//						break;
//					case ListDataEvent.INTERVAL_ADDED:
//						l.contentsChanged( e );
//						break;
//					case ListDataEvent.INTERVAL_REMOVED:
//						l.contentsChanged( e );
//						break;
//					default:
//						throw new IllegalArgumentException( String.valueOf( e.getType() ));
//					}
//				}
//			}
//		});
//	}

	public Object getElementAt( int index )
	{
		return collData.get( index );
	}
	
	/**
//	 *	Subclasses can override this method.
//	 *	By default the child prefs is returned directly.
//	 */
//	protected Object nodeToListData( Preferences child )
//	{
//		return child;
//	}
	
	public int getSize()
	{
		return collData.size();
	}
	
	public void remove( int index )
	throws BackingStoreException
	{
		if( (index < 0) || (index >= collData.size()) ) throw new ArrayIndexOutOfBoundsException();
		final Preferences node = prefs.node( String.valueOf( collData.size() - 1 ));
		node.removeNode();
		collData.remove( index );
		for( int i = index; i < collData.size(); i++ ) {
			dataToNode( i );
		}
	}
	
	public void add( Object o )
	{
		add( collData.size(), o );
	}
	
	public void add( int index, Object o )
	{
		collData.add( index, o );
		for( int i = collData.size() - 1; i >= index; i-- ) {
			dataToNode( i );
		}
		fireIntervalAdded( this, index, index + 1 );
	}
	
	private void dataToNode( int index )
	{
		if( prefs == null ) return;
		final Object		data	= collData.get( index );
		final String		name	= String.valueOf( index );
		final boolean		existed;
		
		try {
			existed = prefs.nodeExists( name );
			if( existed ) {
				prefs.node( name ).clear();
			}
		}
		catch( BackingStoreException e1 ) { e1.printStackTrace(); }
		dataToNode( data, prefs.node( name ));
	}

	protected abstract Object dataFromNode(Preferences node);

	protected abstract void dataToNode(Object data, Preferences node);
		
	public Object get( int index )
	{
		return getElementAt( index );
	}
	
//	public void addListDataListener( ListDataListener l )
//	{
//		collListeners.add( l );
//	}
//
//	public void removeListDataListener( ListDataListener l )
//	{
//		collListeners.remove( l );
//	}
	
//	public void childAdded( NodeChangeEvent e )
//	{
//System.err.println( "childAdded" );
//		final Preferences child = e.getChild();
//		collData.add( child );
//		dispatch( new ListDataEvent( this, ListDataEvent.INTERVAL_ADDED, collData.size() - 1, collData.size() ));
//	}
//
//	public void childRemoved( NodeChangeEvent e )
//	{
//System.err.println( "childRemoved" );
//		final Preferences child = e.getChild();
//		collData.remove( child );
//		dispatch( new ListDataEvent( this, ListDataEvent.INTERVAL_REMOVED, collData.size() - 1, collData.size() ));
//	}
}
