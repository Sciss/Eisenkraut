/*
 *  PanoramaButton.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2011 Hanns Holger Rutz. All rights reserved.
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
 *  Change log:
 *		26-Jul-05	created
 */

package de.sciss.eisenkraut.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import javax.swing.Icon;

import de.sciss.eisenkraut.session.SessionCollection;
import de.sciss.eisenkraut.timeline.AudioTrack;
import de.sciss.eisenkraut.util.MapManager;
import de.sciss.gui.ModificationButton;
import de.sciss.util.Disposable;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class PanoramaButton
extends ModificationButton
implements Disposable
{
	private static final Color			colrNormal		= new Color( 0x3C, 0x3C, 0x3C, 0xFF );
	private static final Color			colrPressed		= new Color( 0x06, 0x06, 0x06, 0xFF );
	private static final Color			colrDisabled	= new Color( 0x6C, 0x6C, 0x6C, 0x7F );
	protected static final Paint		pntCtrl			= new Color( 0x00, 0x00, 0x00, 0x7F );

	protected static final Shape		shpCtrl			= new Ellipse2D.Double( -2, -2, 5, 5 );

	protected AudioTrackPanorama		atPan			= null;
//	private Rectangle					atPanBounds		= null;
	private final Point2D				ctrlPt			= new Point2D.Double();
//	private JPopupMenu					pop				= null;
//	private PanoramaPanel				panpan			= null;
	private final AudioTrack			t;
	private final PopupTriggerMonitor	popMon;

	public PanoramaButton( final AudioTrack t, final SessionCollection tracks )
	{
		super( new PantaRei( colrNormal ),
			   new PantaRei( colrPressed ),
		       new PantaRei( colrDisabled ));
		
		this.t	= t;

		((PantaRei) icnNormal).setCtrlPoint( ctrlPt );
		((PantaRei) icnPressed).setCtrlPoint( ctrlPt );
		((PantaRei) icnDisabled).setCtrlPoint( ctrlPt );
		
//		final WindowListener atPanListener	= new WindowAdapter() {
//			public void windowClosed( WindowEvent e )
//			{
//				if( atPan != null ) {
//					atPanBounds	= atPan.getBounds();
//					atPan		= null;
//				}
//			}
//		};

//		addActionListener( new ActionListener() {
//			public void actionPerformed( ActionEvent e )
//			{
//				if( atPan == null ) {
////					atPan = new PanoramaPalette( root, t, tracks, lm, doors );
//					atPan = new AudioTrackPanorama( root, t, tracks, lm, doors );
////					atPan.addWindowListener( atPanListener );
////					if( atPanBounds != null ) atPan.setBounds( atPanBounds );
//				}
////				atPan.setVisible( true );
////				atPan.toFront();
//				atPan.showPalette();
//			}
//		});
		
		t.getMap().addListener( new MapManager.Listener() {
			public void mapChanged( MapManager.Event e )
			{
				if( e.getPropertyNames().contains( AudioTrack.MAP_KEY_PANAZIMUTH ) ||
					e.getPropertyNames().contains( AudioTrack.MAP_KEY_PANSPREAD )) {
				
					updateButtonState();	
				};
			}
		
			public void mapOwnerModified( MapManager.Event e ) { /* ignore */ }
		});

		popMon = new PopupTriggerMonitor( this );
		popMon.addListener( new PopupTriggerMonitor.Listener() {
			public void componentClicked( PopupTriggerMonitor m )
			{
				if( atPan == null ) {
					atPan = new AudioTrackPanorama( t, tracks );
				}
				atPan.showPalette();
			}
			
			public void popupTriggered( PopupTriggerMonitor m )
			{
				if( atPan == null ) {
					atPan = new AudioTrackPanorama( t, tracks );
				}
				atPan.showPopup( m.getComponent(), m.getComponent().getWidth(), 0, true );
			}
		});

		updateButtonState();	
	}
	
	public void dispose()
	{
		if( atPan != null ) {
//			atPan.setVisible( false );
			atPan.dispose();
			atPan = null;
		}
		popMon.dispose();
	}
	
	protected void updateButtonState()
	{
		Object	o;
		o				= t.getMap().getValue( AudioTrack.MAP_KEY_PANAZIMUTH );
		double	azi		= (o != null) && (o instanceof Number) ? ((Number) o).doubleValue() : 0.0;
		o				= t.getMap().getValue( AudioTrack.MAP_KEY_PANSPREAD );
		double	spread	= (o != null) && (o instanceof Number) ? ((Number) o).doubleValue() : 2.0;
		
		ctrlPt.setLocation( PanoramaPanel.aziAndSpreadToCtrlPoint( azi, spread ));
		ctrlPt.setLocation( (ctrlPt.getX() + 1.0) * 14.5, (1.0 - ctrlPt.getY()) * 14.5 );
		
		if( isVisible() ) repaint();
	}

	protected final static Stroke	strkInside	= new BasicStroke( 0.333f );
	protected final static Stroke	strkOutside	= new BasicStroke( 0.75f );
	protected final static Shape	shpLine		= new Line2D.Double( 0.5, 14.5, 28.5, 14.5 );

	private static class PantaRei
	implements Icon
	{
		private final Color		color;
		private Point2D			ctrlPt;
		
		protected PantaRei( Color color )
		{
			this.color	= color;
		}
	
		public int getIconWidth()
		{
			return 31;
		}
		
		public int getIconHeight()
		{
			return 31;
		}
		
		protected void setCtrlPoint( Point2D ctrlPt )
		{
			this.ctrlPt = ctrlPt;
		}
		
		public void paintIcon( Component c, Graphics g, int x,  int y )
		{
			final Graphics2D		g2			= (Graphics2D) g;
			final Stroke			strkOrig	= g2.getStroke();
			final AffineTransform	atOrig		= g2.getTransform();
			
//			g2.translate( x, y );
			g2.translate( x + 0.5f, y + 0.5f );	// +0.5 = "smoother"
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			g2.setColor( color );
			g2.setStroke( strkOutside );
			g2.drawOval( 0, 0, 29, 29 );
			g2.setStroke( strkInside );
			g2.drawOval( 3, 3, 23, 23 );
			g2.draw( shpLine );

//			g2.translate( ctrlPt.getX(), ctrlPt.getY() );
			g2.translate( ctrlPt.getX() - 0.5f, ctrlPt.getY() - 0.5f );
			g2.setPaint( pntCtrl );
			g2.fill( shpCtrl );
			
			g2.setTransform( atOrig );
			g2.setStroke( strkOrig );
		}
	}
}
