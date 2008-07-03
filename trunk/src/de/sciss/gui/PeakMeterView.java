package de.sciss.gui;

import de.sciss.util.Disposable;

public interface PeakMeterView
extends Disposable
{
//	public void setRMSPainted( boolean onOff );
//	public void setHoldPainted( boolean onOff );
	public int getNumChannels();
	public void clearMeter();
	public boolean meterUpdate( float[] peakRMSPairs, int offset, long time );
}
