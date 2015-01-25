/*
 *  AudioTrack.java
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

package de.sciss.eisenkraut.timeline;

import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;
import de.sciss.eisenkraut.util.MapManager;

import de.sciss.timebased.Trail;

/**
 *  A simple implementation of the <code>Transmitter</code>
 *  interface that does not yet make assumptions
 *  about the data structure but provides some
 *  common means useful for all transmitters.
 *  It provides the basic mechanism for XML import and
 *  export, it handles all methods except
 *  <code>getTrackEditor</code>.
 */
public class AudioTrack
		extends Track
		implements OSCRouter {

	public static final String		MAP_KEY_PANAZIMUTH	= "panazimuth";
	public static final String		MAP_KEY_PANSPREAD	= "panspread";

//	private final		AudioTrail	trail;
	private final		int			chan;
	private final		AudioTracks	coll;

	private final OSCRouterWrapper	osc;

	/**
	 *  Constructs a new empty transmitter.
	 *  Basic initialization is achieved by
	 *  adding a preexisting file to the track editor,
	 *  calling <code>setName</code> etc. methods.
	 */
//	public AudioTrack( AudioTrail trail, int chan )
	public AudioTrack( AudioTracks coll, int chan )	// need chan because of initial oscGetPathComponent !
	{
		super();
		
//		this.trail	= trail;
		this.chan	= chan;
		this.coll	= coll;

//		final NumberSpace	spcAzi		= new NumberSpace( -360.0, 360.0, 0.1 );
//		final NumberSpace	spcSpread	= new NumberSpace( -1.0, 1.0, 0.1 );
		final MapManager	map			= getMap();

		map.putContext( this, MAP_KEY_PANAZIMUTH, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
																		   MapManager.Context.TYPE_DOUBLE, null, "labelAzimuth",
																		   null, 0.0));
		map.putContext( this, MAP_KEY_PANSPREAD, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
																		   MapManager.Context.TYPE_DOUBLE, null, "labelSpread",
																		   null, 0.0));

		osc			= new OSCRouterWrapper( coll, this );
	}

	public Trail getTrail() {
		return coll.getTrail();
	}

	public int getChannelIndex() {
		return chan;
	}

	public Class<?> getDefaultEditor()
	{
		return null;	// XXX
	}

	public int getFlags()
	{
		return ((Number) getMap().getValue( MAP_KEY_FLAGS )).intValue();
	}

	// shorthand for (FLAGS_MUTE | FLAGS_VIRTUALMUTE) == 0
	public boolean isAudible()
	{
		return( (getFlags() & (FLAGS_MUTE | FLAGS_VIRTUALMUTE)) == 0 );
	}

	// ------------- OSCRouter interface -------------

	public String oscGetPathComponent() {
		return String.valueOf(chan);
	}

	public void oscRoute(RoutedOSCMessage rom) {
		osc.oscRoute(rom);
	}

	public void oscAddRouter(OSCRouter subRouter) {
		osc.oscAddRouter(subRouter);
	}

	public void oscRemoveRouter(OSCRouter subRouter) {
		osc.oscRemoveRouter(subRouter);
	}

	public Object oscQuery_flags() {
//		return new Integer( getFlags() );
		return getMap().getValue(MAP_KEY_FLAGS);
	}

	public Object oscQuery_audible()
	{
		return isAudible() ? 1 : 0;
	}

	public Object oscQuery_panAzimuth()
	{
		return getMap().getValue( MAP_KEY_PANAZIMUTH );
	}

	public Object oscQuery_panSpread()
	{
		return getMap().getValue( MAP_KEY_PANSPREAD );
	}

	public Object oscQuery_trackSelected()
	{
		return coll.isSelected(this) ? 1 : 0;
	}

	// flags <(int) flagsToSet> <(int) flagsToClear>
	public void oscCmd_flags( RoutedOSCMessage rom )
	{
		int argIdx	= 1;
		final int flagsSet, flagsClear, oldFlags, newFlags;
	
		try {
			flagsSet	= ((Number) rom.msg.getArg( argIdx )).intValue();
			argIdx++;
			flagsClear	= ((Number) rom.msg.getArg( argIdx )).intValue();
			oldFlags	= getFlags();
			newFlags	= (oldFlags | flagsSet) & ~flagsClear;
			if( oldFlags == newFlags ) return;
			getMap().putValue( this, MAP_KEY_FLAGS, newFlags);
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
	}

	// pan <(float) azi> <(float) spread>
	// azi : 0...360
	// spread : 0...1
	public void oscCmd_pan( RoutedOSCMessage rom )
	{
		int		argIdx	= 1;
		double	azi, spread;
	
		try {
			azi			= ((Number) rom.msg.getArg( argIdx )).doubleValue();
			if( azi < 0.0 ) {
				azi	   += Math.ceil( -azi / 360 ) * 360;
			} else if( azi > 360.0 ) {
				azi	   %= 360.0;
			}
			argIdx++;
			spread		= Math.max( -1.0, Math.min( 1.0, ((Number) rom.msg.getArg( argIdx )).doubleValue() ));
			getMap().putValue( this, MAP_KEY_PANAZIMUTH, azi);
			getMap().putValue( this, MAP_KEY_PANSPREAD, spread);
		}
		catch( ClassCastException e1 ) {
			OSCRoot.failedArgType( rom, argIdx );
		}
		catch( IndexOutOfBoundsException e1 ) {
			OSCRoot.failedArgCount( rom );
		}
	}
}