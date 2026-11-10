package io.github.krasnopolskiy.shortener.support;

import io.github.krasnopolskiy.shortener.core.port.ExpiredLinkRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryExpiredLinkRegistry implements ExpiredLinkRegistry {

    private final Map<String, Instant> expired = new HashMap<>();

    @Override
    public void remember(String code, Instant expiredAt) {
        expired.put(code, expiredAt);
    }

    @Override
    public Optional<Instant> expiredAt(String code) {
        return Optional.ofNullable(expired.get(code));
    }

    @Override
    public void forgetBefore(Instant moment) {
        expired.values().removeIf(expiredAt -> expiredAt.isBefore(moment));
    }
}
