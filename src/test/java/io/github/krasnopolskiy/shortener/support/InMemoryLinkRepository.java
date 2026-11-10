package io.github.krasnopolskiy.shortener.support;

import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.port.LinkRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> links = new LinkedHashMap<>();

    @Override
    public Optional<Link> find(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public List<Link> findByOwner(UUID owner) {
        return links
            .values()
            .stream()
            .filter(link -> link.owner().equals(owner))
            .toList();
    }

    @Override
    public List<Link> findExpired(Instant moment) {
        return links
            .values()
            .stream()
            .filter(link -> link.isExpiredAt(moment))
            .toList();
    }

    @Override
    public void save(Link link) {
        links.put(link.code(), link);
    }

    @Override
    public void delete(String code) {
        links.remove(code);
    }
}
