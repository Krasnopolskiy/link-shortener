package io.github.krasnopolskiy.shortener.core.error;

public class LinkNotFoundException extends ShortenerException {

    public LinkNotFoundException(String code) {
        super("Link '" + code + "' does not exist. Check the spelling or run 'list' to see your links.");
    }
}
