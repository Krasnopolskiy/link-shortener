package io.github.krasnopolskiy.shortener.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.krasnopolskiy.shortener.core.service.LinkPolicy;
import io.github.krasnopolskiy.shortener.core.service.LinkService;
import io.github.krasnopolskiy.shortener.core.service.UrlValidator;
import io.github.krasnopolskiy.shortener.support.InMemoryExpiredLinkRegistry;
import io.github.krasnopolskiy.shortener.support.InMemoryInbox;
import io.github.krasnopolskiy.shortener.support.InMemoryLinkRepository;
import io.github.krasnopolskiy.shortener.support.MutableClock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CleanupSchedulerTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryLinkRepository links = new InMemoryLinkRepository();
    private final LinkService service = new LinkService(
        links,
        new InMemoryExpiredLinkRegistry(),
        new InMemoryInbox(),
        new Base62CodeGenerator(7),
        new UrlValidator(2048, "lnk.local"),
        new LinkPolicy(Duration.ofMinutes(1), 10, 100, Duration.ofDays(1)),
        clock
    );

    @Test
    void deletesExpiredLinksWithoutAnyUserAction() throws InterruptedException {
        UUID owner = UUID.randomUUID();
        String code = service.create(owner, "https://example.com").code();
        clock.advance(Duration.ofMinutes(2));
        CountDownLatch ran = new CountDownLatch(1);
        try (CleanupScheduler scheduler = new CleanupScheduler(service, ran::countDown, e -> {})) {
            scheduler.start(Duration.ofMillis(10));
            assertTrue(ran.await(5, TimeUnit.SECONDS));
        }
        assertTrue(links.find(code).isEmpty());
    }

    @Test
    void keepsRunningAfterAFailure() throws InterruptedException {
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch secondRun = new CountDownLatch(2);
        Runnable failing = () -> {
            runs.incrementAndGet();
            secondRun.countDown();
            throw new IllegalStateException("boom");
        };
        try (CleanupScheduler scheduler = new CleanupScheduler(service, failing, e -> {})) {
            scheduler.start(Duration.ofMillis(10));
            assertTrue(secondRun.await(5, TimeUnit.SECONDS));
        }
        assertTrue(runs.get() >= 2);
    }
}
