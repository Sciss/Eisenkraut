/**
 *	Client-side represenation of the Eisenkraut soundfile editor.
 *
 *	@version	0.70, 02-Jan-09
 *	@author	Hanns Holger Rutz
 */
Eisenkraut {
	classvar <>local, <>default, uniqueID, <>timeoutClock;
	var		<name, <addr, <isLocal, <scsynth, <protocol, <swing;

	var		procPlugIns, respMenu, windows;

	*initClass {
		Class.initClassTree( NetAddr );
		Class.initClassTree( OSCresponder );
		Class.initClassTree( AppClock );
//		Class.initClassTree( UI );
		uniqueID		= 0;
		default		= local = Eisenkraut.new( \localhost, NetAddr( "127.0.0.1", 0x4549 ), \tcp );
		timeoutClock	= AppClock;
	}

	*new { arg name, addr, protocol = \tcp;
		^super.new.prInitEisenkraut( name, addr, protocol );
	}

	prInitEisenkraut { arg argName, argAddr, argProtocol;
		name 	= argName;
		addr 	= argAddr;
		protocol	= argProtocol;
		if( addr.isNil, { addr = NetAddr( "127.0.0.1", 0x4549 )});
		isLocal = addr.addr == 2130706433;

		procPlugIns	= IdentityDictionary.new;
		windows		= IdentitySet.new;

		ShutDown.add({ this.dispose });
	}

	connect {
		if( protocol === \tcp, {
			addr.connect;
		});
	}

	disconnect {
		if( protocol === \tcp, {
			addr.disconnect;
		});
	}

	dispose {
		var bndl;

		respMenu.remove;	// nil.remove allowed
		respMenu = nil;
		bndl		= List.new;
		procPlugIns.keysDo({ arg id;
			bndl.add([ '/gui/menu', \remove, id ]);
		});
		if( bndl.notEmpty, { this.listSendBundle( nil, bndl ); });
		windows.do({ arg win;
			win.close;
		});
		windows.clear;
		this.prDisposeSwing;
		this.disconnect;
	}

	prDisposeSwing {
		if( swing.notNil, {
			swing.dispose;
			swing = nil;
		});
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	initSwing {
		var result, cond, port, protocol, running, cancel, timeout = 5, upd;
		this.sendMsg( '/gui', \initSwing );
		result = this.query( '/gui', [ \swingPort, \swingProtocol, \swingRunning ]);
		if( result.isNil, {
			"OSC Timeout".error;
			^false;
		});
		#port, protocol, running = result;
		if( swing.notNil and: { swing.addr.port != port }, {
			this.prDisposeSwing;
		});
		if( swing.isNil, {
			// XXX addr.hostname no good, should check for loopBack yes/no
			swing = SwingOSC( \EisKSwing, NetAddr( addr.hostname, port ));
		});
		if( protocol === \tcp, {
			this.prTryToConnect;
		});
		if( swing.serverRunning.not, {
			cond = Condition.new;
			upd = UpdateListener.newFor( swing, { arg upd;
				if( swing.serverRunning, {
					cancel.stop;
					result = true;
					cond.test = true; cond.signal;
				});
			}, \serverRunning );
			cancel = {
				timeout.wait;
				upd.remove;
				result = false;
				cond.test = true; cond.signal;
			}.fork( AppClock );
			cond.wait;
			^result;
		});
		^true;
	}

	prTryToConnect {
		block { arg break;
			5.do({
				try {
					swing.connect;
					break.value( true ); // succeeded
				} { arg error;
					1.wait;
				};
			});
		};
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	initTree {
		var result, func, win, bndl;

		bndl		= List.new;
		// warning: sort is inplace
		procPlugIns.values.copy.sort({ arg a, b; a.name <= b.name }).do({ arg plug;
			bndl.add([ '/gui/menu', \add, plug.id, \item, 'process.sc', plug.name ]);
		});
		if( bndl.notEmpty, { this.listSendBundle( nil, bndl ); });

		if( respMenu.notNil, {
			respMenu.remove;
			respMenu = nil;
		});
		respMenu = OSCresponderNode( addr, '/gui/menu', { arg time, resp, msg;
			// [ '/gui/menu', id, 'action' ]
			var plug;

			plug = procPlugIns[ msg[ 1 ]];
			if( plug.notNil, {
				fork {
"Now we are here.".postln;
					if( this.initSwing, {

"Now we are there.".postln;
//swing.dumpOSC( 1, 1 );
						win = plug.makeWindow;
//("WIN = "++win).postln;
//~win = win;
//					func = win.onClose;
//					win.onClose = {
//
//						func.value;	// previous function
//					};
						windows.add( win );
						win.front;
					});
				};
			});
		}).add;

//		if( scsynth.isNil, {
			result	= this.query( '/sc', [ \port, \protocol, \running ]);
			^if( result.notNil and: { result[ 0 ] != 0 }, {
				scsynth 	= EisKSynthServer( name, NetAddr( addr.hostname, result[ 0 ]), clientID: 1, eisK: this );
				if( result[ 1 ] === \tcp, { scsynth.addr.connect });
				scsynth.startAliveThread( 0 );
				true;
			}, {
				scsynth	= nil;
				false;
			});
//		}, {
//			^true;
//		});
	}

//	/**
//	 *	@warning	asynchronous; needs to be called inside a Routine
//	 */
//	allocBuffer { arg numFrames, numChannels = 1;
//		var result;
//
//		result = this.sendMsgSync( '/sc', \allocBuf, [ numFrames, numChannels ]);
//		if( result.notNil, {
//			result = Buffer( scsynth, numFrames, numChannels, result[ 0 ]);
//			scsynth.sync;
//		});
//		^result;
//	}
//
//	freeBuffer { arg buf;
//		this.sendMsg( '/sc', \freeBuf, buf.bufnum );
//	}

	sendMsg { arg ... msg;
		addr.sendMsg( *msg );
	}

	sendBundle { arg time ... msgs;
		addr.sendBundle( time, *msgs );
	}

	queryFunc { arg func, path, properties, timeout = 4.0, condition;
		Routine {
			var result;
			result = this.query( path, properties, timeout, condition );
			func.value( result );
		}.play;
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	get { arg path, getArgs, timeout = 4.0, condition;
		^this.prQuery( path, \get, '/get.reply', getArgs, timeout, condition );
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	query { arg path, properties, timeout = 4.0, condition;
		^this.prQuery( path, \query, '/query.reply', properties, timeout, condition );
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	prQuery { arg path, sendCmd, replyCmd, msgArgs, timeout = 4.0, condition;
		var resp, id, result, cancel;

		if( condition.isNil, { condition = Condition.new; });

		id			= this.nextUniqueID;
		msgArgs		= msgArgs.asArray;

// OSCpathResponder seems to be broken
//		resp = OSCpathResponder( addr, [ "/query.reply", id ], {
//			arg time, resp, msg;
//
//msg.postln;
//
//			resp.remove;
////			condition.test = true;
////			condition.signal;
//			doneFunc.value( path, msg.copyToEnd( 2 ));
//		});
		resp = OSCresponderNode( addr, replyCmd, {
			arg time, resp, msg;

			if( msg[ 1 ] == id, {
				if( cancel.notNil, { cancel.stop; });
				resp.remove;
				result			= msg.copyToEnd( 2 );
				condition.test	= true;
				condition.signal;
//				doneFunc.value( path, msg.copyToEnd( 2 ));
			});
		});
		resp.add;
		condition.test = false;
		if( timeout > 0.0, {
			cancel = Task({
				timeout.wait;
				resp.remove;
				result			= nil;
				condition.test	= true;
				condition.signal;

			}, timeoutClock );
			cancel.start;
		});
		addr.sendMsg( path, sendCmd, id, *msgArgs );
		condition.wait;
		^result;
	}

	nextUniqueID {
		uniqueID = uniqueID + 1;
		^uniqueID;
	}

	listSendMsg { arg msg;
		addr.sendMsg( *msg );
	}

 	listSendBundle { arg time, msgs;
		addr.sendBundle( time, *msgs );
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	sendMsgSync { arg path, cmd, msgArgs, timeout = 4.0, condition;
		var respDone, respFailed, cancel, result, list;

		if( condition.isNil ) { condition = Condition.new; };

		respDone	= OSCresponderNode( addr, '/done', { arg time, resp, msg;
			if( msg[ 1 ].asSymbol === path.asSymbol and: { msg[ 2 ].asSymbol === cmd.asSymbol }) {
				if( cancel.notNil, { cancel.stop; });
				resp.remove;
				result			= msg.copyToEnd( 3 );
				condition.test	= true;
				condition.signal;
			};
		});
		respFailed = OSCresponderNode( addr, '/failed', { arg time, resp, msg;
			if( msg[ 1 ].asSymbol === path.asSymbol and: { msg[ 2 ].asSymbol === cmd.asSymbol }) {
				if( cancel.notNil, { cancel.stop; });
				resp.remove;
				result			= nil;
				condition.test	= true;
				condition.signal;
			};
		});
		respDone.add;
		respFailed.add;
		condition.test = false;
		if( timeout > 0.0, {
			cancel = Task({
				timeout.wait;
				respDone.remove;
				respFailed.remove;
				result			= nil;
				condition.test	= true;
				condition.signal;

			}, timeoutClock );
			cancel.start;
		});
		condition.test = false;
		if( msgArgs.notNil, {
			addr.sendMsg( path, cmd, *msgArgs );
		}, {
			addr.sendMsg( path, cmd );
		});
		condition.wait;
		^result;
	}

	/**
	 *	@warning	asynchronous; needs to be called inside a Routine
	 */
	sync { arg bundles, latency, timeout = 4.0, condition; // array of bundles that cause async action
		var resp, id, cancel, result;

		if( condition.isNil, { condition = Condition.new; });

		id = UniqueID.next;
		resp = OSCresponderNode( addr, '/synced', { arg time, resp, msg;
			if( msg[ 1 ] == id, {
				if( cancel.notNil, { cancel.stop; });
				resp.remove;
				result			= true;
				condition.test 	= true;
				condition.signal;
			});
		});
		resp.add;
		condition.test = false;
		if( timeout > 0.0, {
			cancel = Task({
				timeout.wait;
				resp.remove;
				result			= false;
				condition.test	= true;
				condition.signal;

			}, timeoutClock );
			cancel.start;
		});
		if( bundles.isNil, {
			addr.sendBundle( latency, [ '/sync', id ]);
		}, {
			addr.sendBundle( latency, *(bundles ++ [[ '/sync', id ]]));
		});
		condition.wait;
		^result;
	}

	ping { arg n = 1, wait = 0.1, func;
		var result = 0, pingFunc;

//		if( serverRunning.not ) { "server not running".postln; ^this };
		pingFunc = {
			Routine.run {
				var t, dt;
				t = Main.elapsedTime;
				this.sync;
				dt = Main.elapsedTime - t;
				("measured latency:" + dt + "s").postln;
				result = max( result, dt );
				n = n - 1;
				if( n > 0, {
					SystemClock.sched( wait, { pingFunc.value; nil; });
				}, {
					("maximum determined latency of" + name + ":" + result + "s").postln;
					func.value( result );
				});
			};
		};
		pingFunc.value;
	}

	dumpOSC { arg incoming = 1, outgoing;
		/*
			0 - turn dumping OFF.
			1 - print the parsed contents of the message.
			2 - print the contents in hexadecimal.
			3 - print both the parsed and hexadecimal representations of the contents.
		*/
//		dumpMode = code;
		if( outgoing.isNil, {
			this.sendMsg( '/dumpOSC', incoming );
		}, {
			this.sendMsg( '/dumpOSC', incoming, outgoing );
		});
	}

	addProcessPlugIn { arg plug;
		plug.id = this.nextUniqueID;
		procPlugIns.put( plug.id, plug );
	}

	removeProcessPlugIn { arg plug;
		procPlugIns.remove( plug.id );
	}
}

// !!! OBSOLETE !!!
/**
 *	SuperCollider audio processing plug-in
 *
 *	@version	0.10, 01-Aug-06
 *	@author	Hanns Holger Rutz
 */
EisKPlugInGAGA {
	classvar <all;
	var <eisk, <id;

	*initClass {
		all = IdentitySet.new;
	}

	*new { arg eisk;
		^super.new.prInitPlugIn( eisk );
	}

	prInitPlugIn { arg argEisK;
		eisk = argEisK ?? { Eisenkraut.default };
		id	= eisk.nextUniqueID;
		all.add( this );
	}

	dispose {
		all.remove( this );
	}

	getName {
		^this.subclassResponsibility( thisMethod );
	}

	getParams {
		^this.subclassResponsibility( thisMethod );
	}

	*addPlugIns { arg eisk;
		var bndl, path;

		eisk = eisk ?? { Eisenkraut.default };

		all.do({ arg plug;
			bndl = List.new;
			bndl.add([ '/plugin', \add, plug.id, \process, plug.getName ]);
			path	= ("/plugin/id/" ++ plug.id).asSymbol;
			plug.getParams.do({ arg param;
				if( param.spec.units.notNil, {
					bndl.add([ path, \addParam, param.name, param.label, param.flags, param.spec.minval, param.spec.maxval, param.spec.warp.asSpecifier, param.spec.step, param.spec.default, param.spec.units ]);
				}, {
					bndl.add([ path, \addParam, param.name, param.label, param.flags, param.spec.minval, param.spec.maxval, param.spec.warp.asSpecifier, param.spec.step, param.spec.default ]);
				});
			});
			eisk.sendBundle( nil, *bndl );
		});
	}
}

// !!! OBSOLETE !!!
EisKPlugInParam {
	var <name, <flags, <label, <spec;

	*new { arg name, flags, label, spec;
		^super.new.prInitParam( name, flags, label, spec );
	}

	prInitParam { arg argName, argFlags, argLabel, argSpec;
		name		= argName;
		flags	= argFlags;
		label	= argLabel;
		spec		= argSpec;
	}
}

// !!! OBSOLETE !!!
// resonant low pass filter (just testin')
EisKRLPFPlugIn : EisKPlugIn {
	var params;

	prInitPlugIn {
		var result;

		result	= super.prInitPlugIn;
		params	= List.new;
		params.add( EisKPlugInParam( \freq, 0x001, "CutOff Frequency", ControlSpec(20, 20000, \exp, 0, 440, units: "Hz")));
		params.add( EisKPlugInParam( \q, 0x001, "Resonance (Q)", ControlSpec( 0.5, 1000, \exp, 0, 1, units: nil )));
		params.add( EisKPlugInParam( \gain, 0x001, "Gain", ControlSpec( -20, 20, units: "dB" )));
		^result;
	}

	getName {
		^"RLPF";
	}

	getParams {
		^params;
	}
}