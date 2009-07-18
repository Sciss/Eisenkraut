// lastmod: 03-Jun-08

e = Eisenkraut.default;
e.dumpOSC;


// This example changes the control room's main volume
(
	fork {
		73.do({ arg i;
			e.sendMsg( '/sc', \volume, i.neg.dbamp );
			0.1.wait;
		});
		73.do({ arg i;
			e.sendMsg( '/sc', \volume, (72 - i).neg.dbamp );
			0.1.wait;
		});
	}
)

// This example queries the program version and the number
// of open documents
(
fork {
	var msg, rate, num, startIdx, stopIdx;
	msg = e.query( '/main', \version );
	if( msg.notNil, {
		("Eisenkraut v" ++ msg[ 0 ].round( 0.01 )).inform;
	}, {
		"timeout".warn;
	});
	msg = e.query( '/doc', \count );
	if( msg.notNil, {
		("# of docs = " ++ msg[ 0 ]).inform;
	}, {
		"timeout".warn;
	});
};
)

// The next line opens an audio document
e.sendMsg( '/doc', \open, "~/Desktop/*.aif".standardizePath.pathMatch.choose );
// Select some timeline span in the first document
e.sendMsg( '/doc/index/0/timeline', \select, 4410, 8820 ); // select 100 to 200 milliseconds (assumes 44.1 kHz)
// Delete that span
e.sendMsg( '/doc/active', \delete );
// Make document at index 0 the active one
e.sendMsg( '/doc/index/0', \activate );
// Close it
e.sendMsg( '/doc/active', \close );

// ...or force it to close (will not prompt for confirmation)
e.sendMsg( '/doc/active', \close, true );	// XXX BUG DOESN'T WORK


// Query the active document's name
(
fork {
	var msg, rate, num, startIdx, stopIdx;
	msg = e.query( '/doc/active', \name );
	if( msg.notNil, {
		("Name: "++msg[0]).inform;
	}, {
		"timeout".warn;
	});
};
)


// Close all docs
(
fork {
	var msg, num;
	msg = e.query( '/doc', \count );
	if( msg.notNil, {
		num = msg[ 0 ];
		("# of Docs = "++num).inform;
		num.do({ arg idx;
			e.sendMsg( '/doc/index/0', \close );
		});
	}, {
		"timeout".warn;
	});
};
)
