// last mod: 03-Jun-08

(
e = Eisenkraut.default;
e.addr.connect;
)

// This example will help you to monitor
// recordings using Eisenkraut's Recorder
// Dialog. It boots another scsynth
// server (from sclang) that will be used
// to play beeping tones whenever the
// recorded sound transcends a given
// headroom. This is usefull when you
// perform your recording and cannot stare
// at the screen to make sure you don't
// get a clipped recording
//
// NOTE: the Recorder Dialog must be
// open before executing the code block
// below!
(
s.options.device="Mobile I/O 2882 [2600]";	// use your preferred soundcard here
s.waitForBoot({
	~rout = fork { var msg, peak;
		~amp = 0.2; // peep volume
		~headroom = -3; // dBFS (above which we will peep)
		SynthDef( \peep, { arg out = 0, freq = 1000, amp = 0.1;
			Out.ar( out, SinOsc.ar( freq, mul:
				EnvGen.ar( Env.linen( 0.01, 0.5, 0.2 ), levelScale: amp, doneAction: 2 )
			));
		}).send( s );
		s.sync;
		inf.do({
			msg = e.query( '/doc/active/recorder', \headroom );
			if( msg.notNil, {
				peak = msg[0];
				if( peak.notNil and: { peak > ~headroom }, {
					"Peep".postln;
					Synth( \peep, [ \amp, ~amp ] );
					e.sendMsg( '/doc/active/recorder', \resetHeadroom );
				});
			});
			1.wait
		});
	};
});
)

~rout.stop;
