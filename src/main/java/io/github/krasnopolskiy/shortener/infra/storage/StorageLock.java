package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.error.StorageException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class StorageLock implements AutoCloseable {

    private final FileChannel channel;

    private StorageLock(FileChannel channel) {
        this.channel = channel;
    }

    public static StorageLock acquire(Path directory) {
        try {
            Files.createDirectories(directory);
            FileChannel channel = FileChannel.open(
                directory.resolve(".lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            FileLock lock = tryLock(channel);
            if (lock == null) {
                channel.close();
                throw new StorageException("Another instance is already using " + directory + ". Close it first.");
            }
            return new StorageLock(channel);
        } catch (IOException e) {
            throw new StorageException("Cannot lock " + directory + ": " + e.getMessage(), e);
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
