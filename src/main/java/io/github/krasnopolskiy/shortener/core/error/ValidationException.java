package io.github.krasnopolskiy.shortener.core.error;

public class ValidationException extends ShortenerException {

    public ValidationException(String message) {
        super(message);
    }
}
