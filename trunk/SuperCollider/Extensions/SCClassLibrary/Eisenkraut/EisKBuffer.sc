/**
 *	@version	0.70, 30-Mar-07
 *	@author	Hanns Holger Rutz
 */
EisKBuffer : Buffer {
	*new { arg server, numFrames, numChannels, bufnum;
		if( bufnum.isNil, {
			Error( "EisKBuffer.new cannot be called without an explicit bufnum" ).throw;
		}, {
			^super.new( server, numFrames, numChannels, bufnum );
		});
	}
	
	*alloc { arg server, numFrames, numChannels = 1 ... rest;
		var msg;
		if( rest.size > 0, {
			"EisKBuffer.alloc : extra arguments omitted!".warn;
		});
		server = server ?? { Eisenkraut.default.scsynth };
//		msg = eisK.prQuery( '/sc', \allocBuf, '/done', properties, timeout, condition );
		msg = server.eisK.sendMsgSync( '/sc', \allocBuf, [ numFrames, numChannels ]);
		if( msg.notNil, {
//			msg.postln;
			^this.new( server, numFrames, numChannels, msg.first ).sampleRate_( server.sampleRate );
		}, {
			"EisKBuffer.alloc : timeout!".error;
			^nil;
		});
	}

	*allocConsecutive {
		^this.notYetImplemented;
	}

	*read {
		^this.notYetImplemented;
	}

	*readChannel {
		^this.notYetImplemented;
	}
	
	*readNoUpdate {
		^this.notYetImplemented;
	}

	*loadCollection {
		^this.notYetImplemented;
	}
	
	*sendCollection {
		^this.notYetImplemented;
	}
	
	freeMsg { arg completionMessage;
		server.freeBuf(bufnum);
//		server.bufferAllocator.free(bufnum);
		^["/b_free", bufnum, completionMessage.value(this)];
	}

	*loadDialog {
		^this.notYetImplemented;
	}
}