/*
 *  DocumentHandler.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.session;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import de.sciss.app.Document;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.net.OSCRouter;
import de.sciss.eisenkraut.net.OSCRouterWrapper;
import de.sciss.eisenkraut.net.RoutedOSCMessage;

public class DocumentHandler
        extends de.sciss.app.AbstractDocumentHandler
        implements OSCRouter {

    private static final String OSC_DOC			= "doc";
    // sub level
    private static final String OSC_ACTIVE		= "active";
    private static final String OSC_INDEX		= "index";
    private static final String OSC_ID			= "id";

    // key = Integer( nodeID ) ; value = Session
    private final Map<Integer, Document> mapIDs;

    private final OSCRouterWrapper	osc;
    private final Main				root;

    public DocumentHandler(Main root) {
        super(true);    // we are multi-document aware
        this.root = root;
        this.osc = new OSCRouterWrapper(OSCRoot.getInstance(), this);
        mapIDs = new HashMap<Integer, Document>();
    }

    public void addDocument(Object source, Document doc) {
        synchronized (this.sync) {
            super.addDocument(source, doc);
            this.mapIDs.put(((Session) doc).getNodeID(), doc);
        }
    }

    public void removeDocument(Object source, Document doc) {
        synchronized (this.sync) {
            this.mapIDs.remove(((Session) doc).getNodeID());
            super.removeDocument(source, doc);
        }
    }

    // ------------- OSCRouter interface -------------

    public String oscGetPathComponent()
    {
        return OSC_DOC;
    }

    public void oscRoute( RoutedOSCMessage rom )
    {
        if( rom.hasNext() ) {	// special handling here as documents can be accessed with different paths
            oscRouteNext( rom.next() );
        } else {
            osc.oscRoute( rom );
        }
    }

    private void oscRouteNext( RoutedOSCMessage rom ) throws NumberFormatException {
        final String	subPath;
        final Document	doc;

        try {
            subPath = rom.getPathComponent();

            switch (subPath) {
                case OSC_ACTIVE:
                    doc = getActiveDocument();
                    break;
                case OSC_ID:
                    rom = rom.next();
                    final Integer id = new Integer(rom.getPathComponent());
                    synchronized (this.sync) {
                        doc = this.mapIDs.get(id);
                    }
                    break;
                case OSC_INDEX:
                    rom = rom.next();
                    final int idx = Integer.parseInt(rom.getPathComponent());
                    if (getDocumentCount() > idx) {
                        doc = getDocument(idx);
                    } else {
                        doc = null;
                    }
                    break;
                default:
                    OSCRoot.failedUnknownPath(rom);
                    return;
            }

            if( doc == null ) {
                OSCRoot.failed( rom.msg, "Document not found" );
                return;
            }

            if( !(doc instanceof OSCRouter) ) {
                OSCRoot.failed( rom.msg, "Document doesn't speak OSC" );
                return;
            }

            ((OSCRouter) doc).oscRoute( rom );
        } catch (IndexOutOfBoundsException e1) {
            OSCRoot.failedUnknownPath(rom);
        }
    }

    public void oscAddRouter(OSCRouter subRouter) {
        osc.oscAddRouter(subRouter);
    }

    public void oscRemoveRouter(OSCRouter subRouter) {
        osc.oscRemoveRouter(subRouter);
    }

    public Object oscQuery_count() {
        return getDocumentCount();
    }

    public void oscCmd_open(RoutedOSCMessage rom) {
        try {
            final String path = rom.msg.getArg(1).toString();
            root.getMenuFactory().openDocument(new File(path));
        } catch (IndexOutOfBoundsException e1) {
            OSCRoot.failedArgCount(rom);
        }
    }
}