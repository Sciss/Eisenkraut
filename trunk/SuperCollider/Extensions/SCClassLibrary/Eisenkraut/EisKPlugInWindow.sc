/*
 *	EisKPlugInWindow
 *
 *	@author		Hanns Holger Rutz
 *	@version		0.70, 13-Feb-08
 *
 *	@todo		WindowResponder doesn't work obviously, have to create an AppWindowResponder in EisK i guess
 */
EisKPlugInWindow : JSCWindow
{
	*new { arg name = "Plug In", bounds, resizable = true, border = true, server, scroll = false;
		^super.new( name, bounds, resizable, border, server, scroll );
	}

	prInit { arg argName, argBounds, resizable, border, scroll; // , view;
		var viewID, bndl;

		bounds 	= argBounds;
		// tricky, we have to allocate the TopView's id here
		// to be able to assign our content pane to it, so
		// that JSCView can add key and dnd listeners
		viewID	= server.nextNodeID;

		acResp = OSCpathResponder( server.addr, [ '/window', this.id ], { arg time, resp, msg;
			var state;
		
			state = msg[2].asSymbol;
			case
			{ state === \resized }
			{
//				bounds = this.prBoundsFromJava( Rect( msg[3], msg[4], msg[5], msg[6] ));
				bounds = Rect( msg[3], msg[4], msg[5], msg[6] );
//				if( drawHook.notNil, { this.refresh });
			}
			{ state === \moved }
			{
//				bounds = this.prBoundsFromJava( Rect( msg[3], msg[4], msg[5], msg[6] ));
				bounds = Rect( msg[3], msg[4], msg[5], msg[6] );
			}
			{ state === \closing }
			{
				if( userCanClose, {
					{ this.prClose }.defer;
				});
			}
		}).add;

//		server.sendBundle( nil,
//			[ '/set', '[', '/local', this.id, '[', '/new', "de.sciss.swingosc.Frame" ] ++ argName.asSwingArg ++ [ scroll, ']', ']',
//				\bounds ] ++ this.prBoundsToJava( argBounds ).asSwingArg ++ if( resizable.not, [ \resizable, 0 ]) ++
//				if( border.not, [ \undecorated, 1 ]),
//			[ '/local', "ac" ++ this.id,
//				'[', '/new', "de.sciss.swingosc.WindowResponder", this.id, ']',
//				viewID, '[', '/method', this.id, "getContentPane", ']' ]
//		);
		bndl = Array( 3 );
		bndl.add([ '/local', this.id, '[', '/new', "de.sciss.eisenkraut.net.PlugInWindow" ] ++ argName.asSwingArg ++ argBounds.asSwingArg ++ [ border.not.binaryValue | (scroll.binaryValue << 1) |Ê(resizable.not.binaryValue << 2), ']', ]);
//		if( resizable.not, { bndl.add([ '/set', this.id, \resizable, 0 ])});
		bndl.add([ '/local', "ac" ++ this.id,
				'[', '/new', "de.sciss.swingosc.WindowResponder", this.id, ']',
				viewID, '[', '/method', this.id, "getContentPane", ']' ]);
		server.listSendBundle( nil, bndl );

		view = if( scroll, {
			JSCScrollTopView( this, argBounds.moveTo( 0, 0 ), viewID );
		}, {
			JSCTopView( this, argBounds.moveTo( 0, 0 ), viewID );
		});
	}
}