package io.github.krasnopolskiy.shortener.infra.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.krasnopolskiy.shortener.core.error.StorageException;
import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.model.NotificationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @TempDir
    Path dir;

    private final UUID owner = UUID.randomUUID();

    private Link link(String code) {
        return new Link(code, owner, "https://example.com/?a=1", NOW, NOW.plusSeconds(60), 5, 2);
    }

    @Test
    void linksSurviveReload() {
        Path file = dir.resolve("links.tsv");
        FileLinkRepository first = new FileLinkRepository(file);
        first.save(link("kept"));
        first.save(link("gone"));
        first.delete("gone");
        FileLinkRepository reloaded = new FileLinkRepository(file);
        assertEquals(Optional.of(link("kept")), reloaded.find("kept"));
        assertTrue(reloaded.find("gone").isEmpty());
    }

    @Test
    void findExpiredSelectsByTime() {
        FileLinkRepository repository = new FileLinkRepository(dir.resolve("links.tsv"));
        repository.save(link("abc"));
        assertTrue(repository.findExpired(NOW.plusSeconds(59)).isEmpty());
        assertEquals(1, repository.findExpired(NOW.plusSeconds(60)).size());
    }

    @Test
    void corruptedFileIsReportedAndLeftUntouched() throws IOException {
        Path file = dir.resolve("links.tsv");
        Files.writeString(file, "only\ttwo\n");
        StorageException error = assertThrows(StorageException.class, () -> new FileLinkRepository(file));
        assertTrue(error.getMessage().contains("line 1"));
        assertEquals("only\ttwo\n", Files.readString(file));
    }

    @Test
    void badValueInRowIsReported() throws IOException {
        Path file = dir.resolve("links.tsv");
        Files.writeString(file, "code\tnot-a-uuid\thttps://a.b\t2026\t2026\t1\t0\n");
        assertThrows(StorageException.class, () -> new FileLinkRepository(file));
    }

    @Test
    void notificationsArePerOwnerSurviveReloadAndDrainOnce() {
        Path file = dir.resolve("notifications.tsv");
        UUID other = UUID.randomUUID();
        Notification own = new Notification(owner, NotificationType.EXPIRED, "abc", "https://a.b", NOW);
        FileNotificationInbox first = new FileNotificationInbox(file);
        first.publish(own);
        first.publish(new Notification(other, NotificationType.LIMIT_EXHAUSTED, "xyz", "https://c.d", NOW));

        FileNotificationInbox reloaded = new FileNotificationInbox(file);
        assertEquals(List.of(own), reloaded.drain(owner));
        assertTrue(reloaded.drain(owner).isEmpty());
        assertEquals(1, new FileNotificationInbox(file).drain(other).size());
    }

    @Test
    void expiredRegistrySurvivesReloadAndForgets() {
        Path file = dir.resolve("expired.tsv");
        new FileExpiredLinkRegistry(file).remember("abc", NOW);
        FileExpiredLinkRegistry reloaded = new FileExpiredLinkRegistry(file);
        assertEquals(Optional.of(NOW), reloaded.expiredAt("abc"));
        reloaded.forgetBefore(NOW.plusSeconds(1));
        assertTrue(new FileExpiredLinkRegistry(file).expiredAt("abc").isEmpty());
    }

    @Test
    void sessionRemembersUserUntilCleared() {
        Path file = dir.resolve("current-user");
        UUID user = UUID.randomUUID();
        FileUserSession session = new FileUserSession(file);
        assertTrue(session.current().isEmpty());
        session.switchTo(user);
        assertEquals(Optional.of(user), new FileUserSession(file).current());
        session.clear();
        assertTrue(new FileUserSession(file).current().isEmpty());
    }

    @Test
    void unreadableSessionIsReported() throws IOException {
        Path file = Files.writeString(dir.resolve("current-user"), "garbage");
        assertThrows(StorageException.class, () -> new FileUserSession(file));
    }

    @Test
    void lockRefusesSecondHolderUntilReleased() throws IOException {
        StorageLock lock = StorageLock.acquire(dir);
        assertThrows(StorageException.class, () -> StorageLock.acquire(dir));
        lock.close();
        StorageLock.acquire(dir).close();
    }
}
