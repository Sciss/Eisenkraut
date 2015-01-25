/*
 *  WelcomeScreen.java
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

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.MalformedURLException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import net.roydesign.mac.MRJAdapter;

import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.Main;

/**
 *  A new frame is created and
 *  opened that displays a welcome
 *  message to the new user
 *  (whenever preferences are absent
 *  upon application launch).
 */
@SuppressWarnings("serial")
public class WelcomeScreen
extends JFrame
implements HyperlinkListener
{
	private final JEditorPane		ggContent;
	protected final JButton			ggClose;
	protected final WelcomeScreen	welcome		= this;

	private static final String htmlWelcome1 =
		"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\" \"http://www.w3.org/TR/REC-html40/loose.dtd\">"+
		"<html><head><style type=\"text/css\"><!--\n"+
		"body { background:black; color:white; padding:20px; }\n"+
		"em { font-weight:bold; font-style:normal; }\n"+
		"a { color:white;font-weight:bold; }\n"+
		"p { font-family:\"Lucida Grande\" Helvetica sans-serif;font-size:14pt;padding:4pt 4pt 4pt 4pt;margin:0; }\n"+
		"--></style></head><body><table><tr><td valign=\"top\">";
		
	private static final String htmlWelcome2 =
		"<p>This is a beta version. <B>BETA</B> means it "+
		"still contains a lot of bugs that can possibly damage your files. Use this at your own risk!</p>"+
		"<p>This screen pops up because no valid "+
		"preferences file was found, which suggests that you start this application for the first time. Please take a few "+
		"minutes to read the <EM>README.md</EM> file and the introductory part of the manual. As a first step before doing "+
		"anything else, you should adjust your preferences. The preferences pane will show up when you "+
		"close this window.</p>"+
		"<p>This software is free software; you can redistribute it and/or "+
		"modify it under the terms of the GNU General Public License "+
		"as published by the Free Software Foundation; either "+
		"version 2, june 1991 of the License, or (at your option) any later version.</p>"+
		"<p>This software is distributed in the hope that it will be useful, "+
		"but <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty of "+
		"<strong>MERCHANTABILITY</strong> or <strong>FITNESS FOR A PARTICULAR PURPOSE</strong>. See the GNU "+
		"General Public License for more details.</p>"+
		"</td></tr></table></body></html>";

	/**
	 *  Create and open welcome screen. This
	 *  is in the debug package because it's likely
	 *  to change in a future version.
	 *
	 *  @param  root	application root
	 */
	public WelcomeScreen( final Main root )
	{
		super( "Welcome to " + root.getName() );
		
		final Container cp = getContentPane();
		String			img = "";
		
		try {
			img = "<IMG SRC=\"" + new File( new File( "help" ), "application.png" ).getAbsoluteFile().toURI().toURL() +
		    "\" ALT=\"\">";
		} catch( MalformedURLException e ) { /* ignore */ }

		System.err.println( img );
		
		ggContent = new JEditorPane( "text/html", htmlWelcome1 + img + "</td><td><H1>Welcome to " +
		    root.getName() + "</H1>" + htmlWelcome2 );
		ggContent.setEditable( false );
		ggContent.addHyperlinkListener( this );
		cp.add( ggContent, BorderLayout.CENTER );
		final Action closeAction = new AbstractAction( "- Close -" ) {
			public void actionPerformed( ActionEvent e )
			{
				welcome.setVisible( false );
				welcome.dispose();
				root.getMenuFactory().showPreferences();
			}
		};
		ggClose = new JButton( closeAction );
		cp.add( ggClose, BorderLayout.SOUTH );
		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				ggClose.doClick( 150 );
			}
		});
		setSize( 640, 480 );
		setLocationRelativeTo( null );
		
		setVisible( true );
		toFront();
	}

	// --------- HyperlinkListener interface ---------

	public void hyperlinkUpdate( HyperlinkEvent e )
	{
		if( e.getEventType() == HyperlinkEvent.EventType.ACTIVATED ) {
			try {
				MRJAdapter.openURL( e.getURL().toString() );
			}
			catch( Exception e1 ) {
				BasicWindowHandler.showErrorDialog( this, e1, getTitle() );
			}
		}
	}
}