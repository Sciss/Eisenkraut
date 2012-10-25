/**
 *	@version	0.70, 30-Mar-07
 *	@author	Hanns Holger Rutz
 */
EisKSynthServer : Server {
	var <eisK;

	*new { arg name, addr, options, clientID = 1, eisK;
		var server;
		server = super.new( name, addr, options, clientID );
		named.removeAt( server.name );
		set.remove( server );
		server.prSetEisK( eisK ); // sucky this.eisK don't work
		^server;
	}
	
	prSetEisK { arg argEisK;
		eisK = argEisK;
	}
	
	quit {
		"EisKSynthServer.quit".warn;
		^super.quit;
	}
}