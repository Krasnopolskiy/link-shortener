package io.github.krasnopolskiy.shortener.core.service;

import java.time.Duration;

public record LinkPolicy(Duration ttl, int defaultClickLimit, int maxClickLimit, Duration tombstoneRetention) {}
