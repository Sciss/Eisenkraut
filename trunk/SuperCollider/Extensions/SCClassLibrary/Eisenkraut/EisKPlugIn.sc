/**
 *	@version	0.70, 19-May-08
 *	@author	Hanns Holger Rutz
 */
EisKPlugIn {
	var <eisk, <>name, <>id, <>populateWindowFunc, <>previewFunc, <>renderFunc, <win;
	
	*new { arg eisk;
		^super.newCopyArgs( eisk ? Eisenkraut.default ).prInit;
	}
	
	prInit {
	}

	makeWindow {
		var wb, sb;
		
//"Now we are KKKK.".postln;
		if( win.notNil and: { win.isClosed.not }, {
			^win;
		});
//"Now we are GAGA.".postln;
		win = EisKPlugInWindow( this.name, resizable: false, server: eisk.swing );
//"Now we are JJJJ.".postln;
		GUI.useID( \swing, { populateWindowFunc.value( this, win )});
		wb	= win.bounds;
		sb	= win.class.screenBounds;		
		win.bounds_( Rect( ((sb.width - wb.width) * 0.5).asInt, ((sb.height - wb.height) * (1 - 0.5)).asInt,
				    wb.width, wb.height ));
		^win; // .front;
	}
	
	makePreviewButton { arg parent;
		parent = parent ?? win;
		
		^JSCButton( parent, Rect( 0, 0, 80, 22 ))
			.states_([[ "Preview" ], [ "Preview", nil, Color( 0.9, 0.7, 0.0 )]])
			.action_({ arg b; previewFunc.value( this, b.value == 1 ); });
	}
	
	makeRenderButton { arg parent;
		parent = parent ?? win;
	
		^JSCButton( parent, Rect( 0, 0, 80, 22 ))
			.states_([[ "Render" ]])
			.action_({ arg b; renderFunc.value( this, b.value == 1 ); });
	}
}