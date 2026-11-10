package io.github.krasnopolskiy.shortener.core.error;

public class StorageException extends ShortenerException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
