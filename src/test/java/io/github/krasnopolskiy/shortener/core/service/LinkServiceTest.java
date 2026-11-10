package io.github.krasnopolskiy.shortener.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.krasnopolskiy.shortener.core.error.AccessDeniedException;
import io.github.krasnopolskiy.shortener.core.error.LinkExhaustedException;
import io.github.krasnopolskiy.shortener.core.error.LinkExpiredException;
import io.github.krasnopolskiy.shortener.core.error.LinkNotFoundException;
import io.github.krasnopolskiy.shortener.core.error.StorageException;
import io.github.krasnopolskiy.shortener.core.error.ValidationException;
import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.model.NotificationType;
import io.github.krasnopolskiy.shortener.core.port.CodeGenerator;
import io.github.krasnopolskiy.shortener.infra.Base62CodeGenerator;
import io.github.krasnopolskiy.shortener.support.InMemoryExpiredLinkRegistry;
import io.github.krasnopolskiy.shortener.support.InMemoryInbox;
import io.github.krasnopolskiy.shortener.support.InMemoryLinkRepository;
import io.github.krasnopolskiy.shortener.support.MutableClock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkServiceTest {

    private static final String URL = "https://www.baeldung.com/java-9-http-client";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();
    private final InMemoryLinkRepository links = new InMemoryLinkRepository();
    private final InMemoryExpiredLinkRegistry expired = new InMemoryExpiredLinkRegistry();
    private final InMemoryInbox inbox = new InMemoryInbox();
    private final LinkService service = serviceWith(new Base62CodeGenerator(7), TTL);

    private LinkService serviceWith(CodeGenerator codes, Duration ttl) {
        return new LinkService(
            links,
            expired,
            inbox,
            codes,
            new UrlValidator(2048, "lnk.local"),
            new LinkPolicy(ttl, DEFAULT_LIMIT, MAX_LIMIT, Duration.ofDays(7)),
            clock
        );
    }

    private void followTimes(String code, int times) {
        for (int i = 0; i < times; i++) {
            service.follow(code);
        }
    }

    @Test
    void differentUsersGetDifferentCodesForSameUrl() {
        assertNotEquals(service.create(alice, URL).code(), service.create(bob, URL).code());
    }

    @Test
    void sameUserGetsSeparateLinksForRepeatedUrl() {
        assertNotEquals(service.create(alice, URL).code(), service.create(alice, URL).code());
    }

    @Test
    void generatedCodesStayUniqueOverManyLinks() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            assertTrue(codes.add(service.create(i % 2 == 0 ? alice : bob, URL).code()));
        }
    }

    @Test
    void collidingCodeIsSkipped() {
        ArrayDeque<String> sequence = new ArrayDeque<>(List.of("same", "same", "other"));
        LinkService scripted = serviceWith(sequence::removeFirst, TTL);
        assertEquals("same", scripted.create(alice, URL).code());
        assertEquals("other", scripted.create(bob, URL).code());
    }

    @Test
    void recentlyExpiredCodeIsNotReissued() {
        ArrayDeque<String> sequence = new ArrayDeque<>(List.of("first", "first", "second"));
        LinkService scripted = serviceWith(sequence::removeFirst, TTL);
        scripted.create(alice, URL);
        clock.advance(TTL);
        scripted.removeExpired();
        assertEquals("second", scripted.create(bob, URL).code());
    }

    @Test
    void reportsFailureWhenNoFreeCodeExists() {
        LinkService stuck = serviceWith(() -> "same", TTL);
        stuck.create(alice, URL);
        assertThrows(StorageException.class, () -> stuck.create(bob, URL));
    }

    @Test
    void usesDefaultLimitWhenNoneGiven() {
        assertEquals(DEFAULT_LIMIT, service.create(alice, URL).clickLimit());
    }

    @Test
    void expiryComesFromPolicyNotFromUser() {
        Link link = service.create(alice, URL);
        assertEquals(link.createdAt().plus(TTL), link.expiresAt());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, MAX_LIMIT + 1 })
    void rejectsLimitOutOfRange(int limit) {
        assertThrows(ValidationException.class, () -> service.create(alice, URL, limit));
    }

    @Test
    void acceptsLimitAtUpperBound() {
        assertEquals(MAX_LIMIT, service.create(alice, URL, MAX_LIMIT).clickLimit());
    }

    @Test
    void followReturnsTargetAndCountsClick() {
        Link link = service.create(alice, URL, 3);
        Link followed = service.follow(link.code());
        assertEquals(URL, followed.targetUrl());
        assertEquals(1, followed.clicks());
    }

    @Test
    void anyoneCanFollowALink() {
        Link link = service.create(alice, URL, 3);
        assertEquals(URL, service.follow(link.code()).targetUrl());
        assertEquals(alice, service.follow(link.code()).owner());
    }

    @Test
    void blocksFollowOnceLimitIsReached() {
        Link link = service.create(alice, URL, 2);
        followTimes(link.code(), 2);
        LinkExhaustedException error = assertThrows(LinkExhaustedException.class, () -> service.follow(link.code()));
        assertTrue(error.getMessage().contains("2/2"));
        assertEquals(2, links.find(link.code()).orElseThrow().clicks());
    }

    @Test
    void notifiesOwnerExactlyOnceWhenLimitIsReached() {
        Link link = service.create(alice, URL, 1);
        service.follow(link.code());
        assertThrows(LinkExhaustedException.class, () -> service.follow(link.code()));
        List<Notification> notices = inbox.drain(alice);
        assertEquals(1, notices.size());
        assertEquals(NotificationType.LIMIT_EXHAUSTED, notices.get(0).type());
        assertEquals(link.code(), notices.get(0).code());
    }

    @Test
    void doesNotNotifyBeforeLimitIsReached() {
        Link link = service.create(alice, URL, 2);
        service.follow(link.code());
        assertTrue(inbox.drain(alice).isEmpty());
    }

    @Test
    void concurrentClicksNeverExceedLimit() throws InterruptedException, ExecutionException {
        Link link = service.create(alice, URL, 50);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        Callable<Void> click = () -> {
            try {
                service.follow(link.code());
                succeeded.incrementAndGet();
            } catch (LinkExhaustedException e) {
                blocked.incrementAndGet();
            }
            return null;
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Void> result : pool.invokeAll(
                IntStream.range(0, 400)
                    .mapToObj(i -> click)
                    .toList()
            )) {
                result.get();
            }
        }
        assertEquals(50, succeeded.get());
        assertEquals(350, blocked.get());
        assertEquals(50, links.find(link.code()).orElseThrow().clicks());
    }

    @ParameterizedTest
    @ValueSource(longs = { 1, 24, 72 })
    void removesLinkExactlyAtEndOfConfiguredLifetime(long hours) {
        Duration ttl = Duration.ofHours(hours);
        LinkService shortLived = serviceWith(new Base62CodeGenerator(7), ttl);
        Link link = shortLived.create(alice, URL);

        clock.advance(ttl.minusSeconds(1));
        assertEquals(0, shortLived.removeExpired());
        assertTrue(links.find(link.code()).isPresent());

        clock.advance(Duration.ofSeconds(1));
        assertEquals(1, shortLived.removeExpired());
        assertFalse(links.find(link.code()).isPresent());
    }

    @Test
    void cleanupNotifiesOwnerOfExpiredLink() {
        Link link = service.create(alice, URL);
        clock.advance(TTL);
        service.removeExpired();
        List<Notification> notices = inbox.drain(alice);
        assertEquals(1, notices.size());
        assertEquals(NotificationType.EXPIRED, notices.get(0).type());
        assertEquals(link.expiresAt(), notices.get(0).occurredAt());
    }

    @Test
    void cleanupKeepsLinksThatAreStillAlive() {
        Link oldLink = service.create(alice, URL);
        clock.advance(TTL.minusHours(1));
        Link newLink = service.create(alice, URL);
        clock.advance(Duration.ofHours(1));
        service.removeExpired();
        assertFalse(links.find(oldLink.code()).isPresent());
        assertTrue(links.find(newLink.code()).isPresent());
    }

    @Test
    void followBlocksExpiredLinkBeforeCleanupRuns() {
        Link link = service.create(alice, URL);
        clock.advance(TTL);
        assertThrows(LinkExpiredException.class, () -> service.follow(link.code()));
        assertFalse(links.find(link.code()).isPresent());
        assertEquals(1, inbox.drain(alice).size());
    }

    @Test
    void followAfterCleanupStillReportsExpiredNotMissing() {
        Link link = service.create(alice, URL);
        clock.advance(TTL);
        service.removeExpired();
        assertThrows(LinkExpiredException.class, () -> service.follow(link.code()));
        assertTrue(inbox.drain(bob).isEmpty());
    }

    @Test
    void expiredCodeIsForgottenAfterRetention() {
        Link link = service.create(alice, URL);
        clock.advance(TTL);
        service.removeExpired();
        clock.advance(Duration.ofDays(8));
        service.removeExpired();
        assertThrows(LinkNotFoundException.class, () -> service.follow(link.code()));
    }

    @Test
    void followOfUnknownCodeFails() {
        assertThrows(LinkNotFoundException.class, () -> service.follow("nothing"));
    }

    @Test
    void listShowsOnlyOwnLinksAndSeveralOfThem() {
        Link first = service.create(alice, URL);
        Link second = service.create(alice, "https://example.com");
        service.create(bob, URL);
        assertEquals(List.of(first, second), service.list(alice));
    }

    @Test
    void ownerCanRaiseLimitAndUnblockLink() {
        Link link = service.create(alice, URL, 1);
        service.follow(link.code());
        service.updateClickLimit(alice, link.code(), 2);
        assertEquals(2, service.follow(link.code()).clicks());
    }

    @Test
    void limitCannotDropToClicksAlreadyMade() {
        Link link = service.create(alice, URL, 5);
        followTimes(link.code(), 2);
        assertThrows(ValidationException.class, () -> service.updateClickLimit(alice, link.code(), 2));
        assertEquals(3, service.updateClickLimit(alice, link.code(), 3).clickLimit());
    }

    @Test
    void editedLimitIsCheckedAgainstMaximum() {
        Link link = service.create(alice, URL);
        assertThrows(ValidationException.class, () -> service.updateClickLimit(alice, link.code(), MAX_LIMIT + 1));
    }

    @Test
    void ownerCanChangeTargetButNotToInvalidUrl() {
        Link link = service.create(alice, URL);
        assertEquals(
            "https://example.org",
            service.updateTargetUrl(alice, link.code(), "https://example.org").targetUrl()
        );
        assertThrows(ValidationException.class, () ->
            service.updateTargetUrl(alice, link.code(), "javascript:alert(1)")
        );
        assertEquals("https://example.org", service.follow(link.code()).targetUrl());
    }

    @Test
    void otherUsersCannotEditOrDelete() {
        Link link = service.create(alice, URL, 5);
        assertThrows(AccessDeniedException.class, () -> service.updateClickLimit(bob, link.code(), 50));
        assertThrows(AccessDeniedException.class, () -> service.updateTargetUrl(bob, link.code(), "https://evil.test"));
        assertThrows(AccessDeniedException.class, () -> service.delete(bob, link.code()));
        assertEquals(Optional.of(link), links.find(link.code()));
    }

    @Test
    void ownerCanDelete() {
        Link link = service.create(alice, URL);
        service.delete(alice, link.code());
        assertThrows(LinkNotFoundException.class, () -> service.follow(link.code()));
        assertTrue(inbox.drain(alice).isEmpty());
    }
}
