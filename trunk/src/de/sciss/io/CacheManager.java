/*
 *  CacheManager.java
 *  de.sciss.io package
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
 *		16-Jul-05	created
 *		23-Sep-05	fixes a problem of cache folder not automatically been generated
 *		28-Jul-07	refactored from de.sciss.eisenkraut.io.CacheManager
 */

package de.sciss.io;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Date;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.AbstractAction;
import javax.swing.Action;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.32, 25-Feb-08
 */
public class CacheManager
implements FilenameFilter
{
	private static final String	CACHE_EXT		= ".cache";
	private static final String	HEX_CHARS		= "0123456789ABCDEF";

	private File				folder			= null;
	private int					capacity;			// in MB
	private long				folderSize;			// in Bytes
	
	private SortedSet			cacheList		= new TreeSet();
	
	private boolean				active			= false;
	
	public CacheManager()
	{
		/* empty */
	}
	
	public void setActive( boolean onOff )
	{
		active = onOff;
		if( active ) makeSureFolderExists();
	}
	
	public boolean isActive()
	{
		return active;
	}

	public void addFile( File f )
	{
		CacheEntry ce = new CacheEntry( f );
		if( cacheList.add( ce )) {
			folderSize += ce.size;
			trimToCapacity();
		}
	}
	
	public void removeFile( File f )
	{
		CacheEntry ce = new CacheEntry( f );
		if( cacheList.remove( ce )) {
			folderSize -= ce.size;
		}
		if( f.exists() && !f.delete() ) {
			final String	refName		= f.getName();
			final int		suffixIdx	= refName.lastIndexOf( '.' );
			final String	name		= suffixIdx == -1 ? refName : refName.substring( 0, suffixIdx );
			final File		tempFile	= new File( folder, name + ".tmp" );
			tempFile.delete();
			f.renameTo( tempFile );
			tempFile.deleteOnExit();
		}
	}
	
	public File createCacheFileName( File reference )
	{
		final StringBuffer	strBuf	= new StringBuffer( 16 );
		int					hash	= reference.hashCode();

		for( int i = 0; i < 8; i++, hash >>= 4 ) {
			strBuf.append( HEX_CHARS.charAt( hash & 0x0F ));
		}
		strBuf.append( CACHE_EXT );
		
		return new File( folder, strBuf.toString() );
	}
	
	public void setFolder( String folder )
	{
		setFolder( new File( folder ));
	}

	public void setFolder( File folder )
	{
		setFolderAndCapacity( folder, this.capacity );
	}
	
	public File getFolder()
	{
		return folder;
	}

	public void setCapacity( int capacity )
	{
		setFolderAndCapacity( this.folder, capacity );
	}
	
	public int getCapacity()
	{
		return capacity;
	}

	public void setFolderAndCapacity( String folder, int capacity )
	{
		setFolderAndCapacity( new File( folder ), capacity );
	}

	public void setFolderAndCapacity( File folder, int capacity )
	{
		if( (folder == null) || !folder.equals( this.folder )) {
			if( this.folder != null ) {
				clearCache();
			}
			this.folder	= folder;
//			prefs.put( KEY_FOLDER, folder.getPath() );
			if( isActive() ) makeSureFolderExists();
		}
		if( this.capacity != capacity ) {
			this.capacity	= capacity;
//			prefs.put( KEY_CAPACITY, new Param( capacity, ParamSpace.NONE | ParamSpace.ABS ).toString() );
		}
		
		updateFileList();
		trimToCapacity();
	}
	
	private void makeSureFolderExists()
	{
		if( folder != null ) folder.mkdirs();
	}
	
	private void updateFileList()
	{
		final File[] files = folder == null ? null : folder.listFiles( this );
		cacheList.clear();
		folderSize = 0;
		if( files == null ) return;
		
		CacheEntry ce;
		
		for( int i = 0; i < files.length; i++ ) {
			ce			= new CacheEntry( files[ i ]);
			cacheList.add( ce );
			folderSize += ce.size;
		}
	}
	
	private void trimToCapacity()
	{
		CacheEntry ce;
		long	capaBytes	= (long) capacity * 0x100000;	// megabyte -> byte
	
		while( folderSize > capaBytes ) {
			ce = (CacheEntry) cacheList.first();
			cacheList.remove( ce );
			folderSize -= ce.size;
			if( !ce.file.delete() ) {
				ce.file.deleteOnExit();
			}
		}
	}
	
	private void clearCache()
	{
		CacheEntry ce;
	
		while( !cacheList.isEmpty() ) {
			ce = (CacheEntry) cacheList.last();
			cacheList.remove( ce );
			if( !ce.file.delete() ) {
				ce.file.deleteOnExit();
			}
		}
		folderSize = 0;
	}
	
	/**
	 *  Get an Action object that will dump the
	 *  current cache status to the console.
	 *
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
	public Action getDebugDumpAction()
	{
		return new ActionDebugDump();
	}
	
	protected void debugDump()
	{
		System.err.println( "WaveformCacheManager " + this.hashCode() + "; active ? " + isActive() +
			"; cache folder = " + (folder == null ? "null" : folder.getAbsolutePath()) +
			"; listed files = " + cacheList.size() +"; listed size = " + (folderSize / 0x100000) +
			"MB (capacity = " + capacity +" MB)" );
		if( !cacheList.isEmpty() ) {
			System.err.println( "Oldest file = " + new Date( ((CacheEntry) cacheList.first()).lastModified ).toString() );
			System.err.println( "Newest file = " + new Date( ((CacheEntry) cacheList.last()).lastModified ).toString() );
		}
	}
	
// ------- FilenameFilter interface -------

	public boolean accept( File dir, String name )
	{
		return( name.endsWith( CACHE_EXT ));
	}
	
// ------- internal classes -------

	private class ActionDebugDump
	extends AbstractAction
	{
		protected ActionDebugDump()
		{
			super( "Dump Waveform Cache" );
		}

		public void actionPerformed( ActionEvent e )
		{
			debugDump();
		}
	}
	
	private static class CacheEntry
	implements Comparable
	{
		protected final File	file;
		protected final long	size;
		protected final long	lastModified;
		
		protected CacheEntry( File file )
		{
			this.file			= file;
			this.size			= file.length();
			this.lastModified	= file.lastModified();
		}
		
		public int hashCode()
		{
			return file.hashCode();
		}
		
		public boolean equals( Object o )
		{
			if( o != null ) {
				if( o instanceof CacheEntry ) {
					return( this.file.equals( ((CacheEntry) o).file ));
				} else if( o instanceof File ) {
					return( this.file.equals( o ));
				}
			}
			return false;
		}
		
		public int compareTo( Object o )
		{
			long diff = this.lastModified - ((CacheEntry) o).lastModified;
			return( diff < 0 ? -1 : (diff > 0 ? 1 : 0) );
		}
	}
}