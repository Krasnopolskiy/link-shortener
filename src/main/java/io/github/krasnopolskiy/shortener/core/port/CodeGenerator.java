package io.github.krasnopolskiy.shortener.core.port;

@FunctionalInterface
public interface CodeGenerator {
    String next();
}
