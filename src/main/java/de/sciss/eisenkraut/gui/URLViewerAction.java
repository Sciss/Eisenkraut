/*
 *  URLViewerAction.java
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

package de.sciss.eisenkraut.gui;

import de.sciss.common.BasicWindowHandler;
import de.sciss.gui.HelpFrame;
import de.sciss.gui.MenuAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.net.URL;

/**
 *  Generic action for bringing up
 *  a html document either in the
 *  help viewer or the default web browser
 */
public class URLViewerAction extends MenuAction {

    private final URL theURL;
    private final boolean openWebBrowser;

    /**
     * @param   theURL          what file to open ; when using the
     *                          help viewer, that's the relative help file name
     *                          without .html extension. when using web browser,
     *                          that's the complete URL!
     * @param   text  ...
     * @param   shortcut  ...
     * @param   openWebBrowser  if true, use the default web browser,
     *                          if false use internal help viewer
     */
    public URLViewerAction(String text, KeyStroke shortcut, URL theURL, boolean openWebBrowser) {
        super(text, shortcut);

        this.theURL = theURL;
        this.openWebBrowser = openWebBrowser;
    }

    /**
     *  Tries to find the component using
     *  the <code>Main</code> class' <code>getComponent</code>
     *  method. It does not instantiate a
     *  new object if the component is not found.
     *  If the window is already open, this
     *  method will bring it to the front.
     */
    public void actionPerformed(ActionEvent e) {
        if (openWebBrowser) {
            try {
                final URI uri = theURL.toURI();
                Desktop.getDesktop().browse(uri);
            } catch (Exception e1) {
                BasicWindowHandler.showErrorDialog(null, e1, NAME);
            }
        } else {
            HelpFrame.openViewerAndLoadHelpFile(theURL);
        }
    }
}
