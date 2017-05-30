/*
 *  OSCRouterWrapper.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */
 
package de.sciss.eisenkraut.net;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class OSCRouterWrapper
		implements OSCRouter {

	private final OSCRouter	r;
	private final OSCRouter	superRouter;
	private final Map<String, OSCRouter> mapSubRouters = new HashMap<String, OSCRouter>();
	
	private static final Class[] oscCmdMethodTypes		= { RoutedOSCMessage.class };
	private static final Class[] oscGetMethodTypes		= { RoutedOSCMessage.class };

	public OSCRouterWrapper(OSCRouter superRouter, OSCRouter thisRouter) {
		r = thisRouter;
		this.superRouter = superRouter;
		if (superRouter != null) superRouter.oscAddRouter(r);
	}

	public void remove() {
		if (superRouter != null) superRouter.oscRemoveRouter(r);
	}

	public void oscRoute(RoutedOSCMessage rom) {
		final int argCnt = rom.msg.getArgCount();
	
		if( rom.hasNext() ) {
			rom = rom.next();
			final OSCRouter nextR = mapSubRouters.get(rom.getPathComponent());
			if (nextR != null) {
				nextR.oscRoute(rom);
			} else {
				OSCRoot.failedUnknownPath(rom);
			}
		} else {
			final Object[]	replyArgs;
			final String	cmd;
			Method			oscMethod;

			if (argCnt == 0) {
				OSCRoot.failedArgCount(rom);
				return;
			}
			try {
				cmd = rom.msg.getArg(0).toString();
				if (cmd.equals(OSCRoot.OSC_QUERY)) {
					if (argCnt < 2) {
						OSCRoot.failedArgCount(rom);
						return;
					}
					replyArgs = new Object[argCnt - 1];
					replyArgs[0] = rom.msg.getArg(1);
					int argIdx = 2;
					try {
						for (; argIdx < argCnt; argIdx++) {
							oscMethod = r.getClass().getMethod("oscQuery_" + rom.msg.getArg(argIdx).toString(), (Class[]) null);
							replyArgs[argIdx - 1] = oscMethod.invoke(r, (Object[]) null);
						}
					} catch (NoSuchMethodException e1) {
						OSCRoot.failedArgValue(rom, argIdx);
						return;
					}
					rom.reply(OSCRoot.OSC_QUERYREPLY, replyArgs);

				} else if (cmd.equals(OSCRoot.OSC_GET)) {

					if (argCnt < 3) {
						OSCRoot.failedArgCount(rom);
						return;
					}
					final Object[] methodResult;
					try {
						oscMethod = r.getClass().getMethod("oscGet_" + rom.msg.getArg(2), oscGetMethodTypes);
						methodResult = (Object[]) oscMethod.invoke(r, rom);
						if (methodResult != null) {
							replyArgs = new Object[methodResult.length + 1];
							replyArgs[0] = rom.msg.getArg(1);
							System.arraycopy(methodResult, 0, replyArgs, 1, methodResult.length);
							rom.reply(OSCRoot.OSC_GETREPLY, replyArgs);
						}
					} catch (NoSuchMethodException e1) {
						OSCRoot.failedArgValue(rom, 2);
					}
					
				} else {	// any other command
					try {
						oscMethod = r.getClass().getMethod("oscCmd_" + cmd, oscCmdMethodTypes);
						oscMethod.invoke(r, rom);
					} catch (NoSuchMethodException e1) {
						OSCRoot.failedUnknownCmd(rom);
					}
				}
			} catch (SecurityException e1) {
				OSCRoot.failed(rom, e1);
			} catch (IllegalAccessException e1) {
				OSCRoot.failed(rom, e1);
			} catch (IllegalArgumentException e1) {
				OSCRoot.failed(rom, e1);
			} catch (InvocationTargetException e1) {
				OSCRoot.failed(rom, e1);
			} catch (ExceptionInInitializerError e1) {
				OSCRoot.failed(rom, e1);
			} catch (IOException e1) {
				OSCRoot.failed(rom, e1);
			}
		}
	}

	public void oscAddRouter(OSCRouter subRouter) {
		if (mapSubRouters.put(subRouter.oscGetPathComponent(), subRouter) != null) {
			throw new IllegalArgumentException("Tried to overwrite existing router for sub path " + subRouter.oscGetPathComponent());
		}
	}

	public void oscRemoveRouter(OSCRouter subRouter) {
		if (mapSubRouters.remove(subRouter.oscGetPathComponent()) == null) {
			throw new IllegalArgumentException("Tried to remove unknown router for sub path " + subRouter.oscGetPathComponent());
		}
	}
	
	public String oscGetPathComponent()
	{
		return r.oscGetPathComponent();
	}
}