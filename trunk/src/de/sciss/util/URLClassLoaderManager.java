package de.sciss.util;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class URLClassLoaderManager
{
	private final ClassLoader		parent;
	private DynamicURLClassLoader 	loader;
	private Set						urls	= new HashSet();
	
	public URLClassLoaderManager()
	{
		parent = null;
		makeLoader();
	}

	public URLClassLoaderManager( ClassLoader parent )
	{
		this.parent = parent;
		makeLoader();
	}
	
	private void makeLoader()
	{
		final Object[]	urlsA	= this.urls.toArray();
		final URL[]		urlsTA	= new URL[ urlsA.length ];
		for( int i = 0; i < urlsA.length; i++ ) urlsTA[ i ] = (URL) urlsA[ i ];
		loader = parent == null ?	new DynamicURLClassLoader( urlsTA ) :
									new DynamicURLClassLoader( urlsTA, parent );
	}

	public void addURL( URL url )
	{
		loader.addURL( url );
		urls.add( url );
	}
	
	public void addURLs( URL[] u )
	{
		loader.addURLs( u );
		for( int i = 0; i < u.length; i++ ) this.urls.add( u[ i ]);
	}

	public void removeURL( URL url )
	{
		if( urls.remove( url )) makeLoader();
	}
	
	public void removeURLs( URL[] u )
	{
		boolean changed = false;
		for( int i = 0; i < u.length; i++ ) changed |= this.urls.remove( u[ i ]);
		if( changed ) makeLoader();
	}

	public ClassLoader getCurrentLoader()
	{
		return loader;
	}
}