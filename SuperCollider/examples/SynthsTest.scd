// lastmod: 03-Jun-08

// Eisenkraut administrates its own scsynth
// instance. On the SuperCollider client side,
// this becomes available when calling
// the initTree method. Upon success, the
// synthesis server is found in Eisenkraut.default.scsynth

// NOTE: the synthesis server must have been booted
// before executing this code block!
(
e = Eisenkraut.default;
e.addr.connect;
e.dumpOSC;
fork {Êe.initTree }
)

e.scsynth.class;	// should return 'EisKSynthServer'
e.scsynth.addr.isConnected;	// should return 'true' (initTree establishes the TCP connection)
//e.scsynth.addr.connect;

// This example assumes you have an open active
// document in Eisenkraut. It queries the document's
// diskBus (the bus to which the soundfile is routed
// before the panorama stage). It will place a new
// synth after the document's inputGroup (the group
// in which the buffer-reading synths are placed).
// This synth will filter the bus content by modulation
// with a sine (i.e. ring modulation).
(
fork {
	var msg, inputGroup, diskBus, numChannels, index, defName;

	msg = e.query( '/doc/active/sc', \inputGroup );
	if( msg.notNil, {
		inputGroup = Group.basicNew( e.scsynth, msg[ 0 ]);
		msg = e.query( '/doc/active/sc', [Ê\diskBusIndex, \diskBusNumChannels ]);
		if( msg.notNil, {
			index		= msg[ 0 ];
			numChannels 	= msg[ 1 ];
//			diskBus 		= Bus( \audio, index, numChannels, e.scsynth );
			defName		= "filter" ++ numChannels;
			SynthDef( defName, {Êarg bus, freq = 441;
				ReplaceOut.ar( bus, In.ar( bus, numChannels ) * SinOsc.ar( freq ));
			}).send( e.scsynth );
			e.scsynth.sync;
			~filter = Synth( defName, [ \bus, index ], inputGroup, \addAfter );
		}, {
			"timeout".warn;
		});
	}, {
		"timeout".warn;
	});
};
)

// Here a small GUI to control the ring modulation frequency:

fork { e.initSwing };

(
	var gg, flow, bounds;

	bounds = JSCWindow.screenBounds( g );
	w = EisKPlugInWindow( "Ring Modulation", Rect( (bounds.width - 360) >> 1, (bounds.height - 64) >> 1, 360, 64 ),
					   resizable: false, server: e.swing );
	flow = FlowLayout( w.view.bounds );
	w.view.decorator = flow;
	GUI.useID( \swing, { EZSlider( w, 350 @ 22, "Freq", Spec.specs[ \freq ], { arg ez;
		~filter.set( \freq, ez.value );
	}, labelWidth: 60 )});
	flow.nextLine;
	flow.shift( 226, 4 );
	JSCButton( w, Rect( 0, 0, 60, 22 ))
		.states_([[ "Preview" ], [ "Preview", Color.white, Color.blue ]])
		.action_({ arg b; /* to do */ });
	JSCButton( w, Rect( 0, 0, 60, 22 ))
		.states_([[ "Render" ]])
		.action_({ arg b; /* to do */ });
	
	w.front; 
)

~filter.free;
