package io.github.krasnopolskiy.shortener.core.port;

import io.github.krasnopolskiy.shortener.core.model.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationInbox {
    void publish(Notification notification);

    List<Notification> drain(UUID owner);
}
