/**
 *	(C)opyright 2006-2008 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	@author	Hanns Holger Rutz
 *	@version	0.11, 19-May-08
 */
EisKNRTFile {
	var <file;
	var pending, pendingSize = 16;
	
	classvar maxBundleSize	= 8192;

	*new { arg file;
		^super.new.prInit( file );
	}
	
	prInit { arg argFile;
		file		= argFile;
		pending	= List.new;
	}
	
	*openWrite { arg path;
		var f;
		
		f = File( path, "wb" );
		^this.new( f );
	}

	sendRaw { arg rawArray;
		this.prFlushMessages;
		file.write( rawArray.size );
		file.write( rawArray );
	}
	sendMsg { arg ... args;
		var raw, rawSize;
		
		raw		= args.asRawOSC;
		rawSize	= raw.size + 4;
		if( (pendingSize + rawSize) > maxBundleSize, {
			this.prFlushMessages;
		});
		pending.add( args );
		pendingSize = pendingSize + rawSize;
	}
	sendBundle { arg time ... args;
		var raw;
		this.prFlushMessages;
		raw		= ([ time ] ++ args).asRawOSC;
		file.write( raw.size );
		file.write( raw );
	}
	
	closeFile {
		this.prFlushMessages;
		file.close;
		^file;
	}
	
	prFlushMessages {
		var raw;
		if( pending.notEmpty, {
			raw	= ([ nil ] ++ pending).asRawOSC;
			file.write( raw.size );
			file.write( raw );
			pending.clear;
			pendingSize = 16;
		});
	}
}