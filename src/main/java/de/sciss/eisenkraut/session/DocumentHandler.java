/*
 *  DocumentHandler.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2013 Hanns Holger Rutz. All rights reserved.
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
 *      21-May-05	created
 *		20-Jan-06	implements OSCRouter
 */

package de.sciss.eisenkraut.session;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import de.sciss.app.Document;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class DocumentHandler
extends de.sciss.app.AbstractDocumentHandler
implements OSCRouter
{
	private static final String OSC_DOC			= "doc";
	// sub level
	private static final String OSC_ACTIVE		= "active";
	private static final String OSC_INDEX		= "index";
	private static final String OSC_ID			= "id";

	// key = Integer( nodeID ) ; value = Session
	private final Map	mapIDs		= new HashMap();
	
	private final OSCRouterWrapper	osc;
	private final Main				root;

	public DocumentHandler( Main root )
	{
		super( true );	// we are multi-document aware
		this.root	= root;
		this.osc	= new OSCRouterWrapper( OSCRoot.getInstance(), this );
	}
	
	public void addDocument( Object source, Document doc )
	{
		synchronized( this.sync ) {
			super.addDocument( source, doc );
			this.mapIDs.put( new Integer( ((Session) doc).getNodeID() ), doc );
		}
	}

	public void removeDocument( Object source, Document doc )
	{
		synchronized( this.sync ) {
			this.mapIDs.remove( new Integer( ((Session) doc).getNodeID() ));
			super.removeDocument( source, doc );
		}
	}

	// ------------- OSCRouter interface -------------
	
	public String oscGetPathComponent()
	{
		return OSC_DOC;
	}
	
	public void oscRoute( RoutedOSCMessage rom )
	{
		if( rom.hasNext() ) {	// special handling here as documents can be accessed with different paths
			oscRouteNext( rom.next() );
		} else {
			osc.oscRoute( rom );
		}
	}
	
	private void oscRouteNext( RoutedOSCMessage rom )
	{
		final String	subPath;
		final Document	doc;
		
		try {
			subPath = rom.getPathComponent();

			if( subPath.equals( OSC_ACTIVE )) {
				doc = getActiveDocument();
			} else if( subPath.equals( OSC_ID )) {
				rom = rom.next();
				final Integer id = new Integer( rom.getPathComponent() );
				synchronized( this.sync ) {
					doc = (Document) this.mapIDs.get( id );
				}
			} else if( subPath.equals( OSC_INDEX )) {
				rom = rom.next();
				final int idx = Integer.parseInt( rom.getPathComponent() );
				if( getDocumentCount() > idx ) {
					doc = getDocument( idx );
				} else {
					doc = null;
				}
			} else {
				OSCRoot.failedUnknownPath( rom );
				return;
			}

			if( doc == null ) {
				OSCRoot.failed( rom.msg, "Document not found" );
				return;
			}

			if( !(doc instanceof OSCRouter) ) {
				OSCRoot.failed( rom.msg, "Document doesn't speak OSC" );
				return;
			}
			
			((OSCRouter) doc).oscRoute( rom );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedUnknownPath( rom );
		}
		catch( NumberFormatException e1 ) {
			OSCRoot.failedUnknownPath( rom );
		}
	}
	
	public void oscAddRouter( OSCRouter subRouter )
	{
		osc.oscAddRouter( subRouter );
	}

	public void oscRemoveRouter( OSCRouter subRouter )
	{
		osc.oscRemoveRouter( subRouter );
	}
	
	public Object oscQuery_count()
	{
		return new Integer( getDocumentCount() );
	}

	public void oscCmd_open( RoutedOSCMessage rom )
	{
		try {
			final String path = rom.msg.getArg( 1 ).toString();
			root.getMenuFactory().openDocument( new File( path ));
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
			return;
		}
//		catch( ClassCastException e1 ) {
//			OSCRoot.failedArgType( rom, 1 );
//		}
	}
}