package io.github.krasnopolskiy.shortener.core.error;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class LinkExpiredException extends ShortenerException {

    public LinkExpiredException(String code, Instant expiredAt) {
        super(
            "Link '" +
                code +
                "' is unavailable: its lifetime ended at " +
                expiredAt.truncatedTo(ChronoUnit.SECONDS) +
                " and it was deleted."
        );
    }
}
