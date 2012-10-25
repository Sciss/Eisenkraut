// last mod: 19-May-08

(
e = Eisenkraut.default;
e.connect;
fork { e.initTree; e.initSwing };
)

// Example of a realtime plug-in:
// FFT-Filter that erases bins
// above a certain magnitude.
//
// WARNING : scsynth must be booted
// before executing this code!
//
// After executing this code,
// a new menu item appears in
// Process -> SuperCollider.
// Choosing this item will execute
// the populateWindowFunc function
// below.
(
x = EisKPlugIn.new.name_( "Mag Below Filter" );
e.addProcessPlugIn( x );
fork { e.initTree; };

x.populateWindowFunc = { arg plug, win;
	var flow, gui, synth, buf, ggFFTSize, ggThreshold, ggBypass, ggRender, ggProgress, fftSize = 1024,
	    threshold = -24.dbamp, threshSpec, lbThreshold, fStop, fCreateDef;

try {
	gui = GUI.current;
	win.bounds_( win.bounds.resizeTo( 368, 100 ));
	
	flow = FlowLayout( win.view.bounds );
	win.view.decorator = flow;
	
	gui.staticText.new( win, Rect( 0, 0, 64, 24 ))
		.align_( \right )
		.string_( "FFT Size" );

	ggFFTSize = gui.popUpMenu.new( win, Rect( 0, 0, 100, 24 ))
		.canFocus_( false )
		.items_([ "32", "64", "128", "256", "512", "1024", "2048", "4096" ])
		.value_( 5 )
		.action_({ arg b;
			fftSize = 1 << (b.value + 5);
		});

	gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "samples" );

	flow.nextLine;

	gui.staticText.new( win, Rect( 0, 0, 64, 24 ))
		.align_( \right )
		.string_( "Threshold" );

	threshSpec = ControlSpec( -96, 0, 'lin' ); // 'db'
	ggThreshold = gui.slider.new( win, Rect( 0, 0, 240, 20 ))
		.value_( threshSpec.unmap( -24 ))
		.action_({ arg b;
			threshold = threshSpec.map( b.value ).dbamp;
			lbThreshold.string = threshSpec.map( b.value ).round( 0.1 ).asString ++ " dB";
			if( synth.notNil, { synth.set( \threshold, threshold )});
		});
	lbThreshold = gui.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.string_( "-24 dB" );

	flow.nextLine;
	flow.shift( 8, 8 );

	fStop = {
		synth.free;
		synth = nil;
		buf.do(_.free);
		buf = nil;
	};
	
	fCreateDef = { arg numChannels, localBuf, localFFTSize;
		SynthDef( \filterMagBelow, { arg threshold = 0, bus = 0;
			var inp, filter;
			inp 		= In.ar( bus, numChannels ).asArray;
			threshold	= threshold * localFFTSize;
			filter	= Array.fill( numChannels, { arg ch;
				IFFT( PV_MagBelow( FFT( localBuf[ ch ].bufnum, inp[ ch ]), threshold ));
			});
			ReplaceOut.ar( bus, filter );
		});
	};
	
	ggBypass = gui.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Bypass" ], [ "Bypass", Color.black, Color.yellow ]])
		.value_( 1 )
		.action_({ arg b;
			if( b.value == 0, {
				{
					var localBuf, msg, grpInput, diskBus, localThresh, localFFTSize, def;
					
						msg = e.query( '/doc/active/sc', [ \diskBusIndex, \diskBusNumChannels, \inputGroup ]);
						if( msg.notNil, {
						diskBus 	= Bus( \audio, msg[ 0 ], msg[ 1 ], e.scsynth );
						grpInput	= Group.basicNew( e.scsynth, msg[ 2 ]);
						localFFTSize = fftSize;
						localThresh = threshold;
						localBuf	= Array.fill( diskBus.numChannels, {
							EisKBuffer.alloc( e.scsynth, localFFTSize, 1 );
						});
						def = fCreateDef.value( diskBus.numChannels, localBuf, localFFTSize );
						synth = Synth.basicNew( def.name, e.scsynth );
						def.send( e.scsynth, synth.newMsg( grpInput, [ \threshold, localThresh, \bus, diskBus.index ], \addAfter ));
						buf		= localBuf;
					});
				}.fork( AppClock );
			}, {
				fStop.value;
			});
		});
	ggBypass.onClose = fStop; // XXX NEVER GETS CALLED

	ggRender = gui.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Render" ]])
		.action_({ arg b;
			{
				var msg, msg2, msg3, oscFileName, oscFile, fna, audioFileName, numChannels, rate, totalLen, cmd, prog, newProg,
				    localFFTSize, localThresh, localBuf, def, oscFileName2, oscRaw, blockSize, delay;
				msg		= e.sendMsgSync( '/main', \createTempFile );
				msg2	= e.sendMsgSync( '/main', \createTempFile );
				msg3	= e.sendMsgSync( '/main', \createTempFile );
				if( msg.notNil && msg2.notNil && msg3.notNil, {
					oscFileName = msg.first;
					oscFileName2 = msg2.first;
					audioFileName = msg3.first;
					msg = e.query( '/doc/active/sc', [ \diskBusNumChannels ]);
					if( msg.notNil, {
						numChannels = msg.first;
						msg = e.query( '/doc/active/timeline', [ \rate, \selectionStart, \selectionStop ]);
						if( msg.notNil, {
							rate = msg[ 0 ];
						localFFTSize	= fftSize;
						localThresh	= threshold;
						blockSize	  	= 64;
						delay		= localFFTSize - blockSize;
							totalLen = (msg[ 2 ] - msg[ 1 ] + delay) / rate;
							(oscFileName.asString.asCompileString ++ " (numCh = "++numChannels++"; rate = "++rate++"; duration = "++totalLen++") --> " ++ audioFileName).postln;
							"Creating NRT file...".postln;
							e.sendMsg( '/doc/active/sc', \createNRTFile, oscFileName, 0, 0, numChannels, rate );
// XXX BROKEN
//							e.sync;
e.query( '/main', [ \version ]);
							"...done".postln;
							
							1.0.wait;
							
//							"Reading NRT file...".postln;
//
//							oscFile = OSCFile.read( oscFileName );
//							"...done".postln;
//
							"Creating merged NRT file...".postln;
//oscFileName2="/tmp/test.osc";
						fna = FileNetAddr.openWrite( oscFileName2.asString );
//							"-----------1".postln;
						localBuf	= Array.fill( numChannels, { arg ch;
							Buffer( e.scsynth, localFFTSize, 1, ch );
						});
//							"-----------2".postln;
						localBuf.do({ arg buf;
							fna.sendBundle( 0.0, buf.allocMsg );
						});
//							"-----------3".postln;
						def = fCreateDef.value( numChannels, localBuf, localFFTSize );
						synth = Synth.basicNew( def.name, e.scsynth );
						fna.sendBundle( 0.0, def.recvMsg );
						fna.sendBundle( 0.0, synth.newMsg( Group.basicNew( e.scsynth, 0 ), [ \threshold, localThresh, \bus, 0 ], \addToTail ));
//							"-----------4".postln;
//("cp " ++ oscFileName ++ " /tmp/test2.osc").systemCmd;
//							"-----------4.5".postln;
//oscFileName = "/tmp/test2.osc";
						oscFile = File( oscFileName.asString, "rb" );
//							"-----------5".postln;
						oscRaw = Int8Array.newClear( oscFile.length );
						oscFile.read( oscRaw );
//							"-----------6".postln;

						oscFile.close;
						fna.file.write( oscRaw );
							// acount for delay
							fna.sendBundle( totalLen, synth.freeMsg );
//							"-----------7".postln;
//						oscFile.packets.do({ arg p;
//							fna.sendRaw( p.asRawOSC );
//						});
						fna.closeFile;
						
							"...done".postln;
//audioFileName = "/Users/rutz/Desktop/test.aif";
							cmd = "./scsynth -i 0 -o "++numChannels++" -N " ++ oscFileName2 ++ " _ " ++ audioFileName ++ " "++rate++" AIFF float";
							ggProgress.knobColor_( Color.blue( 0.3 ));
							ggProgress.hi = 0.0;
							prog = 0.0;
							ggProgress.visible = true;
							Pipe.do( cmd, { arg line, i;
								if( line.beginsWith( "nextOSCPacket " ), {
									newProg = line.copyToEnd( 14 ).asFloat / totalLen;
									if( (newProg - prog) > 0.01, {
										prog = newProg;
										ggProgress.hi = newProg;
										0.yield;
									});
								}, {
									line.postln;
									0.yield;
								});
							});
							ggProgress.hi = 1.0;
							ggProgress.knobColor = Color.green( 0.75 );
//							win.userCanClose = true;
//							win.name = win.name + "done.";

							e.sendMsg( '/doc/active', \replace, audioFileName, delay );

						}, {
							"timeout".warn;	
						});
					}, {
						"timeout".warn;	
					});
				}, {
					"timeout".warn;	
				});
			}.fork( AppClock );
		});

	ggProgress = GUI.rangeSlider.new( win, Rect( 0, 0, 172, 24 ))
		.visible_( false );
} { arg error;
	error.reportError;
};

};
)
