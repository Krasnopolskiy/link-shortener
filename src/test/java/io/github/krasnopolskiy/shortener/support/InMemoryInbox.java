package io.github.krasnopolskiy.shortener.support;

import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.port.NotificationInbox;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryInbox implements NotificationInbox {

    private final List<Notification> pending = new ArrayList<>();

    @Override
    public void publish(Notification notification) {
        pending.add(notification);
    }

    @Override
    public List<Notification> drain(UUID owner) {
        List<Notification> own = pending
            .stream()
            .filter(n -> n.owner().equals(owner))
            .toList();
        pending.removeAll(own);
        return own;
    }
}
