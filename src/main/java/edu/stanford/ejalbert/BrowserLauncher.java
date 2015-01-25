package edu.stanford.ejalbert;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

// Simple contemporary replacement for the Eric Albert's class
// that is hard wired somewhere in MRJAdapter
public class BrowserLauncher {

    private BrowserLauncher() { /* empty */ }

    public static void openURL(String url) throws IOException {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }

    protected static void dummy() {}
}
