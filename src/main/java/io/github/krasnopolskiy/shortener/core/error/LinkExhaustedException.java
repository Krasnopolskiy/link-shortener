package io.github.krasnopolskiy.shortener.core.error;

public class LinkExhaustedException extends ShortenerException {

    public LinkExhaustedException(String code, int clickLimit) {
        super(
            "Link '" +
                code +
                "' is unavailable: the click limit is used up (" +
                clickLimit +
                "/" +
                clickLimit +
                "). Its owner can raise the limit."
        );
    }
}
