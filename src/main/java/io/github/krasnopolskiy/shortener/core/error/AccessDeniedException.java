package io.github.krasnopolskiy.shortener.core.error;

public class AccessDeniedException extends ShortenerException {

    public AccessDeniedException(String code) {
        super("Link '" + code + "' belongs to another user. Only its creator can change or delete it.");
    }
}
