// lastmod: 03-Jun-08

// we have a SwingOSC bridge built-in Eisenkraut
// that we can use to create custom GUI. The
// initSwing method in Eisenkraut will boot the
// SwingOSC server according to the settings in
// the preferences (go to the OSC tab and verify
// that SwingOSC.jar is properly located, it's
// not included in Eisenkraut, so you might need to
// install SwingOSC for SuperCollider separately)

(
e = Eisenkraut.default;
e.addr.connect;
e.dumpOSC;
fork {Êe.initSwing };
)


// While it's possible to directly instantiate a JSCWindow,
// it is not recommended because of the menu bar and floating
// palette management integration. Instead you use
// EisKPugInWindow which is more or less functionally
// equivalent to JSCWindow.
//
// NOTE: make sure to pass in Eisenkraut.default.swing as
// the window's server argument, unless you make that
// the default SwingOSC server!
(

w = EisKPlugInWindow( "Plog", Rect(128, 64, 340, 360), server: e.swing );

32.do({ arg i;
	b = JSCButton(w, Rect(rrand(20,300),rrand(20,300), 75, 24));
	b.states = [["Start "++i, Color.black, Color.rand],
		["Stop "++i, Color.white, Color.red]];
});

w.front; 

)


w.close;
