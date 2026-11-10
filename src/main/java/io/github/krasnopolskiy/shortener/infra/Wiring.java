package io.github.krasnopolskiy.shortener.infra;

import io.github.krasnopolskiy.shortener.core.port.NotificationInbox;
import io.github.krasnopolskiy.shortener.core.port.UserSession;
import io.github.krasnopolskiy.shortener.core.service.LinkPolicy;
import io.github.krasnopolskiy.shortener.core.service.LinkService;
import io.github.krasnopolskiy.shortener.core.service.UrlValidator;
import io.github.krasnopolskiy.shortener.infra.storage.FileExpiredLinkRegistry;
import io.github.krasnopolskiy.shortener.infra.storage.FileLinkRepository;
import io.github.krasnopolskiy.shortener.infra.storage.FileNotificationInbox;
import io.github.krasnopolskiy.shortener.infra.storage.FileUserSession;
import java.nio.file.Path;
import java.time.Clock;

public record Wiring(LinkService service, UserSession session, NotificationInbox inbox) {
    public static Wiring create(AppConfig config, Clock clock) {
        Path dir = config.storageDir();
        NotificationInbox inbox = new FileNotificationInbox(dir.resolve("notifications.tsv"));
        LinkPolicy policy = new LinkPolicy(
            config.linkTtl(),
            config.defaultClickLimit(),
            config.maxClickLimit(),
            config.tombstoneRetention()
        );
        LinkService service = new LinkService(
            new FileLinkRepository(dir.resolve("links.tsv")),
            new FileExpiredLinkRegistry(dir.resolve("expired.tsv")),
            inbox,
            new Base62CodeGenerator(config.codeLength()),
            new UrlValidator(config.maxUrlLength(), config.shortDomain()),
            policy,
            clock
        );
        return new Wiring(service, new FileUserSession(dir.resolve("current-user")), inbox);
    }
}
