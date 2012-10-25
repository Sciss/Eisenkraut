// lastmod: 03-Jun-08

(
e = Eisenkraut.default;
e.addr.connect;
fork {Êe.initSwing };
)

// This example provides a usefull
// utility to programmtically set
// the panorama of each audio channel.
// You specify the start angle for
// the first channel and an incremental
// angle to advance on each successive
// channel.
// Spread is specified between -1
// (minimum spread) to 0 (normal spread)
// to 1 (maximum spread).
(
var ggStart, ggDelta, ggSpread;
w = EisKPlugInWindow( "Pan", Rect( 800, 100, 160, 100 ), resizable: false, server: e.swing );
JSCStaticText( w, Rect( 4, 4, 52, 20 )).string_( "Azi Start" );
ggStart = JSCNumberBox( w, Rect( 60, 4, 80, 20 )).value_( -45.0 );
JSCStaticText( w, Rect( 4, 26, 52, 20 )).string_( "Azi Delta" );
~ggDelta = ggDelta = JSCNumberBox( w, Rect( 60, 26, 80, 20 )).value_( 45.0 );
JSCStaticText( w, Rect( 4, 48, 52, 20 )).string_( "Spread" );
ggSpread = JSCNumberBox( w, Rect( 60, 48, 80, 20 )).value_( 0.0 );
JSCButton( w, Rect( 4, 74, 80, 20 )).states_([[ "Set Pan" ]] ).canFocus_( false ).action_({ arg b; var aziStart, aziDelta, spread;
aziStart = ggStart.value;
aziDelta = ggDelta.value;
spread = ggSpread.value;
fork {
	var msg, rate, num, startIdx, stopIdx, numChannels;
	msg = e.query( '/doc/active/audio', [ \count ]);
	if( msg.notNil, {
		msg.postln;
		numChannels	= msg[0];
		numChannels.do({ arg ch; e.sendMsg( "/doc/active/audio/" ++ ch, \pan, (((ch * aziDelta) + aziStart) % 360).round( 0.1 ), spread )});
	}, {
		"timeout".warn;
	});
}
});
w.front;
)

// to make a full rotation over 14 channels:
~ggDelta.value = 360/14
