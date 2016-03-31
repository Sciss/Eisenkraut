/*
 *  RoutedOSCMessage.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import java.io.IOException;
import java.net.SocketAddress;

import de.sciss.app.BasicEvent;
import de.sciss.net.OSCMessage;

@SuppressWarnings("serial")
public class RoutedOSCMessage extends BasicEvent {
	public final OSCMessage			msg;
	public final SocketAddress		addr;
	public final long				when;
	public final OSCRoot			server;
	
	private final String[]			path;
	private final int				pathIdx;

	public RoutedOSCMessage(OSCMessage msg, SocketAddress addr, long when, OSCRoot server, String[] path, int pathIdx) {
		super(addr, 0, when);
		this.msg		= msg;
		this.addr		= addr;
		this.when		= when;
		this.server		= server;
		this.path		= path;
		this.pathIdx	= pathIdx;
	}

	public boolean incorporate(BasicEvent oldEvent) {
		return false;
	}

	public int getPathIndex() {
		return pathIdx;
	}

	public int getPathCount() {
		return path.length;
	}

	public String getPathComponent(int idx) {
		return path[idx];
	}

	public String getPathComponent() {
		return path[pathIdx];
	}

	public String getNextPathComponent() {
		return getNextPathComponent(1);
	}

	public String getNextPathComponent(int skip) {
		return path[pathIdx + skip];
	}

	public boolean hasNext() {
		return (hasNext(1));
	}

	public boolean hasNext(int numComponents) {
		return (pathIdx + numComponents < path.length);
	}

	public RoutedOSCMessage next() {
		return next(1);
	}

	public RoutedOSCMessage next(int skip) {
		return new RoutedOSCMessage(msg, addr, when, server, path, pathIdx + skip);
	}

	public void reply(String cmd, Object[] args)
			throws IOException {
		server.send(new OSCMessage(cmd, args), addr);
	}

	public void replyFailed()
			throws IOException {
		replyFailed(0);
	}

	public void replyFailed(int argCount)
			throws IOException {
		final Object[] args = new Object[argCount + 1];
		args[0] = msg.getName();
		for (int i = 0; i < argCount; i++) {
			args[i + 1] = msg.getArg(i);
		}
		server.send(new OSCMessage(OSCRoot.OSC_FAILEDREPLY, args), addr);
	}

	public void replyDone(int copyArgCount, Object[] doneArgs)
			throws IOException {
		final Object[] args = new Object[copyArgCount + doneArgs.length + 1];
		int j = 0;
		args[j++] = msg.getName();
		for (int i = 0; i < copyArgCount; i++) {
			args[j++] = msg.getArg(i);
		}
		for (Object doneArg : doneArgs) {
			args[j++] = doneArg;
		}
		server.send(new OSCMessage(OSCRoot.OSC_DONEREPLY, args), addr);
	}
}