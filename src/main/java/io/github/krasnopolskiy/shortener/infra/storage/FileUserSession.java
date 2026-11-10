package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.error.StorageException;
import io.github.krasnopolskiy.shortener.core.port.UserSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class FileUserSession implements UserSession {

    private final Path path;

    private UUID current;

    public FileUserSession(Path path) {
        this.path = path;
        this.current = load();
    }

    @Override
    public synchronized Optional<UUID> current() {
        return Optional.ofNullable(current);
    }

    @Override
    public synchronized void switchTo(UUID user) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, user + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Cannot write " + path + ": " + e.getMessage(), e);
        }
        current = user;
    }

    @Override
    public synchronized void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Cannot delete " + path + ": " + e.getMessage(), e);
        }
        current = null;
    }

    private UUID load() {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return UUID.fromString(Files.readString(path, StandardCharsets.UTF_8).strip());
        } catch (IOException | IllegalArgumentException e) {
            throw new StorageException("File " + path + " is unreadable. Fix or remove it.", e);
        }
    }
}
