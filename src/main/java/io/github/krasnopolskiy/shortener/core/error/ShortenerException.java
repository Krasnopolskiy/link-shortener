package io.github.krasnopolskiy.shortener.core.error;

public abstract class ShortenerException extends RuntimeException {

    protected ShortenerException(String message) {
        super(message);
    }

    protected ShortenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
