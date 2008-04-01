/*
 *  DynamicURLClassLoader.java
 *  de.sciss.util package
 *
 *  Copyright (c) 2005-2008 Hanns Holger Rutz. All rights reserved.
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
 *		08-Aug-06	created
 *		14-Oct-06	copied from de.sciss.swingosc.ClassLoader
 *		30-Jul-07	refactored from DynamicURLClassLoader, added facility to remove URLs
 */
 
package de.sciss.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *	Extends <code>java.net.URLClassLoader</code> to be
 *	able to dynamically add URLs to the classpath.
 *	URLs can refer to a jar (ending with '.jar', they can be a
 *	classes directory (ending with '/'),
 *	they can be a single class (ending with '.class').
 *
 *	@author		Hanns Holger Rutz
 *	@version	0.59, 25-Feb-08
 */
public class DynamicURLClassLoader
extends URLClassLoader
{
	private final Map mapSingleClasses = new HashMap();
	
	/**
	 *	Creates a new class loader instance
	 *	with no custom folders and jars existing.
	 *	To add these, call <code>addURL</code> repeatedly.
	 *
	 */
	public DynamicURLClassLoader()
	{
		super( new URL[ 0 ]);
	}

	/**
	 *	Creates a new class loader instance
	 *	with a given list of custom folders and jars.
	 */
	public DynamicURLClassLoader( URL[] urls )
	{
		this();
		for( int i = 0; i < urls.length; i++ ) addURL( urls[ i ]);
	}

	/**
	 *	Creates a new class loader instance
	 *	with no custom folders and jars existing.
	 *	To add these, call <code>addURL</code> repeatedly.
	 *
	 */
	public DynamicURLClassLoader( ClassLoader parent )
	{
		super( new URL[ 0 ], parent );
	}

	/**
	 *	Creates a new class loader instance
	 *	with a given list of custom folders and jars.
	 */
	public DynamicURLClassLoader( URL[] urls, ClassLoader parent )
	{
		this( parent );
		for( int i = 0; i < urls.length; i++ ) addURL( urls[ i ]);
	}

	/**
	 *  Made a public method
	 */
	public void addURL( URL url )
	{
		final String path = url.getPath();
		if( path.endsWith( ".class" )) {
			final int i = path.lastIndexOf( '/' ) + 1;
			mapSingleClasses.put( path.substring( i, path.length() - 6 ), url );
//System.out.println( "stored '" + path.substring( i, path.length() - 6 ) + "'" );
		} else {
			super.addURL( url );
		}
	}
	
	public void addURLs( URL[] urls )
	{
		for( int i = 0; i < urls.length; i++ ) addURL( urls[ i ]);
	}

	protected Class findClass( String name )
    throws ClassNotFoundException
    {
		try {
			return super.findClass( name );
		}
		catch( ClassNotFoundException e ) {
			final URL url = (URL) mapSingleClasses.get( name );
//System.out.println( "my got dem url '" + url + "'" );
			if( url != null ) {
				final byte[] classBytes = loadClassBytes( url );
				if( classBytes != null ) {
					return defineClass( name, classBytes, 0, classBytes.length );
				}
			}
			throw e;
		}
    }

	private byte[] loadClassBytes( URL url )
	{
		final List		collBytes	= new ArrayList();
		final List		collLen		= new ArrayList();
		final byte[]	classBytes;
		InputStream		is			= null;
		byte[]			b;
		int				len;
		int				totalLen	= 0;

//System.out.println( "tryin da load '" + url + "'" );
		
		try {
			is 	= url.openStream();
			do {
				b	= new byte[ 4096 ];
				len	= is.read( b );
				if( len > 0 ) {
//System.out.println( "adding buffer of len " + len );
					collBytes.add( b );
					collLen.add( new Integer( len ));
					totalLen += len;
				}
			} while( len > 0 );
			is.close();
			
//System.out.println( "total len " + totalLen );
			classBytes	= new byte[ totalLen ];
			for( int i = 0, off = 0; i < collBytes.size(); i++ ) {
				len = ((Integer) collLen.get( i )).intValue();
				b	= (byte[]) collBytes.get( i );
				System.arraycopy( b, 0, classBytes, off, len );
//System.out.println( "adding "+ len +" bytes at off " + off );
				off += len;
			}
			return classBytes;
		}
		catch( IOException e1 ) {
			if( is != null ) {
				try {
					is.close();
				}
				catch( IOException e11 ) { /* ignored */ }
			}
			return null;
		}
	}
}
