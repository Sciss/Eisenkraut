/*
 *  PeakMeterGroup.java
 *  (de.sciss.gui package)
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
 *		20-Dec-07	created
 *		05-Feb-08	copied from SwingOSC
 *		03-Jul-08	copied from EisK
 */
package de.sciss.gui;

/**
 *	@version	0.59, 03-Jul-08
 *	@author		Hanns Holger Rutz
 */
public class PeakMeterGroup
implements PeakMeterView
{
	private final PeakMeterView[]	views;
	private final int				numChannels;
	
	public PeakMeterGroup( PeakMeterView[] views )
	{
		this.views	= views;
		int numCh = 0;
		for( int i = 0; i < views.length; i++ ) numCh += views[ i ].getNumChannels();
		numChannels = numCh;
	}

//	public void setRMSPainted( boolean onOff )
//	{
//		for( int i = 0; i < views.length; i++ ) {
//			views[ i ].setRMSPainted( onOff );
//		}
//	}
//
//	public void setHoldPainted( boolean onOff )
//	{
//		for( int i = 0; i < views.length; i++ ) {
//			views[ i ].setHoldPainted( onOff );
//		}
//	}
	
	public int getNumChannels()
	{
		return numChannels;
	}
	
	public boolean meterUpdate( float[] peakRMSPairs, int offset, long time )
	{
		int dirty = 0;

		for( int i = 0; i < views.length; i++ ) {
			if( views[ i ].meterUpdate( peakRMSPairs, offset, time )) dirty++;
			offset += views[ i ].getNumChannels() << 1; 
		}
		
		return( dirty > 0 );
	}
	
	public void clearMeter()
	{
		for( int i = 0; i < views.length; i++ ) views[ i ].clearMeter();
	}
	
	public void dispose()
	{
		for( int i = 0; i < views.length; i++ ) views[ i ].dispose();
	}
}
