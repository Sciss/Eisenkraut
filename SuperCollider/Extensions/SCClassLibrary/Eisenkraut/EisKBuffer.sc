/**
 *	@version	0.71, 18-Jun-09
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
	
	*alloc { arg server, numFrames, numChannels = 1, completionMessage, bufnum;
		var msg;
		server = server ?? { Eisenkraut.default.scsynth };
		if( bufnum.isNil, {
			msg = server.eisK.sendMsgSync( '/sc', \allocBuf, [ numFrames, numChannels ]);
			if( msg.notNil, {
				bufnum = msg.first;
			}, {
				"EisKBuffer.alloc : timeout!".error;
				^nil;
			});
		});
		^super.newCopyArgs(server,
						bufnum,
						numFrames,
						numChannels)
					.alloc(completionMessage).sampleRate_(server.sampleRate).cache;
	}

//	*cueSoundFile { arg server,path,startFrame = 0,numChannels= 2,
//			 bufferSize=32768,completionMessage;
//		^this.alloc(server,bufferSize,numChannels,{ arg buffer;
//						buffer.readMsg(path,startFrame,bufferSize,0,true,completionMessage)
//					}).cache;
//	}

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
	
	free { arg completionMessage;
		server.eisK.sendMsg( '/sc', \freeBuf, bufnum );
	}
	
//	freeMsg { arg completionMessage;
////		server.eisK.sendMsgSync( '/sc', \freeBuf, bufnum );
//		server.eisK.sendMsg( '/sc', \freeBuf, bufnum );
//		^super.freeMsg;
//	}

	*loadDialog {
		^this.notYetImplemented;
	}
}