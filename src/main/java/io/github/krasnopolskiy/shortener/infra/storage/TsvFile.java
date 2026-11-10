package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.error.StorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

class TsvFile {

    private static final String SEPARATOR = "\t";

    private final Path path;

    TsvFile(Path path) {
        this.path = path;
    }

    <T> List<T> read(int columns, Function<String[], T> rowMapper) {
        List<String> lines = readLines();
        List<T> rows = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            rows.add(parseRow(lines.get(i), i + 1, columns, rowMapper));
        }
        return rows;
    }

    void write(Collection<List<String>> rows) {
        List<String> lines = rows
            .stream()
            .map(row -> String.join(SEPARATOR, row))
            .toList();
        try {
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            move(temporary);
        } catch (IOException e) {
            throw new StorageException("Cannot write " + path + ": " + e.getMessage(), e);
        }
    }

    private List<String> readLines() {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    private <T> T parseRow(String line, int number, int columns, Function<String[], T> rowMapper) {
        String[] cells = line.split(SEPARATOR, -1);
        if (cells.length != columns) {
            throw corrupted(number, null);
        }
        try {
            return rowMapper.apply(cells);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw corrupted(number, e);
        }
    }

    private StorageException corrupted(int line, Exception cause) {
        return new StorageException("File " + path + " is corrupted at line " + line + ". Fix or remove it.", cause);
    }

    private void move(Path temporary) throws IOException {
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
