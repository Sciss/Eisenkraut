// last mod: 05-Nov-08
(
e = Eisenkraut.default;
e.addr.connect;
fork { e.initSwing };
)

// This example demonstrates the use
// of JSCMenuNode : an extra menu "Util"
// is added which contains two items
// "Prev Marker" and "Next Marker"
// (accessible via Meta+(Shift)+Quote).
// They will move the timeline position
// to the succeeding or preceding marker.
// ~preRoll specifies a negative offset
// to the positioning.

(
~preRoll = 0.5;
~action = { arg dir;
	var msg, rate, pos, numMarks, idx, len, preFrames;
	fork {
		msg = e.query( '/doc/active/timeline', [ \rate, \length ]);
		if( msg.notNil, {
			#rate, len = msg;
			preFrames = (~preRoll * rate).round.asInteger;
			msg = e.query( '/doc/active/transport', \position );
			pos = if( msg.isNil, 0, {Êmsg[ 0 ]});
			msg = e.query( '/doc/active/markers', \count );
			numMarks = if( msg.isNil, 0, {Êmsg[ 0 ]});
			msg = e.get( '/doc/active/markers', [ \indexOf, pos + if( dir, preFrames, 0 )]);
			if( msg.notNil, {
				idx = msg[ 0 ];
				if( idx < 0, { idx = (idx + 1).neg + if( dir, 0, -1 )}, { idx = idx + if( dir, 1, -1 )});
				if( (idx >= 0) && (idx < numMarks), {
					msg = e.get( '/doc/active/markers', [ \at, idx ]);
					if( msg.notNil, {
						pos = (msg[ 0 ] - preFrames).clip( 0, len );
						e.sendMsg( '/doc/active/timeline', \position, pos );
					}, {
						"timeout".warn;
					});
				});
			}, {
				"timeout".warn;
			});
		}, {
			"timeout".warn;
		});
	};
};
~root = JSCMenuRoot( e.swing );
~menu = JSCMenuGroup( ~root, "Util" );
// note: on a german keyboard A-umlaut
~itemPrev = JSCMenuItem( ~menu, "Prev Marker" ).shortcut_( "meta shift QUOTE" ).action_({ ~action.( false )});
~itemNext = JSCMenuItem( ~menu, "Next Marker" ).shortcut_( "meta QUOTE" ).action_({ ~action.( true )});
)

~menu.remove;

// ------------------------------------------------------------------------------------------------------

// With different actions:
// this offers two different zoom levels
// at the stroke of a key. Zoom level 1
// shows the left border of the selection at close-up (sample level).
// Zoom level 2 shows the whole selection (similar to
// alt+F but with a little headroom to both sides).

(
~action = { arg type;
	var msg, rate, len, selStart, selStop, viewStart, viewStop, scrB;
	fork {
		msg = e.query( '/doc/active/timeline', [ \rate, \length, \selectionStart, \selectionStop ]);
		if( msg.notNil, {
			#rate, len, selStart, selStop = msg;
			switch( type,
			\in, {
				scrB		= JSCWindow.screenBounds( e.swing );
				viewStart	= max( 0, selStart - (scrB.width * 0.25).asInteger );
				viewStop	= min( len, selStart + (scrB.width * 0.75).asInteger );
				e.sendMsg( '/doc/active/timeline', \view, viewStart, viewStop );
			},
			\out, {
				viewStart	= max( 0, selStart - ((selStop - selStart) * 0.5).asInteger );
				viewStop	= min( len, selStop + ((selStop - selStart) * 0.5).asInteger );
				e.sendMsg( '/doc/active/timeline', \view, viewStart, viewStop );
			}, {
				("Illegal type '" ++ type ++ "'").error;
			});
		}, {
			"timeout".warn;
		});
	};
};
~root = JSCMenuRoot( e.swing );
~menu = JSCMenuGroup( ~root, "Util" );
~itemPrev = JSCMenuItem( ~menu, "Zoom to Selection Start" ).shortcut_( "alt 1" ).action_({ ~action.( \in )});
~itemNext = JSCMenuItem( ~menu, "Zoom to Whole Selection" ).shortcut_( "alt 2" ).action_({ ~action.( \out )});
)

~menu.remove;
