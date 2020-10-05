/**
 *	Eisenkraut SynthDef creation
 *
 *	change log
 *	- 16-Jan-06	eisk-phasor modified after wolkenpumpe to include
 *				padding overlapping boundary to remove clicks in
 *				SRC mode introduced by a BufRd bug
 *	- 21-Sep-06	multichannel defs dynamically created in JCollider now
 *	- 04-Dec-07	removed OffsetOut uses
 *	- 06-May-08	putting them all together in one file
 *	- 08-Oct-08	removed BufRateScale
 *
 *	@author	Hanns Holger Rutz
 *	@version	08-Oct-08
 */

s = Server.local;

(
//p = "/Applications/Eisenkraut/synthdefs/"; // folder in which to save the synth defs
p = "~/Documents/devel/Eisenkraut/src/main/resources/de/sciss/eisenkraut/net/".standardizePath; // folder in which to save the synth defs
//n = 32; // maximum number of channels for which to generate synth defs
~defs = nil;

// ------------ realtime synthdefs ------------

// information synth that
// reads from a mono audio bus
// and writes peak and mean-square
// to two adjacent control busses
// which can be queried using /b_getn
~defs = ~defs.add( SynthDef( "eisk-meter", { arg i_aInBs = 0, i_kOtBs, t_trig;
	var in, rms, peak;
	
	in	= In.ar( i_aInBs );
	rms	= Lag.ar( in.squared, 0.1 );	// Amplitude.ar reports strange things (at least not the RMS)
	// peak nowaday DOES take the abs
//	peak	= Peak.ar( abs( in ), t_trig );
	peak	= Peak.ar( in, t_trig );
	
	// we are reading the values asynchronously through
	// a /c_getn on the meter bus. each request is followed
	// by a /n_set to re-trigger the latch so that we are
	// not missing any peak values.
	Out.kr( i_kOtBs, [ Latch.kr( peak, t_trig ), rms ]);
	
}));

// single channel no thrills limiter
~defs = ~defs.add( SynthDef( "eisk-limiter", { arg i_aBus, i_ceil = 0.99, i_look = 0.01;
	var out, in;
		
	in	= In.ar( i_aBus );
	out	= Limiter.ar( in, i_ceil, i_look );
	ReplaceOut.ar( i_aBus, out );
}));

// single channel gain insert
~defs = ~defs.add( SynthDef( "eisk-gain", { arg i_aBus, volume = 1.0;
	ReplaceOut.ar( i_aBus, In.ar( i_aBus ) * volume );
}));

// function included in panning synth
//SynthDef( "eisk-volume", { arg i_aInBus, i_aOutBus, volume = 1.0;
//	OffsetOut.ar( bus: i_aOutBus, channelsArray: In.ar( bus: i_aInBus ) * volume );
//}).writeDefFile( p );

// old version, produces clicks when using rates other than 0.5, 1.0, 2.0 etc.
//SynthDef( "eisk-phasor", { arg i_aInBuf, rate = 1.0, i_aPhBs;
//
//	var clockTrig, phasorTrig, clockRate, phasor;
//
//	clockRate		= 2 * rate / BufDur.kr( i_aInBuf );
//	clockTrig		= Impulse.ar( freq: clockRate );
//	phasorTrig	= PulseDivider.ar( trig: clockTrig, div: 2, start: 1 );
//	phasor 		= Phasor.ar( trig: phasorTrig, rate: BufRateScale.kr( i_aInBuf ) * rate,
//							start: 0, end: BufFrames.kr( i_aInBuf ));
//	SendTrig.ar( in: clockTrig, id: 0, value: PulseCount.ar( trig: clockTrig ));
//	OffsetOut.ar( bus: i_aPhBs, channelsArray: phasor );
//}).writeDefFile( p );

// version 2 lets phasor run 'free' (i.e. without retriggering).
// seems to be fine
// ; note that this is incompatible with the old version because
// SendTrig emits values 1 smaller than in the old version
// and the application needs to /b_read a full buffer initially
// (not a half buffer as in the old version)
//SynthDef( "eisk-phasor", { arg i_aInBuf, rate = 1.0, i_aPhBs;
//
//	var phasorRate, halfPeriod, numFrames, phasor, phasorTrig, clockTrig;
//
//	phasorRate 	= BufRateScale.kr( bufnum: i_aInBuf ) * rate;
//	halfPeriod	= BufDur.kr( bufnum: i_aInBuf ) / (2 * rate);
//	numFrames		= BufFrames.kr( bufnum: i_aInBuf );
//	phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames );
//	phasorTrig	= Trig1.ar( in: phasor - (numFrames / 2), dur: 0.01 );
//	clockTrig		= phasorTrig + TDelay.ar( phasorTrig, halfPeriod );
//
//	SendTrig.ar( in: clockTrig, id: 0, value: PulseCount.ar( trig: clockTrig ));
//	OffsetOut.ar( bus: i_aPhBs, channelsArray: phasor );
//}).writeDefFile( p );

// version 3 copied from wolkenpumpe : phasor leaves out padding frames
// at the buffer start and end which will be accessed by BufRd's interpolation
// algorithm
~pad = 4;
~defs = ~defs.add( SynthDef( "eisk-phasor", { arg i_aInBf, rate = 1.0, i_aPhBs;
	var phasorRate, halfPeriod, numFrames, phasor, phasorTrig, clockTrig;

// NOTE: BufRateScale used to report 1.0 in SC 3.2 (hence was superfluous)
// ; this changed as of summer 2008 SVN and since EisK will pass in the
// correct rate scale factor in the 'rate' control, this would result in
// wrong pitch. Furthermore, since you can change the rate in the info window
// while still the old sound file headers are on the harddisk, using BufRateScale
// won't make sense.
//	phasorRate 	= BufRateScale.kr( i_aInBf ) * rate;
	phasorRate	= rate;
	halfPeriod	= BufDur.kr( i_aInBf ) / (2 * rate);
	numFrames		= BufFrames.kr( i_aInBf );
	// i can't remember why the ~pad is not included in phasor's start arg
	// but added to the phasor output. i guess there was a good reason why i did this
	phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames - (2 * ~pad) ) + ~pad;
	phasorTrig	= Trig1.kr( A2K.kr( phasor ) - (numFrames / 2), 0.01 );
	clockTrig		= phasorTrig + TDelay.kr( phasorTrig, halfPeriod );

	SendTrig.kr( clockTrig, 0, PulseCount.kr( clockTrig ));
//	OffsetOut.ar( i_aPhBs, phasor );	// OffsetOut is buggy!
	Out.ar( i_aPhBs, phasor );
}));

/*
	////////////////////////////// DEBUGGING VERSION
	SynthDef( "eisk-phasor", { arg i_aInBf, rate = 1.0, i_aPhBs, stopClock = 1.0e7, stopPhase = 1.0e7;
		var phasorRate, halfPeriod, numFrames, phasor, phasorPad, phasorTrig, clockTrig, clock, stop;
	
		phasorRate 	= BufRateScale.kr( i_aInBf ) * rate;
		halfPeriod	= BufDur.kr( i_aInBf ) / (2 * rate);
		numFrames		= BufFrames.kr( i_aInBf );
		// i can't remember why the ~pad is not included in phasor's start arg
		// but added to the phasor output. i guess there was a good reason why i did this
		phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames - (2 * ~pad) );
		phasorPad		= phasor + ~pad;
		phasorTrig	= Trig1.kr( A2K.kr( phasorPad ) - (numFrames / 2), 0.01 );
		clockTrig		= phasorTrig + TDelay.kr( phasorTrig, halfPeriod );
		clock		= PulseCount.kr( clockTrig );
		
	//	((clock >= stopClock) && (phasor >= stopPhase)).poll;
		clock.poll( clockTrig, "clock" );
		phasor.poll( 10, "phase" );
	//	(InRange.kr( clock - stopClock, 0, 1.0e7 ) && InRange.kr( A2K.kr( phasor ) - stopPhase, 0, 1.0e7 )).poll( 10, "stop" );
	stopClock=stopClock-1;
		stop			= (InRange.kr( A2K.kr( phasor ) - stopPhase, 0, numFrames ) + (clock - stopClock).sign).clip( 0, 1 );
	//	stop.poll( stop, "------------- stop" );
	
		SendTrig.kr( clockTrig, 0, clock );
	//	OffsetOut.ar( i_aPhBs, phasorPad );	// OffsetOut is buggy!
	
	//	Out.ar( i_aPhBs, phasorPad );
		Out.ar( i_aPhBs, phasorPad * (1 - stop) );
	}).writeDefFile( p );
*/


// reads input from sound file
// and writes it to an audio bus
~defs = ~defs.add( SynthDef( "eisk-route", {
	arg i_aInBs, i_aOtBs;

//	OffsetOut.ar( i_aOtBs, In.ar( i_aInBs ));	// OffsetOut is buggy!
	Out.ar( i_aOtBs, In.ar( i_aInBs ));
}));

//// reads input from sound file
//// and writes it to an audio bus
//SynthDef( "eisk-input0", {
//	arg i_aInBuf, i_aOutBus, i_gain = 1.0, i_aPhBs, i_interpolation = 1;
//}).writeDefFile( p );
//
//n.do({ arg i;
//	var numChannels = i + 1;
//
//	SynthDef( "eisk-input" ++ numChannels, {
//		arg i_aInBuf, i_aOutBus, i_gain = 1.0, i_aPhBs, i_interpolation = 1;
//	
//		OffsetOut.ar( bus: i_aOutBus, channelsArray: BufRd.ar( numChannels: numChannels, bufnum: i_aInBuf,
//			phase: In.ar( i_aPhBs ), loop: 0, interpolation: i_interpolation ) * i_gain );
//	}).writeDefFile( p );
//});
//
//n.do({ arg i;
//	var numChannels = i + 1;
//
//	SynthDef( "eisk-sfrec" ++ numChannels, {
//		arg i_aInBus, i_aOutBuf;
//		
//		DiskOut.ar( i_aOutBuf, In.ar( i_aInBus, numChannels ));
//	}).writeDefFile( p );
//});
//
// distribute mono input among
// multichannel output
//SynthDef( "eisk-pan0", {
//	arg i_aInBus, i_aOutBus, pos = 0.0, width = 2.0, orient = 0.0, volume = 1.0;
//}).writeDefFile( p );
//
//SynthDef( "eisk-pan1", {
//	arg i_aInBus, i_aOutBus, pos = 0.0, width = 2.0, orient = 0.0, volume = 1.0;
//	
//	OffsetOut.ar( i_aOutBus, In.ar( i_aInBus ) * i_gain );
//}).writeDefFile( p );
//
//(n - 1).do({ arg i;
//	var numChannels = i + 2;
//
//	SynthDef( "eisk-pan" ++ numChannels, {
//		arg i_aInBus, i_aOutBus, pos = 0.0, width = 2.0, orient = 0.0, volume = 1.0;
//		
//		OffsetOut.ar( bus: i_aOutBus, channelsArray: PanAz.ar( numChans: numChannels,
//			in: In.ar( i_aInBus ), pos: pos, level: volume, width: width, orientation: orient ));
//	}).writeDefFile( p );
//});
)

~defs.writeDefFile( "eisk-all", p );
