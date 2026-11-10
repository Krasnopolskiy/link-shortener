package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.port.ExpiredLinkRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileExpiredLinkRegistry implements ExpiredLinkRegistry {

    private static final int COLUMNS = 2;

    private final TsvFile file;
    private final Map<String, Instant> expired = new LinkedHashMap<>();

    public FileExpiredLinkRegistry(Path path) {
        this.file = new TsvFile(path);
        file.read(COLUMNS, cells -> Map.entry(cells[0], Instant.parse(cells[1]))).forEach(entry ->
            expired.put(entry.getKey(), entry.getValue())
        );
    }

    @Override
    public synchronized void remember(String code, Instant expiredAt) {
        expired.put(code, expiredAt);
        flush();
    }

    @Override
    public synchronized Optional<Instant> expiredAt(String code) {
        return Optional.ofNullable(expired.get(code));
    }

    @Override
    public synchronized void forgetBefore(Instant moment) {
        if (expired.values().removeIf(expiredAt -> expiredAt.isBefore(moment))) {
            flush();
        }
    }

    private void flush() {
        file.write(
            expired
                .entrySet()
                .stream()
                .map(entry -> List.of(entry.getKey(), entry.getValue().toString()))
                .toList()
        );
    }
}
