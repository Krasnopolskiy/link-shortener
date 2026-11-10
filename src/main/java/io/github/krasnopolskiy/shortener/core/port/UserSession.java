package io.github.krasnopolskiy.shortener.core.port;

import java.util.Optional;
import java.util.UUID;

public interface UserSession {
    Optional<UUID> current();

    void switchTo(UUID user);

    void clear();
}
