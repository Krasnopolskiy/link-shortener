package io.github.krasnopolskiy.shortener.core.port;

import java.net.URI;

@FunctionalInterface
public interface Browser {
    boolean open(URI uri);
}
