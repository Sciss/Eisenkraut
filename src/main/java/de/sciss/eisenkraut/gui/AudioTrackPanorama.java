/*
 *  AudioTrackPanorama.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2012 Hanns Holger Rutz. All rights reserved.
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
 *		23-Jul-05	created
 *		24-Aug-06	converted from PanoramaPalette to AudioTrackPanorama
 */

package de.sciss.eisenkraut.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JPopupMenu;
import javax.swing.WindowConstants;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.gui.CoverGrowBox;
import de.sciss.common.AppWindow;
import de.sciss.util.Disposable;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 28-Jun-08
 */
public class AudioTrackPanorama
implements Disposable
{
	private final AudioTrack					t;
	private final ActionListener				actionListener;
	private final SessionCollection.Listener	scListener;
	private final PreferenceChangeListener		prefListener;
//	private final LockManager					lm;
//	private final int							doors;
	private final Preferences					audioPrefs;
	private final SessionCollection				tracks;

	protected PanoramaPanel						pan				= null;
	private AppWindow							palette			= null;
	private JPopupMenu							popup			= null;
	
	private boolean								listening		= false;

	public AudioTrackPanorama( final AudioTrack t, SessionCollection tracks )
	{
		final Application	app			= AbstractApplication.getApplication();
		
		audioPrefs	= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );

		this.t		= t;
//		this.lm		= lm;
//		this.doors	= doors;
		this.tracks	= tracks;

		actionListener = new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( pan != null ) {
					t.getMap().putValue( AudioTrackPanorama.this, AudioTrack.MAP_KEY_PANAZIMUTH, new Double( pan.getAzimuth() ));
					t.getMap().putValue( AudioTrackPanorama.this, AudioTrack.MAP_KEY_PANSPREAD,  new Double( pan.getSpread() ));
				}
			}
		};
		
		scListener = new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				if( e.collectionContains( t ) &&
					(e.getModificationType() == SessionCollection.Event.ACTION_REMOVED) ) {
					
					dispose();
				}
			}

			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				if( (e.getSource() != AudioTrackPanorama.this) && (pan != null) && e.collectionContains( t ) &&
				    (e.setContains( AudioTrack.MAP_KEY_PANAZIMUTH ) || e.setContains( AudioTrack.MAP_KEY_PANSPREAD ))) {

					setAzimuthAndSpread();
				}
			}

			public void sessionObjectChanged( SessionCollection.Event e ) { /* empty */ }
		};
		
		prefListener = new PreferenceChangeListener() {
			public void preferenceChange( PreferenceChangeEvent e )
			{
				if( e.getKey().equals( PrefsUtil.KEY_OUTPUTCONFIG )) {
					createPanPan();
				}
			}
		};
	}
	
	private void createPalette()
	{
		if( palette != null ) return;
		if( popup != null ) destroyPopup();

		final Application	app			= AbstractApplication.getApplication();
	
		palette = new AppWindow( AbstractWindow.PALETTE );
		palette.setTitle( app.getResourceString( "palettePanorama" ) + " : " + t.getName() );
		createPanPan();
   		palette.getContentPane().add( CoverGrowBox.create(), BorderLayout.SOUTH );
		palette.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		palette.addListener( new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e )
			{
				dispose();
			}
		});
		palette.init();
	}
	
	private void createPopup( boolean disposeOnMouseRelease )
	{
		if( popup != null ) return;
		if( palette != null ) destroyPalette();
		
		popup	= new JPopupMenu();
		createPanPan();
		if( disposeOnMouseRelease ) {
			pan.addMouseListener( new MouseAdapter() {
				public void mouseReleased( MouseEvent e )
				{
					dispose();
				}
			});
		}
		popup.addPopupMenuListener( new PopupMenuListener() {
			public void popupMenuCanceled( PopupMenuEvent e )
			{
				dispose();
			}
			
			public void popupMenuWillBecomeInvisible( PopupMenuEvent e ) { /* empty */ }
			public void popupMenuWillBecomeVisible( PopupMenuEvent e ) { /* empty */ }
		});
//popup.setCursor( new java.awt.Cursor( java.awt.Cursor.CROSSHAIR_CURSOR ));
	}
	
	public void showPalette()
	{
		createPalette();
		startListening();
		palette.setVisible( true );
		palette.toFront();
	}
	
	public void showPopup( Component invoker, int x, int y, boolean beginDragging )
	{
		createPopup( beginDragging );
		startListening();
		popup.show( invoker, x, y );
		if( beginDragging ) pan.beginDragging();
	}
	
	private void startListening()
	{
		if( !listening ) {
			tracks.addListener( scListener );
			audioPrefs.addPreferenceChangeListener( prefListener );
			listening = true;
		}
	}
	
	private void stopListening()
	{
		if( listening ) {
			tracks.removeListener( scListener );
			audioPrefs.removePreferenceChangeListener( prefListener );
			listening = false;
		}
	}
	
	public void dispose()
	{
		stopListening();
		destroyPalette();
		destroyPopup();
		destroyPanPan();
	}
	
	private void destroyPalette()
	{
		if( palette == null ) return;
		
		palette.dispose();
		palette = null;
	}

	private void destroyPopup()
	{
		if( popup == null ) return;
		
		popup.setVisible( false );
		popup = null;
	}

	protected void destroyPanPan()
	{
		if( pan == null ) return;

		pan.getParent().remove( pan );
		pan.removeActionListener( actionListener );
		pan = null;
	}

	protected void createPanPan()
	{
		final String		cfgName		= audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null );
		RoutingConfig		oCfg		= null;

		destroyPanPan();

		try {
			if( cfgName != null && audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).nodeExists( cfgName )) {
				oCfg	= new RoutingConfig( audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).node( cfgName ));
				pan		= new PanoramaPanel( oCfg.numChannels, oCfg.startAngle );
				setAzimuthAndSpread();
				pan.addActionListener( actionListener );
				if( palette != null ) {
					palette.getContentPane().add( pan, BorderLayout.CENTER );
					palette.revalidate();
				} else {
					popup.add( pan, BorderLayout.CENTER );
					popup.revalidate();
				}
			}
		}
		catch( BackingStoreException e1 ) {
			System.err.println( e1 );
		}
	}

	protected void setAzimuthAndSpread()
	{
		final double	azi, spread;
		Object			o;

//		if( !lm.attemptShared( doors, 250 )) return;
//		try {
			o = t.getMap().getValue( AudioTrack.MAP_KEY_PANAZIMUTH );
			if( (o != null) && (o instanceof Number) ) {
				azi	= ((Number) o).doubleValue();
			} else {
				azi	= pan.getAzimuth();
			}
			o = t.getMap().getValue( AudioTrack.MAP_KEY_PANSPREAD );
			if( (o != null) && (o instanceof Number) ) {
				spread	= ((Number) o).doubleValue();
			} else {
				spread	= pan.getSpread();
			}
			pan.setAzimuthAndSpread( azi, spread );
//		}
//		finally {
//			lm.releaseShared( doors );
//		}
	}
}
