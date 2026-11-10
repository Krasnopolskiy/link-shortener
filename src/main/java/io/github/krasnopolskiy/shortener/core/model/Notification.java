package io.github.krasnopolskiy.shortener.core.model;

import java.time.Instant;
import java.util.UUID;

public record Notification(UUID owner, NotificationType type, String code, String targetUrl, Instant occurredAt) {}
