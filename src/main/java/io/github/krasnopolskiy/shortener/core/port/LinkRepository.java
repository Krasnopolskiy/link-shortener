package io.github.krasnopolskiy.shortener.core.port;

import io.github.krasnopolskiy.shortener.core.model.Link;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {
    Optional<Link> find(String code);

    List<Link> findByOwner(UUID owner);

    List<Link> findExpired(Instant moment);

    void save(Link link);

    void delete(String code);
}
