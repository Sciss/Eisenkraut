/*
 *  NRTFile.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import de.sciss.net.OSCBundle;
import de.sciss.net.OSCChannel;
import de.sciss.net.OSCMessage;
import de.sciss.net.OSCPacket;

public class NRTFile {
    private final RandomAccessFile	raf;
    private final FileChannel		fch;
    private double					time;
//	private final List				pending	= new ArrayList();
    private final ByteBuffer		b;
    private OSCBundle				pending	= null;

    private NRTFile( File f, String mode )
    throws IOException
    {
        raf		= new RandomAccessFile( f, mode );
        fch		= raf.getChannel();
        b		= ByteBuffer.allocate( OSCChannel.DEFAULTBUFSIZE );
        time	= 0.0;
        pending	= new OSCBundle( time );
    }

    public static NRTFile openAsWrite( File f )
    throws IOException
    {
        return new NRTFile( f, "rw" );
    }

    public void close()
    throws IOException
    {
        if( pending.getPacketCount() == 0 ) {
            pending.addPacket( new OSCMessage( "/status" ));	// properly mark ending of rendering
        }
        flush();
//		fch.close();
        raf.close();
    }

    public void dispose()
    {
        try {
//			fch.close();
            raf.close();
        }
        catch( IOException e1 ) { /* ignore */ }
    }

    public void write( OSCPacket p )
    throws IOException
    {
        if( p instanceof OSCBundle ) {
            final OSCBundle bndl = (OSCBundle) p;
            if( bndl.getTimeTag() != pending.getTimeTag() ) {
                throw new IllegalArgumentException( "Bundles must have timetag corresponding to current time" );
            }
            flush();
            flush( bndl );
        } else if( p instanceof OSCMessage ) {
            pending.addPacket( p );
        } else {
            throw new IllegalArgumentException( p.getClass().getName() );
        }
    }

    public void flush()
    throws IOException
    {
        if( pending.getPacketCount() == 0 ) return;

        flush( pending );
    }

    private void flush( OSCBundle bndl )
    throws IOException
    {
        final int pos2;

        b.clear();
        b.putInt( 0 );			// calculate size later
        bndl.encode( b );
        pos2 = b.position();
        b.position( 0 );
        b.putInt( pos2 - 4 ).position( pos2 );
        b.flip();
        fch.write( b );
    }

    public void addTime( double seconds )
    throws IOException
    {
        if( seconds < 0.0 ) throw new IllegalArgumentException( "Cannot step back in time" );
        flush();
        time   += seconds;
        pending	= new OSCBundle( time );
    }

    public void setTime( double seconds )
    throws IOException
    {
        if( time > seconds ) throw new IllegalArgumentException( "Cannot step back in time" );
        flush();
        time	= seconds;
        pending	= new OSCBundle( time );
    }

    public double getTime()
    {
        return time;
    }
}
