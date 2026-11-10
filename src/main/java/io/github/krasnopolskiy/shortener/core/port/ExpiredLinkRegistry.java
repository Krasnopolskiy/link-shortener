package io.github.krasnopolskiy.shortener.core.port;

import java.time.Instant;
import java.util.Optional;

public interface ExpiredLinkRegistry {
    void remember(String code, Instant expiredAt);

    Optional<Instant> expiredAt(String code);

    void forgetBefore(Instant moment);
}
