package io.github.krasnopolskiy.shortener.core.service;

import io.github.krasnopolskiy.shortener.core.error.AccessDeniedException;
import io.github.krasnopolskiy.shortener.core.error.LinkExhaustedException;
import io.github.krasnopolskiy.shortener.core.error.LinkExpiredException;
import io.github.krasnopolskiy.shortener.core.error.LinkNotFoundException;
import io.github.krasnopolskiy.shortener.core.error.ShortenerException;
import io.github.krasnopolskiy.shortener.core.error.StorageException;
import io.github.krasnopolskiy.shortener.core.error.ValidationException;
import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.model.NotificationType;
import io.github.krasnopolskiy.shortener.core.port.CodeGenerator;
import io.github.krasnopolskiy.shortener.core.port.ExpiredLinkRegistry;
import io.github.krasnopolskiy.shortener.core.port.LinkRepository;
import io.github.krasnopolskiy.shortener.core.port.NotificationInbox;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 32;
    private static final int MIN_CLICK_LIMIT = 1;

    private final LinkRepository links;
    private final ExpiredLinkRegistry expiredLinks;
    private final NotificationInbox inbox;
    private final CodeGenerator codes;
    private final UrlValidator urlValidator;
    private final LinkPolicy policy;
    private final Clock clock;

    public LinkService(
        LinkRepository links,
        ExpiredLinkRegistry expiredLinks,
        NotificationInbox inbox,
        CodeGenerator codes,
        UrlValidator urlValidator,
        LinkPolicy policy,
        Clock clock
    ) {
        this.links = links;
        this.expiredLinks = expiredLinks;
        this.inbox = inbox;
        this.codes = codes;
        this.urlValidator = urlValidator;
        this.policy = policy;
        this.clock = clock;
    }

    public synchronized Link create(UUID owner, String url) {
        return create(owner, url, policy.defaultClickLimit());
    }

    public synchronized Link create(UUID owner, String url, int clickLimit) {
        String target = urlValidator.normalize(url);
        checkLimitRange(clickLimit);
        Instant now = clock.instant();
        Link link = new Link(uniqueCode(), owner, target, now, now.plus(policy.ttl()), clickLimit, 0);
        links.save(link);
        return link;
    }

    public synchronized Link follow(String code) {
        Link link = requireAvailable(code);
        if (link.isExhausted()) {
            throw new LinkExhaustedException(code, link.clickLimit());
        }
        Link clicked = link.withClick();
        links.save(clicked);
        if (clicked.isExhausted()) {
            notifyOwner(clicked, NotificationType.LIMIT_EXHAUSTED, clock.instant());
        }
        return clicked;
    }

    public synchronized List<Link> list(UUID owner) {
        removeExpired();
        return links.findByOwner(owner);
    }

    public synchronized Link updateClickLimit(UUID user, String code, int newLimit) {
        Link link = requireOwned(user, code);
        checkLimitRange(newLimit);
        if (newLimit <= link.clicks()) {
            throw new ValidationException(
                "New limit must be greater than the " + link.clicks() + " clicks already made."
            );
        }
        Link updated = link.withClickLimit(newLimit);
        links.save(updated);
        return updated;
    }

    public synchronized Link updateTargetUrl(UUID user, String code, String url) {
        Link updated = requireOwned(user, code).withTargetUrl(urlValidator.normalize(url));
        links.save(updated);
        return updated;
    }

    public synchronized void delete(UUID user, String code) {
        requireOwned(user, code);
        links.delete(code);
    }

    public synchronized int removeExpired() {
        Instant now = clock.instant();
        List<Link> expired = links.findExpired(now);
        expired.forEach(this::expire);
        expiredLinks.forgetBefore(now.minus(policy.tombstoneRetention()));
        return expired.size();
    }

    private Link requireAvailable(String code) {
        Link link = links.find(code).orElseThrow(() -> missingLinkError(code));
        if (link.isExpiredAt(clock.instant())) {
            expire(link);
            throw new LinkExpiredException(code, link.expiresAt());
        }
        return link;
    }

    private ShortenerException missingLinkError(String code) {
        Optional<Instant> expiredAt = expiredLinks.expiredAt(code);
        if (expiredAt.isPresent()) {
            return new LinkExpiredException(code, expiredAt.get());
        }
        return new LinkNotFoundException(code);
    }

    private Link requireOwned(UUID user, String code) {
        Link link = requireAvailable(code);
        if (!link.owner().equals(user)) {
            throw new AccessDeniedException(code);
        }
        return link;
    }

    private void expire(Link link) {
        links.delete(link.code());
        expiredLinks.remember(link.code(), link.expiresAt());
        notifyOwner(link, NotificationType.EXPIRED, link.expiresAt());
    }

    private void notifyOwner(Link link, NotificationType type, Instant occurredAt) {
        inbox.publish(new Notification(link.owner(), type, link.code(), link.targetUrl(), occurredAt));
    }

    private void checkLimitRange(int clickLimit) {
        if (clickLimit < MIN_CLICK_LIMIT || clickLimit > policy.maxClickLimit()) {
            throw new ValidationException(
                "Click limit must be between " +
                    MIN_CLICK_LIMIT +
                    " and " +
                    policy.maxClickLimit() +
                    ", got " +
                    clickLimit +
                    "."
            );
        }
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.next();
            if (links.find(code).isEmpty() && expiredLinks.expiredAt(code).isEmpty()) {
                return code;
            }
        }
        throw new StorageException(
            "Could not find a free short code. Increase short.code-length in the configuration."
        );
    }
}
