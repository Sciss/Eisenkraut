// last mod: 03-Jun-08

(
e = Eisenkraut.default;
e.addr.connect;
)

// This example queries all markers
// of the active document, and returns
// them in the variable ~pos
// (a List of marker positions in
// sample frames)
(
~pos = List.new;
fork {
	var msg, rate, num, startIdx, stopIdx;
	msg = e.query( '/doc/active/timeline', \rate );
	if( msg.notNil, {
		rate = msg[ 0 ];
		msg = e.query( '/doc/active/markers', \count );
		if( msg.notNil, {
			num = msg[ 0 ];
			("Number of markers: "++num).postln;
			startIdx = 0;
			// maximum 128 markers per query, based on an estimated of maximum marker names ...
			// 128 * (32 + 4 + 5) + headerSize = ca. 5000 bytes
			stopIdx = min( num, startIdx + 128 );
			while({ startIdx < num }, {
				msg = e.get( '/doc/active/markers', [ \range, startIdx, stopIdx ]);
				if( msg.notNil, {
					msg.pairsDo({ arg pos, name;
						("Marker '"++name++"' at frame "++pos++" = "++(pos/rate).asTimeString( 0.001 )).postln;
						~pos.add( pos );
					});
				}, {
					"timeout".warn;
				});
				startIdx	= stopIdx;
				stopIdx	= min( num, startIdx + 128 );
			});
		}, {
			"timeout".warn;
		});
	}, {
		"timeout".warn;
	});
}
)

// You could store t he marker positions as a compile string...
f = File( "~/Desktop/marks.txt".standardizePath, "w" );
f.write( ~pos.asCompileString );
f.close;


// ...to read them in again at a later point in time...
f = File( "~/Desktop/marks.txt".standardizePath, "r" );
~pos = f.readAllString.interpret;
f.close;

// ...and to send them back to an Eisenkraut audio document
~pos.clump(128).do({ arg posList; var marks; marks = (posList ++ "Mark".dup(posList.size)).unlace(posList.size).flatten; e.listSendMsg([ '/doc/active/markers', \add ] ++ marks )});

// ...or rename them before as to indicate region begins/ends...
~pos.clump(128).do({ arg posList; var marks; marks = posList.collect({ arg elem, i; [ elem, if( i.even, "Beg", "End" )]}).flatten; e.listSendMsg([ '/doc/active/markers', \add ] ++ marks )});

// next, let's place the timeline position at the nearest marker before current position
(
fork {
	var msg, oldpos, nextIdx, newPos;
	msg = e.query( '/doc/active/timeline', \position );
	if( msg.notNil, {
		oldpos = msg[ 0 ];
		msg = e.get( '/doc/active/markers', [ \indexOf, oldpos ]);
		if( msg.notNil, {
			nextIdx = msg[ 0 ];
			if( nextIdx < 0, { nextIdx = (nextIdx + 2).neg });
			if( nextIdx < 0, {
				newPos = 0;
			}, {
				msg = e.get( '/doc/active/markers', [ \at, nextIdx ]);
				if( msg.notNil, {
					newPos = msg[ 0 ];
				}, {
					"timeout".warn;
					newPos = -1;
				});
			});
			if( newPos >= 0, {
				e.sendMsg( '/doc/active/timeline', \position, newPos );
				("new position: "++newPos).postln;
			});
		}, {
			"timeout".warn;
		});
	}, {
		"timeout".warn;
	});
}
)


// goodbye to Eisenkraut
e.sendMsg( '/main', \quit );
