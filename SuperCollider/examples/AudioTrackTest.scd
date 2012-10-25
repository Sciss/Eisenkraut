// last mod: 03-Jun-08

e = Eisenkraut.default;
e.connect;

// Just a stupid test of
// the audio track functionalities:
// This code block mutes all tracks,
// and shifts each track's panorama azimuth
// by 22.5 degrees
(
fork {
	var msg, num, success, azi, spread, addr, bndl;
	
//	5.wait;
	
	msg = e.query( '/doc/active/audio', \count );
	if( msg.notNil, {
		num = msg[ 0 ];
		("Number of audio tracks: "++num).postln;

		bndl = List.new;

		if( block { arg break;
			num.do({ arg idx;
				addr = "/doc/active/audio/" ++ idx;
				msg = e.query( addr, [ \panAzimuth, \panSpread ]);
				if( msg.notNil, {
					azi 		= msg[ 0 ] + 22.5;
					spread	= msg[ 1 ];
					//	public static final int FLAGS_SOLO			= 0x01;
					//	public static final int FLAGS_MUTE			= 0x02;
					//	public static final int FLAGS_SOLOSAFE		= 0x04;
					//	public static final int FLAGS_VIRTUALMUTE	= 0x08;
					bndl.add([ addr, \flags, 0x02, 0 ]);
					bndl.add([ addr, \pan, azi, spread ]);
				}, {
					break.value( false );
				});
			});
			true;
		}, {
			e.listSendBundle( nil, bndl );
		}, {
			"timeout".warn;
		});
	}, {
		"timeout".warn;
	});
}
	¥	)
