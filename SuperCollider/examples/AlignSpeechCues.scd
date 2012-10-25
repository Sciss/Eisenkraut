// last mod: 03-Jun-08
(
e = Eisenkraut.default;
e.addr.connect;
fork { e.initSwing };
)

// This example will take the current
// timeline position of the active document,
// add a given cue duration (cueDur) to
// it and advance it by inserting silence to
// the next integer number of seconds.
// a marker is placed to indicate this stopping
// point.
(
var cueDur = 1.5; // seconds;
var mark = true;
w = JSCWindow( "Align", Rect( 800, 100, 100, 40 ), resizable: false, server: e.swing );
JSCButton( w, Rect( 4, 4, 80, 20 )).states_([[ "Next Cue" ]] ).canFocus_( false ).action_({ arg b;
fork {
	var msg, rate, num, startIdx, stopIdx, start, stop, frames;
	msg = e.query( '/doc/active/timeline', [ \rate, \position ]);
	if( msg.notNil, {
//		msg.postln;
		start	= msg[1];
		frames	= (cueDur * msg[0] + 0.5).asInteger;
		stop  	= (start + frames - 1);
		frames	= stop - (stop % frames) - start;
		[ start, frames ].postln;
		if( mark, { e.sendMsg( '/doc/active/markers', \add, start, "Mark" )});
		if( frames > 0, {
			e.sendMsg( '/doc/active', \insertSilence, frames );
		}, {
			"Already aligned!".warn;
		});
	}, {
		"timeout".warn;
	});
}
});
w.front;
)


// trunc end of selection
(
var cueDur = 1.5; // seconds;
var mark = true;
w = JSCWindow( "Align", Rect( 800, 100, 100, 40 ), resizable: false, server: e.swing );
JSCButton( w, Rect( 4, 4, 80, 20 )).states_([[ "Trunc" ]] ).canFocus_( false ).action_({ arg b;
fork {
	var msg, rate, num, startIdx, stopIdx, start, stop, frames;
	msg = e.query( '/doc/active/timeline', [ \rate, \selectionStart, \selectionStop ]);
	if( msg.notNil, {
//		msg.postln;
		start	= msg[1];
		frames	= (cueDur * msg[0] + 0.5).asInteger;
		stop  	= start + frames;
		frames	= msg[2] - stop;
		[ start, frames ].postln;
		if( frames > 0, {
			e.sendMsg( '/doc/active/timeline', \select, stop, stop + frames );
			e.sendMsg( '/doc/active', \delete );
		}, {
			"Too small!".warn;
		});
	}, {
		"timeout".warn;
	});
}
});
w.front;
)
