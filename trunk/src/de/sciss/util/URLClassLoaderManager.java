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
	
	public void addURLs( URL[] urls )
	{
		loader.addURLs( urls );
		for( int i = 0; i < urls.length; i++ ) this.urls.add( urls[ i ]);
	}

	public void removeURL( URL url )
	{
		if( urls.remove( url )) makeLoader();
	}
	
	public void removeURLs( URL[] urls )
	{
		boolean changed = false;
		for( int i = 0; i < urls.length; i++ ) changed |= this.urls.remove( urls[ i ]);
		if( changed ) makeLoader();
	}

	public ClassLoader getCurrentLoader()
	{
		return loader;
	}
}