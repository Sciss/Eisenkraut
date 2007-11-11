/*
 *	EisKPlugInWindow
 *
 *	@author		Hanns Holger Rutz
 *	@version		0.70, 05-Aug-07
 *
 *	@todo		WindowResponder doesn't work obviously, have to create an AppWindowResponder in EisK i guess
 */
EisKPlugInWindow : JSCWindow
{
	*new { arg name = "Plug In", bounds, resizable = true, server;
		^super.new.initSCWindow( name, bounds, resizable, true, server );
	}

	prInit { arg argName, argBounds, resizable, border; // , view;
		var viewID;

		bounds 	= argBounds;
		// tricky, we have to allocate the TopView's id here
		// to be able to assign our content pane to it, so
		// that JSCView can add key and dnd listeners
		viewID	= server.nextNodeID;

		acResp = OSCresponderNode( server.addr, "/window", { arg time, resp, msg;
			var state;
		
			case { msg[1] == this.id }
			{
				state = msg[2].asSymbol;
				case { (state === \resized) || (state === \moved) }
				{
					// XXX
				}
				{ state === \closing }
				{
					if( userCanClose, {
						{ this.prClose; }.defer;
					});
				}
			};
		}).add;

//		server.sendBundle( nil,
//			[ '/set', '[', "/local", this.id, '[', "/new", "de.sciss.swingosc.Frame", argName, ']', ']',
//				\bounds ] ++ this.prBoundsToJava( argBounds ).asSwingArg ++ [ \resizable, resizable,
//				\undecorated, border.not ],
//			[ '/set', '[', "/local", viewID, '[', '/method', this.id, "getContentPane", ']', ']',
//				\layout, '[', '/ref', \null, ']' ],
//			[ "/local", "ac" ++ this.id,
//				'[', "/new", "de.sciss.swingosc.WindowResponder", this.id, ']' ]
//		);

		server.sendBundle( nil,
			[ '/set', '[', '/local', this.id, '[', "/new", "de.sciss.eisenkraut.net.PlugInWindow", ']', ']',
				\bounds ] ++ this.prBoundsToJava( argBounds ).asSwingArg ++ [ \resizable, resizable,
				\title, argName ],
			[ '/set', '[', '/local', viewID, '[', '/method', this.id, \getContentPane, ']', ']',
				\layout, '[', '/ref', \null, ']' ],
			[ '/local', "ac" ++ this.id,
				'[', '/new', 'de.sciss.swingosc.WindowResponder', this.id, ']' ]
		);

		view = JSCTopView( this, argBounds.moveTo( 0, 0 ), id: viewID );
	}
}