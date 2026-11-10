package io.github.krasnopolskiy.shortener.core.model;

import java.time.Instant;
import java.util.UUID;

public record Link(
    String code,
    UUID owner,
    String targetUrl,
    Instant createdAt,
    Instant expiresAt,
    int clickLimit,
    int clicks
) {
    public boolean isExpiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return clicks >= clickLimit;
    }

    public Link withClick() {
        return new Link(code, owner, targetUrl, createdAt, expiresAt, clickLimit, clicks + 1);
    }

    public Link withClickLimit(int newLimit) {
        return new Link(code, owner, targetUrl, createdAt, expiresAt, newLimit, clicks);
    }

    public Link withTargetUrl(String newTarget) {
        return new Link(code, owner, newTarget, createdAt, expiresAt, clickLimit, clicks);
    }
}
