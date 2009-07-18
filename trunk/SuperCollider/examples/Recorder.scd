// lastmod: 03-Jun-08

(
e = Eisenkraut.default;
e.connect;
fork { e.initTree;Êe.initSwing };
)

e.swing.dumpOSC(1);
e.swing.dumpOSC(0);

// WARNING : fails if scsynth wasn't yet booted!

// Example of a realtime plug-in:
// A recorder GUI that let's you
// record everything that comes out
// of a certain bus. For simplicity
// you specify bus index and numChannels
// manually instead of hooking it
// up to automatically match the control room's
// output configuration.
// 
// Note that a new menu item appears in
// Process -> SuperCollider.
// Choosing this item will execute
// the populateWindowFunc function
// below.
(
var headerSuffix, ggTimer, ggChannelOffset, ggNumChannels, channelOffset = 0, numChannels = 2, headerFormat = "aiff", ggHeaderFormat, ggSampleFormat, sampleFormat = "float", folder, ggFolder, ggFile, file = "", autoFile = true, routTimer;

x = EisKPlugIn.new.name_( "Control Room Recorder" );
e.addProcessPlugIn( x );
fork { e.initTree; };

headerSuffix		= IdentityDictionary.new;
headerSuffix.put( \aiff, "aif" );
headerSuffix.put( \next, "au" );
headerSuffix.put( \wav, "wav" );
headerSuffix.put( \ircam, "irc" );
headerSuffix.put( \raw, "raw" );

folder = "~/Desktop".standardizePath ++ "/";

x.populateWindowFunc = { arg plug, win;
	var flow, gui, headerFormats, sampleFormats;

	("win.server.addr : "++win.server.addr).postln;
	
	gui = GUI.current;
	win.bounds_( win.bounds.resizeTo( 368, 156 ));
	
	flow = FlowLayout( win.view.bounds );
	win.view.decorator = flow;
	
	GUI.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.align_( \right )
		.string_( "Folder" );

	ggFolder = GUI.dragSink.new( win, Rect( 0, 0, 280, 20 ))
//		.resize_( 2 )
		.object_( folder )
		.action_({ arg b;
			folder = b.object;
		});
	GUI.button.new( win, Rect( 0, 0, 20, 20 ))
//		.resize_( 3 )
		.states_([[ "..." ]])
		.canFocus_( false )
		.action_({ arg b;
			GUI.use( gui, {
				GUI.dialog.savePanel({ arg path;
					ggFolder.object = path;
					ggFolder.doAction;
				});
			});
		});

	flow.nextLine;

	GUI.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.align_( \right )
		.string_( "File" );

	ggFile = GUI.textField.new( win, Rect( 0, 0, 260, 20 ))
//		.resize_( 2 )
//		.object_( file )
		.enabled_( autoFile.not )
		.action_({ arg b;
			file = b.object;
		});
	GUI.button.new( win, Rect( 0, 0, 40, 20 ))
//		.resize_( 3 )
		.states_([[ "Auto" ], [ "Auto", Color.white, Color.blue ]])
		.canFocus_( false )
		.value_( autoFile.binaryValue )
		.action_({ arg b;
			autoFile = b.value == 1;
			ggFile.enabled( autoFile.not );
		});

	flow.nextLine;
	GUI.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.align_( \right )
		.string_( "Format" );

	headerFormats = headerSuffix.keys.asArray.performUnaryOp( \asString ).sort;

	ggHeaderFormat = GUI.popUpMenu.new( win, Rect( 0, 0, 80, 24 ))
		.canFocus_( false )
		.items_( headerFormats )
//		.value_( headerFormats.indexOf( headerFormat ))
		.value_( headerFormats.collect({ arg item, idx; if( item == headerFormat, idx, -1 )}).maxItem )
		.action_({ arg b;
			headerFormat = headerFormats[ b.value ];
		});

	sampleFormats = [ "int16", "int24", "int32", "float" ];
	ggSampleFormat = GUI.popUpMenu.new( win, Rect( 0, 0, 80, 24 ))
		.canFocus_( false )
		.items_( sampleFormats )
//		.value_( sampleFormats.indexOf( sampleFormat ))
		.value_( sampleFormats.collect({ arg item, idx; if( item == sampleFormat, idx, -1 )}).maxItem )
		.action_({ arg b;
			sampleFormat = sampleFormats[ b.value ];
		});
	~ggSampleFormat = ggSampleFormat;

	flow.nextLine;

	GUI.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.align_( \right )
		.string_( "Bus" );

	ggChannelOffset = GUI.numberBox.new( win, Rect( 0, 0, 36, 24 ))
		.align_( \right )
		.object_( channelOffset )
		.action_({ arg b;
			channelOffset = b.value.asInteger;
		});
		
	GUI.staticText.new( win, Rect( 0, 0, 48, 24 ))
		.align_( \right )
		.string_( "Chans" );

	ggNumChannels = GUI.numberBox.new( win, Rect( 0, 0, 36, 24 ))
		.align_( \right )
		.object_( numChannels )
		.action_({ arg b;
			numChannels = b.value.asInteger;
		});
			
	flow.nextLine;
	flow.shift( 48, 8 );

	~ggRec = GUI.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Rec" ]])
		.action_({ arg b;
			var path, target, targetAddAction;
			b.enabled = false;
			{
				target			= nil;
				targetAddAction	= \addToTail;
		//		folder = "recordings/";
				if( autoFile, {
					file = "SC_" ++ Date.localtime.stamp ++ "." ++ headerSuffix[ headerFormat.asSymbol ];
					ggFile.object = file;
				});
				path = folder ++ file;
				~buf = EisKBuffer.alloc( e.scsynth, 65536, numChannels );
				~buf.write( path, headerFormat, sampleFormat, 0, 0, true );
				SynthDef( "simpleRecorder" ++ numChannels, { arg i_bus, i_buf;
					DiskOut.ar( i_buf, In.ar( i_bus, numChannels )); 
				}).send( e.scsynth );
				e.scsynth.sync;
				~node = Synth( "simpleRecorder" ++ ~buf.numChannels, [ \i_buf,  ~buf.bufnum,
					\i_bus, channelOffset ], target ?? { RootNode( e.scsynth )}, targetAddAction );
				~ggStop.enabled = true;
				routTimer = fork {
					inf.do({ arg secs;
						ggTimer.string_( (secs + 360000).asTimeString.copyToEnd( 1 ));
						1.wait;
					});
				};
			}.fork( AppClock );
		});
	~ggStop = GUI.button.new( win, Rect( 0, 0, 80, 24 ))
		.states_([[ "Stop" ]])
		.enabled_( false )
		.action_({ arg b;
			b.enabled = false;
			~node.free;
			~node = nil;
			routTimer.stop;
			routTimer = nil;
			if( ~buf.notNil, {
				~buf.close({ arg buf; buf.free; });
				~buf = nil; 
			});
			~ggRec.enabled = true;
		});

	ggTimer = GUI.staticText.new( win, Rect( 0, 0, 72, 24 ))
		.string_( "00:00:00" );
	
};
)
