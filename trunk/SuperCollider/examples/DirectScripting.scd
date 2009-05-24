e = Eisenkraut.default;
e.addr.connect;
fork { e.initSwing };

~appC = JavaObject.getClass( "de.sciss.app.AbstractApplication", e.swing );
~app = ~appC.getApplication__;  // instance of de.sciss.eisenkraut.Main
~docHandler = ~app.getDocumentHandler__;
~doc = ~docHandler.getActiveDocument__;
~timeline = JavaObject.getField( ~doc, \timeline );

// example: zoom in from 100% to 10%

(
var frames, zoomSpan;
fork {
	frames = ~timeline.getLength_; // in sample frames
	"Document has % frames\n".postf( frames );
	// note: Span is an extra SuperCollider proxy class from my lib!
	// it implements the asSwingArg method and hence can be used directly
	// as an argument to a JavaObject!
	zoomSpan = Span( 0, frames );
	~timeline.editScroll( ~timeline, zoomSpan );
	2.wait;
	"Now zooming in!".postln;
	(99..10).do({ arg i;
		zoomSpan = Span( 0, frames * i / 100 );
		~timeline.editScroll( ~timeline, zoomSpan );
		0.02.wait;
	});
};
)

~timeline.edit