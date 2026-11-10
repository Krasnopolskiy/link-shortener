package io.github.krasnopolskiy.shortener.infra.storage;

import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.model.NotificationType;
import io.github.krasnopolskiy.shortener.core.port.NotificationInbox;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileNotificationInbox implements NotificationInbox {

    private static final int COLUMNS = 5;

    private final TsvFile file;
    private final List<Notification> pending;

    public FileNotificationInbox(Path path) {
        this.file = new TsvFile(path);
        this.pending = new ArrayList<>(file.read(COLUMNS, FileNotificationInbox::fromRow));
    }

    @Override
    public synchronized void publish(Notification notification) {
        pending.add(notification);
        flush();
    }

    @Override
    public synchronized List<Notification> drain(UUID owner) {
        List<Notification> own = pending
            .stream()
            .filter(notification -> notification.owner().equals(owner))
            .toList();
        if (!own.isEmpty()) {
            pending.removeAll(own);
            flush();
        }
        return own;
    }

    private void flush() {
        file.write(pending.stream().map(FileNotificationInbox::toRow).toList());
    }

    private static List<String> toRow(Notification notification) {
        return List.of(
            notification.owner().toString(),
            notification.type().name(),
            notification.code(),
            notification.targetUrl(),
            notification.occurredAt().toString()
        );
    }

    private static Notification fromRow(String[] cells) {
        return new Notification(
            UUID.fromString(cells[0]),
            NotificationType.valueOf(cells[1]),
            cells[2],
            cells[3],
            Instant.parse(cells[4])
        );
    }
}
