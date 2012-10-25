// last mod: 03-Jun-08
(
e = Eisenkraut.default;
e.addr.connect;
fork { e.initSwing };
)

// Takes the selected span of the active
// document and expands to to both sides
// to align with the next integer number
// of seconds
(
w = EisKPlugInWindow( "Align", Rect( 800, 100, 100, 40 ), resizable: false, server: e.swing );
JSCButton( w, Rect( 4, 4, 80, 20 )).states_([[ "Fix Sel" ]] ).action_({ arg b;
fork {
	var msg, rate, num, startIdx, stopIdx;
	msg = e.query( '/doc/active/timeline', [ \rate, \selectionStart, \selectionStop ]);
	if( msg.notNil, {
		msg.postln;
		e.sendMsg( '/doc/active/timeline', \select, msg[1] - (msg[1]%msg[0]), (msg[2]+msg[0]-1)-((msg[2]+msg[0]-1)%msg[0]) );
	}, {
		"timeout".warn;
	});
}
});
w.front;
)
