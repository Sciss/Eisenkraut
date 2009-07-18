// last mod: 03-Jun-08

e = Eisenkraut.default;

// This example renames
// markers of the active document.
// It will look for markers
// named as specified in the
// variable oldName, and will
// rename these to the name
// specified in the variable newName.
(
fork {
	var msg, num, startIdx, stopIdx, newMarks, delMarks, oldName, newName;
	
	oldName	= \Mark;
	newName	= \attk;
	
	newMarks	= List.new;
	delMarks	= List.new;
	msg = e.query( '/doc/active/markers', \count );
	if( msg.notNil, {
		num = msg[ 0 ];
		("Number of markers: "++num).postln;
		
		// read markers
		
		startIdx = 0;
		// maximum 128 markers per query, based on an estimated of maximum marker names ...
		// 128 * (32 + 4 + 5) + headerSize = ca. 5000 bytes
		stopIdx = min( num, startIdx + 128 );
		while({ startIdx < num }, {
			msg = e.get( '/doc/active/markers', [ \range, startIdx, stopIdx ]);
			if( msg.notNil, {
				msg.pairsDo({ arg pos, name, i;
					if( name.asSymbol === oldName, {
						newMarks.add( pos );
						newMarks.add( newName );
						delMarks.add( (i >> 1) + startIdx );
//					}, {
//						("Not our marker : '"++name++"'").inform;
					});
				});
			}, {
				"timeout".warn;
			});
			startIdx	= stopIdx;
			stopIdx	= min( num, startIdx + 128 );
		});
		
		// clear markers

		startIdx	= 0;
		num		= delMarks.size;
		stopIdx	= min( num, startIdx + 1024 );
		while({ startIdx < num }, {
			e.listSendMsg([ '/doc/active/markers', \remove, \at, delMarks.copyRange( startIdx, stopIdx - 1 )].flatten );
			startIdx	= stopIdx;
			stopIdx	= min( num, startIdx + 1024 );
		});

		// write markers

		startIdx	= 0;
		num		= newMarks.size;
		stopIdx	= min( num, startIdx + 256 );
		while({ startIdx < num }, {
			e.listSendMsg([ '/doc/active/markers', \add, newMarks.copyRange( startIdx, stopIdx - 1 )].flatten );
			startIdx	= stopIdx;
			stopIdx	= min( num, startIdx + 256 );
		});
		
	}, {
		"timeout".warn;
	});
}
)
	¥	
// This example will
// delete duplicate markers,
// that is markers whose
// name and position are equal.
// It will also print warnings
// if two markers share the same
// position but have different names.
(
fork {
	var msg, num, startIdx, stopIdx, newMarks, delMarks, lastName , lastPos;
	
	newMarks	= List.new;
	delMarks	= List.new;
	msg = e.query( '/doc/active/markers', \count );
	if( msg.notNil, {
		num = msg[ 0 ];
		("Number of markers: "++num).postln;
		
		// read markers
		
		startIdx = 0;
		// maximum 128 markers per query, based on an estimated of maximum marker names ...
		// 128 * (32 + 4 + 5) + headerSize = ca. 5000 bytes
		stopIdx = min( num, startIdx + 128 );
		lastName = nil;
		lastPos  = -1;
		while({ startIdx < num }, {
			msg = e.get( '/doc/active/markers', [ \range, startIdx, stopIdx ]);
			if( msg.notNil, {
				msg.pairsDo({ arg pos, name, i;
					name = name.asSymbol;
					if( pos === lastPos, {
						if( name === lastName, {
							("Deleting duplicate pos "++pos).inform;
							delMarks.add( (i >> 1) + startIdx );
						}, {
							("Warning: duplicate pos "++pos++" with different names ('"++lastName++"', '"++name++"')").inform;
						});
					});
					lastName = name;
					lastPos  = pos;
				});
			}, {
				"timeout".warn;
			});
			startIdx	= stopIdx;
			stopIdx	= min( num, startIdx + 128 );
		});
		
		// clear markers

		startIdx	= 0;
		num		= delMarks.size;
		stopIdx	= min( num, startIdx + 1024 );
		while({ startIdx < num }, {
			e.listSendMsg([ '/doc/active/markers', \remove, \at, delMarks.copyRange( startIdx, stopIdx - 1 )].flatten );
			startIdx	= stopIdx;
			stopIdx	= min( num, startIdx + 1024 );
		});		
	}, {
		"timeout".warn;
	});
}
)
	¥	
// This example will create markers
// at the border (start and stop position)
// of the current selection (using a GUI)

fork { e.initSwing }; // make sure SwingOSC is initialized
(
	var w, ggLeftName, ggRightName, flow;
	
	w = EisKPlugInWindow( "Markers for Selection", Rect( 400, 700, 212, 100 ), server: e.swing );
	flow = FlowLayout( w.view.bounds );
	w.view.decorator = flow;
	
	JSCStaticText( w, Rect( 0, 0, 80, 24 ))
		.string_( "Left Name" );
	ggLeftName = JSCTextField( w, Rect( 0, 0, 120, 24 ))
		.string_( "Left" );
	flow.nextLine;
	JSCStaticText( w, Rect( 0, 0, 80, 24 ))
		.string_( "Right Name" );
	ggRightName = JSCTextField( w, Rect( 0, 0, 120, 24 ))
		.string_( "Rght" );
	flow.nextLine;
	JSCStaticText( w, Rect( 0, 0, 80, 24 ));
	JSCButton( w, Rect( 0, 0, 120, 24 ))
		.states_([[ "Place" ]])
		.action_({ arg b;
			var leftN, rightN, msg, start, stop;
			
			leftN	= ggLeftName.string;
			rightN	= ggRightName.string;
		
			fork {
				msg = e.query( '/doc/active/timeline', [ \selectionStart, \selectionStop ]);
				if( msg.notNil, {
					start = msg[ 0 ];
					stop  = msg[ 1 ];
					("Selection: " ++ start ++ " ... " ++ stop).inform;
					if( stop > start, {
						e.sendMsg( '/doc/active/markers', \add, start, leftN, stop, rightN );
					});
				}, {
					"timeout".warn;
				});
			
			};
		});
	
	w.front;
)
