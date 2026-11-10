package io.github.krasnopolskiy.shortener.infra;

import io.github.krasnopolskiy.shortener.core.service.LinkService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CleanupScheduler implements AutoCloseable {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "link-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private final LinkService service;
    private final Runnable afterRun;
    private final Consumer<RuntimeException> onError;

    public CleanupScheduler(LinkService service, Runnable afterRun, Consumer<RuntimeException> onError) {
        this.service = service;
        this.afterRun = afterRun;
        this.onError = onError;
    }

    public void start(Duration interval) {
        executor.scheduleWithFixedDelay(this::run, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void run() {
        try {
            service.removeExpired();
            afterRun.run();
        } catch (RuntimeException e) {
            onError.accept(e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
