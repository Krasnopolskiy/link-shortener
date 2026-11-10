package io.github.krasnopolskiy.shortener.infra;

import io.github.krasnopolskiy.shortener.core.port.Browser;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public class DesktopBrowser implements Browser {

    @Override
    public boolean open(URI uri) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return false;
            }
            Desktop.getDesktop().browse(uri);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return false;
        }
    }
}
