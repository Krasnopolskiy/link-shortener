package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.port.LinkRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FileLinkRepository implements LinkRepository {

    private static final int COLUMNS = 7;

    private final TsvFile file;
    private final Map<String, Link> links = new LinkedHashMap<>();

    public FileLinkRepository(Path path) {
        this.file = new TsvFile(path);
        file.read(COLUMNS, FileLinkRepository::fromRow).forEach(link -> links.put(link.code(), link));
    }

    @Override
    public synchronized Optional<Link> find(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public synchronized List<Link> findByOwner(UUID owner) {
        return links
            .values()
            .stream()
            .filter(link -> link.owner().equals(owner))
            .toList();
    }

    @Override
    public synchronized List<Link> findExpired(Instant moment) {
        return links
            .values()
            .stream()
            .filter(link -> link.isExpiredAt(moment))
            .toList();
    }

    @Override
    public synchronized void save(Link link) {
        links.put(link.code(), link);
        flush();
    }

    @Override
    public synchronized void delete(String code) {
        if (links.remove(code) != null) {
            flush();
        }
    }

    private void flush() {
        file.write(links.values().stream().map(FileLinkRepository::toRow).toList());
    }

    private static List<String> toRow(Link link) {
        return List.of(
            link.code(),
            link.owner().toString(),
            link.targetUrl(),
            link.createdAt().toString(),
            link.expiresAt().toString(),
            Integer.toString(link.clickLimit()),
            Integer.toString(link.clicks())
        );
    }

    private static Link fromRow(String[] cells) {
        return new Link(
            cells[0],
            UUID.fromString(cells[1]),
            cells[2],
            Instant.parse(cells[3]),
            Instant.parse(cells[4]),
            Integer.parseInt(cells[5]),
            Integer.parseInt(cells[6])
        );
    }
}
