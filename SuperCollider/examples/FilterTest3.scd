// last mod: 16-Jul-09

(
e = Eisenkraut.default;
e.connect;
fork { e.initTree; e.initSwing };
)

// Example of a realtime plug-in:
// adding equalization
//
// This heavily borrows from MasterEQ by Wouter Snoei!
//
// Rendering is still experimental!
(
x = EisKPlugIn.new.name_( "Parametric EQ" );
e.addProcessPlugIn( x );
fork { e.initTree };

x.populateWindowFunc = { arg plug, win;
	var fnt, fnt2, flow, ggFilterGraph, fltSettings, numBands, minFreq, maxFreq, maxGain, freqGrid, fToControl, fSendCurrent, ggBandTabs,
	    bandViews, ggBypass, isPlaying, fPlay, fStop, fCreateDef, ggRender, ggProgress, synth, selectedBand, numChannels, sr,
	    rPlayStop, condPlayStop, gui, b;

try {
	fnt  = Font( Font.defaultSansFace, 9 );
	fnt2 = Font( Font.defaultSansFace, 11 );
	gui = GUI.current;

	b	= win.bounds.resizeTo( 560, 272 );
	win.bounds_( b );
	flow = FlowLayout( b, 4 @ 4, 4 @ 8 );
	win.view.decorator = flow;
	
	sr = s.sampleRate ? 44100;
	
	ggFilterGraph = UserView( win, b.insetBy( 4, 4 ).height_( 180 ))
//		.focusVisible_( false )
		;
	
	// each: frequency, gain, q
	fltSettings = [[ 100, 0, 1 ], [ 250, 0, 1 ], [ 1000, 0, 1 ], [ 3500, 0, 1 ], [ 6000, 0, 1 ]];
	numBands	= fltSettings.size;
	minFreq	= 16;
	maxFreq	= (sr / 2) * 0.99; // 22050;
	maxGain	= 24;
	freqGrid = [ 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000 ];
	
	fToControl = { fltSettings.collect({ arg item, i; [ item[ 0 ], item[ 2 ].reciprocal, item[ 1 ]]}).flat };
	fSendCurrent = { // set any node listening to \eqControls
		if( synth.notNil, { synth.setn( 'eqControls', fToControl.value )});
	};
		
	ggBandTabs = TabbedView( win, 320 @Ê42,
			[ "Low Shelf", "Peak 1", "Peak 2", "Peak 3", "High Shelf" ],
			{ arg i; Color.hsv( i / numBands, 0.75, 0.5, 0.5 )} ! numBands )
		.font_( fnt2 );

	ggBandTabs.focusActions = { arg i; { selectedBand = i; ggFilterGraph.refresh }} ! numBands;
	
	bandViews = ggBandTabs.views.collect({ arg view, i;
		var vw_array, fltSetting;
		
		view.decorator = FlowLayout( view.bounds.moveTo( 0, 0 )); 
		
		fltSetting = fltSettings[ i ];
		
		StaticText( view, 35 @ 18 ).font_( fnt2 ).align_( \right ).string_( "Freq:" );
		vw_array = vw_array.add( 
			NumberBox( view, 56 @ 18 ).font_( fnt2 ).value_( fltSetting[ 0 ])
				.clipLo_( minFreq ).clipHi_( maxFreq )
				.action_({ arg view;
					fltSetting[ 0 ] = view.value;
					fSendCurrent.value;
					ggFilterGraph.refresh;
				});
		);
		StaticText( view, 18 @ 18 ).font_( fnt2 ).string_( "Hz" );
		StaticText( view, 32 @ 18 ).font_( fnt2 ).align_( \right ).string_( "Gain:" );
		vw_array = vw_array.add( 
			NumberBox( view, 40 @ 18 ).font_( fnt2 ).value_( fltSetting[ 1 ])
				.clipLo_( -24 ).clipHi_( 24 )
				.action_({ arg view;
					fltSetting[ 1 ] = view.value;
					fSendCurrent.value;
					ggFilterGraph.refresh;
				});
		);
		StaticText( view, 14 @ 18 ).font_( fnt2 ).string_( "dB" );
		StaticText( view, 25 @ 18 ).font_( fnt2 ).align_( \right )
			.string_( if( (i == 0) || (i == (numBands - 1)), "S", "Q" ));
		vw_array = vw_array.add( 
			NumberBox( view, 40 @ 18 ).font_( fnt2 ).value_( fltSetting[ 2 ])
				.step_( 0.1 )
				.clipLo_( if( (i == 0) || (i == (numBands - 1)), 0.4, 0.1 ))
				.clipHi_( if( (i == 0) || (i == (numBands - 1)), 1.8, 100 ))
				.action_({ arg view;
					fltSetting[ 2 ] = view.value;
					fSendCurrent.value;
					ggFilterGraph.refresh;
				});
		);
		vw_array;
	});
			
	flow.nextLine;
	ggBypass = Button( win, 80 @ 24 )
		.states_([[ "Bypass" ], [ "Bypass", Color.black, Color.yellow ]])
		.value_( 1 )
		.action_({ arg view; if( view.value == 0, fPlay, fStop )});
		
	ggFilterGraph.mouseDownAction = { arg view, x, y, mod;
		var bounds, pt, newSel;
		
		bounds	= view.bounds;
		pt		= x @ y;
		
		newSel = fltSettings.detectIndex({ arg fltSetting;
			(fltSetting[ 0 ].explin( minFreq, maxFreq, 0, bounds.width ) @
			 fltSetting[ 1 ].linlin( maxGain.neg, maxGain, bounds.height, 0 )).dist( pt ) <= 5;
		});
		if( newSel != selectedBand, {
			selectedBand = newSel;
			if( selectedBand.notNil, { ggBandTabs.focus( selectedBand )});
			view.refresh;
		});
	};
		
	ggFilterGraph.mouseMoveAction = { arg view, x, y, mod;
		var bounds, fltSetting, bandView, value;
		
		bounds	= view.bounds;
		
		if( selectedBand.notNil, {
			fltSetting	= fltSettings[ selectedBand ];
			bandView		= bandViews[ selectedBand ];
			case { (mod & 0x080000) != 0 } // alt
			{
				value = if( (selectedBand == 0) || (selectedBand == (numBands - 1)), {
					y.linexp( 0, bounds.height, 0.4, 1.8 );
				}, {
					y.linexp( 0, bounds.height, 0.1, 10 );
				});
				fltSetting[ 2 ] = if( (mod & 0x020000) != 0, { // shift
					value.nearestInList( if( (selectedBand == 0) || (selectedBand == (numBands - 1)),
						[ 0.4, 0.6, 1.0, 1.4, 1.8 ], [ 0.1, 0.2, 0.4, 1, 2, 4, 10 ]));
				}, {
					value.round( 0.01 );
				});
				bandView[ 2 ].value = fltSetting[ 2 ];
			 }
			{ (mod & 0x020000) != 0 } // shift
			{
				fltSetting[ 0 ] = x.linexp( 0, bounds.width, minFreq, maxFreq ).nearestInList( freqGrid );
				fltSetting[ 1 ] = y.linlin( 0, bounds.height, maxGain, maxGain.neg ).round( 6 );
					
				bandView[ 0 ].value = fltSetting[ 0 ];
				bandView[ 1 ].value = fltSetting[ 1 ];			}
			{ // no special mod
				fltSetting[ 0 ] = x.linexp( 0, bounds.width, minFreq, maxFreq ).round( 1 );
				fltSetting[ 1 ] = y.linlin( 0, bounds.height, maxGain, maxGain.neg ).round( 0.25 );
				bandView[ 0 ].value = fltSetting[ 0 ];
				bandView[ 1 ].value = fltSetting[ 1 ];
			};
			fSendCurrent.value;
			view.refresh;
		});
	};
	
	ggFilterGraph.drawFunc = { arg view;
		var freqs, svals, values, bounds, zeroline;
		var freq = 1200, q = 2, db = 12;
		var vlines;
		var hlines = [ -18, -12, -6, 6, 12, 18 ];
		var pt;
	  GUI.use( gui, {
		
		bounds = view.bounds.moveTo( 0, 0 );
		
		#freq, db, q = fltSettings[ 0 ] ? [ freq, db, q ];
		
		freqs = { arg i; i } ! (bounds.width + 1);
		freqs = freqs.linexp( 0, bounds.width, minFreq, maxFreq );
		
		values = numBands.collect({ arg i;
			if( i == 0, { BLowShelf }, { if( i == (numBands - 1), { BHiShelf }, { BPeakEQ })})
				.magResponse( freqs, sr, fltSettings[ i ][ 0 ], fltSettings[ i ][ 2 ].reciprocal, fltSettings[ i ][ 1 ])
		}).ampdb.clip( -200, 200 );
		
		zeroline = bounds.height / 2;
		svals	= values.sum.linlin( maxGain.neg, maxGain, bounds.height, 0 );
		values	= values.linlin( maxGain.neg, maxGain, bounds.height, 0 );
		vlines	= freqGrid.explin( minFreq, maxFreq, 0, bounds.width );
		
		pt		= fltSettings.collect({ arg fltSetting;
			(fltSetting[ 0 ].explin( minFreq, maxFreq, 0, bounds.width )) @ (fltSetting[1].linlin( maxGain.neg, maxGain, bounds.height, 0 ));
		});

		Pen.strokeColor = Color.gray( 0.2, 0.5 );
		Pen.strokeRect( bounds.insetBy( -1.5, -1.5 ));
//		Pen.addRect( bounds ).clip;
		Pen.strokeColor = Color.gray( 0.2, 0.125 );
		
		hlines.do({ arg y, i;
			y = y.linlin( maxGain.neg, maxGain, bounds.height, 0 );
			Pen.line( 0 @ y, bounds.width @ y );
		});
		vlines.do({ arg x, i;
			if( (i % 4) != 3, {ÊPen.line( x @ 0, x @ bounds.height )});
		});
		Pen.stroke;
	
		Pen.color = Color.gray( 0.2, 0.5 );
		vlines.do({ arg x, i;
			if( (i % 4) == 3, { Pen.line( x @ 0, x @ bounds.height )});
		});
		Pen.line( 0 @ zeroline, bounds.width @ zeroline ).stroke;
		
		Pen.font = fnt;
		
		hlines.do({ arg y;
			Pen.stringAtPoint( if( y > 0, "+%", "%" ).format( y ),
				3 @ (y.linlin( maxGain.neg, maxGain, bounds.height, 0 ) - 13) );
		});
		vlines.do({ arg x, i;
			Pen.stringAtPoint( if( freqGrid[ i ] < 1000,
				{ "%".format( freqGrid[ i ])}, {Ê"%k".format( freqGrid[ i ] / 1000 )}),
				(x + 2) @ (bounds.height - 14) );
		});
		
		values.do({ arg svals, i;
			var color;
			color = Color.hsv( i / numBands, 0.75, 0.5, if( selectedBand == i, 0.75, 0.25 ));
			Pen.color = color;
			Pen.moveTo( 0 @ svals[ 0 ]);
			svals[ 1.. ].do({ arg val, j; Pen.lineTo( (j + 1) @ val )});
			Pen.lineTo( bounds.width @ (bounds.height / 2) );
			Pen.lineTo( 0 @ (bounds.height / 2) );
			Pen.lineTo( 0 @ svals[ 0 ]);
			Pen.fill;
			
			Pen.strokeColor = color.alpha_( 0.75 );
			Pen.addArc( pt[ i ], 5, 0, 2pi );
			Pen.stroke;
		});
		
		Pen.strokeColor = Color.blue( 0.5 );
		Pen.moveTo( 0 @ svals[ 0 ]);
		svals[ 1.. ].do({ arg val, j;
			Pen.lineTo( (j + 1) @ val );
		});
		Pen.stroke;
	  });
	};
	 
	isPlaying = false;
	
	condPlayStop = Condition.new;
	rPlayStop = Routine({
		var pl = false, syn, msg, diskBus, grpInput, def;
		while({ win.isClosed.not }, {
			condPlayStop.wait;
			condPlayStop.test = false;
			if( pl != isPlaying, {
				pl = isPlaying;
				if( isPlaying, {
					msg = e.query( '/doc/active/sc', [ \diskBusIndex, \diskBusNumChannels, \inputGroup ]);
					if( msg.notNil, {
						diskBus 	= Bus( \audio, msg[ 0 ], msg[ 1 ], e.scsynth );
						grpInput	= Group.basicNew( e.scsynth, msg[ 2 ]);
						def = fCreateDef.( diskBus.numChannels );
						syn = Synth.basicNew( def.name, e.scsynth );
						def.send( e.scsynth, syn.newMsg( grpInput, [ \bus, diskBus, \eqControls, fToControl.value ], \addAfter ));
						e.scsynth.sync;
						synth = syn;
					}, {
						"OSC Timeout".error;
					});
				}, {
					synth = nil;
					syn.release;
				});
			});
		});
	}).play( AppClock );
		
	fPlay = {
		isPlaying			= true;
		condPlayStop.test	= true;
		condPlayStop.signal;
	};
		
	fStop = {
		isPlaying			= false;
		condPlayStop.test	= true;
		condPlayStop.signal;
	};
		
	win.onClose = fStop;
	
	fCreateDef = { arg numChannels;
		SynthDef( "filterPEQ" ++ numChannels, { arg bus = 0, gate = 1, fadeTime = 0.05, doneAction = 2;
			// doneAction 14: free group, doneAction 2: free node
			var frdb, input, env;
			env = EnvGen.kr( Env.asr( fadeTime, 1, fadeTime ), gate, doneAction: doneAction );
			input = In.ar( bus, numChannels );
			frdb  = Control.names([ \eqControls ]).kr([ 1000, 1, 0 ].dup( numBands ).flatten ).clump( 3 );
			input = BLowShelf.ar( input, *frdb[ 0 ].lag( 0.1 ));
			input = BPeakEQ.ar(   input, *frdb[ 1 ].lag( 0.1 ));
			input = BPeakEQ.ar(   input, *frdb[ 2 ].lag( 0.1 ));
			input = BPeakEQ.ar(   input, *frdb[ 3 ].lag( 0.1 ));
			input = BHiShelf.ar(  input, *frdb[ 4 ].lag( 0.1 ));
			XOut.ar( bus, env, input );
		});
	};
		
	ggRender = Button( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Render" ]])
		.action_({ arg b;
			{
				var msg, msg2, msg3, oscFileName, oscFile, fna, audioFileName, numChannels, rate, totalLen, cmd, prog, newProg,
				    localThresh, def, oscFileName2, oscRaw;
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
							totalLen = (msg[ 2 ] - msg[ 1 ]) / rate;
							(oscFileName.asString.asCompileString ++ " (numCh = "++numChannels++"; rate = "++rate++"; duration = "++totalLen++") --> " ++ audioFileName).postln;
							"Creating NRT file...".postln;
							e.sendMsg( '/doc/active/sc', \createNRTFile, oscFileName, 0, 0, numChannels, rate );
							
//							[ "oscFileName", oscFileName, "oscFileName2", oscFileName2 ].postcs;
							
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
// oscFileName2="/tmp/test.osc";
						fna = EisKNRTFile.openWrite( oscFileName2.asString );
							"-----------3".postln;
						def = fCreateDef.value( numChannels );
						synth = Synth.basicNew( def.name, e.scsynth );
						fna.sendBundle( 0.0, def.recvMsg );
						fna.sendBundle( 0.0, synth.newMsg( Group.basicNew( e.scsynth, 0 ), [ \eqControls, fToControl.value, \bus, 0 ], \addToTail ));
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
//							"-----------7".postln;
//						oscFile.packets.do({ arg p;
//							fna.sendRaw( p.asRawOSC );
//						});
						fna.closeFile;
						
							"...done".postln;
// audioFileName = desktopFolder ++ "test.aif";
							cmd = "./scsynth -i 0 -o "++numChannels++" -N " ++ oscFileName2 ++ " _ " ++ audioFileName ++ " "++rate++" AIFF float";
														
							ggProgress.knobColor_( Color.blue( 0.3 ));
							ggProgress.hi = 0.0;
							prog = 0.0;
							ggProgress.visible = true;
							Pipe.do( cmd, { arg line, i;
//line.postcs;
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
							
//							e.sendMsg( '/doc/active', \editMode, \overwrite );
							e.sendMsg( '/doc/active/transport', \stop );
							e.sendMsg( '/doc/active', \replace, audioFileName );
							ggBypass.valueAction_( 1 );

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

	ggProgress = RangeSlider( win, 172 @ 24 )
		.canFocus_( false )
		.visible_( false );
} { arg error;
	error.reportError;
};

};
)
