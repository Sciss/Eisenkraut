// lastmod: 02-Jan-09

(
e = Eisenkraut.default;
e.connect;
fork { e.initTree;Êe.initSwing };
)

// WARNING : fails if scsynth wasn't yet booted!

// Example of a realtime plug-in:
// Inserts a synth in the master group
// that converts input M-S (mid-side) signals
// into X-Y signals. For simplicity, this
// just does it in audio output bus of the active document.
// 
// for technical details see http://emusician.com/mag/emusic_front_center/
// 
// Note that a new menu item appears in
// Process -> SuperCollider.
// Choosing this item will execute
// the populateWindowFunc function
// below.
(
var def, synth;

x = EisKPlugIn.new.name_( "M-S Decoder" );
e.addProcessPlugIn( x );
fork { e.initTree };

def = SynthDef( \msDecoder, { arg bus = 0, midAmp = 1.0, sideAmp = 1.0;
	var mid, side, left, right;
	#mid, side = In.ar( bus, 2 );
	mid		= mid * midAmp;
	side		= side * sideAmp;
	left		= mid + side;
	right	= mid - side;
	ReplaceOut.ar( bus, [ left, right ]);
});

x.populateWindowFunc = { arg plug, win;
	var flow, gui, ggSideGain, lbSideGain, ggBypass, spec, sideAmp = 1.0, bypass = true, cond, keepGoing = true;
		
	gui = GUI.current;
	win.bounds_( win.bounds.resizeTo( 360, 60 ));
	
	flow = FlowLayout( win.view.bounds );
	win.view.decorator = flow;
	
	spec = ControlSpec(-20, 20, units: " dB",default: 0); // Spec.specs[ \boostcut ];
	cond = Condition.new;
	fork { var msg, diskBus, grpInput;
		while({ keepGoing }, {
			cond.wait; cond.test = false;
			if( synth.notNil, {
				synth.free; synth = nil;
			});
			if( bypass.not, {
				msg = e.query( '/doc/active/sc', [ \diskBusIndex, \diskBusNumChannels, \inputGroup ]);
				if( msg.notNil, {
					diskBus 	= Bus( \audio, msg[ 0 ], msg[ 1 ], e.scsynth );
					grpInput	= Group.basicNew( e.scsynth, msg[ 2 ]);
					def.send( e.scsynth );
					e.scsynth.sync;
					synth = Synth( \msDecoder, [ \bus, diskBus.index, \sideAmp, sideAmp ], grpInput, \addAfter );
				}, {
					"OSC Timeout!".error;
				});
			});
		});
	};
	
	GUI.staticText.new( win, Rect( 0, 0, 60, 24 ))
		.align_( \right )
		.string_( "Side Gain" );

	ggSideGain = GUI.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( spec.unmap( 0 ))
		.action_({ arg b; var db;
			db = spec.map( b.value );
			lbSideGain.string = db.round( 0.1 ).asString + "dB";
			sideAmp = db.dbamp;
			if( synth.notNil, {Êsynth.set( \sideAmp, sideAmp )});
		});
	lbSideGain = GUI.staticText.new( win, Rect( 0, 0, 60, 24 ))
		.string_( "0 dB" );

	flow.nextLine;

	ggBypass = gui.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Bypass" ], [ "Bypass", Color.black, Color.yellow ]])
		.value_( 1 )
		.action_({ arg b;
			bypass = b.value == 1;
			cond.test = true; cond.signal;
		});
	ggBypass.onClose = { keepGoing = false; bypass = true; cond.test = true; cond.signal };
	
};
)
