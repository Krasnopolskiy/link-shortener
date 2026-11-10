package io.github.krasnopolskiy.shortener;

import io.github.krasnopolskiy.shortener.cli.ConsoleApp;
import io.github.krasnopolskiy.shortener.core.error.ShortenerException;
import io.github.krasnopolskiy.shortener.core.service.ShortLinkFormat;
import io.github.krasnopolskiy.shortener.infra.AppConfig;
import io.github.krasnopolskiy.shortener.infra.CleanupScheduler;
import io.github.krasnopolskiy.shortener.infra.ConfigException;
import io.github.krasnopolskiy.shortener.infra.DesktopBrowser;
import io.github.krasnopolskiy.shortener.infra.Wiring;
import io.github.krasnopolskiy.shortener.infra.storage.StorageLock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final int EXIT_OK = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_USAGE = 2;
    private static final int CONFIG_ARGUMENTS = 2;
    private static final List<String> HELP_FLAGS = List.of("--help", "-h");
    private static final String USAGE = "Usage: java -jar link-shortener.jar [--config <file>]";

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        if (args.length == 1 && HELP_FLAGS.contains(args[0])) {
            System.out.println(USAGE);
            return EXIT_OK;
        }
        if (args.length != 0 && !(args.length == CONFIG_ARGUMENTS && args[0].equals("--config"))) {
            System.err.println(USAGE);
            return EXIT_USAGE;
        }
        try {
            AppConfig config = AppConfig.load(
                args.length == CONFIG_ARGUMENTS ? Optional.of(Path.of(args[1])) : Optional.empty()
            );
            startConsole(config);
            return EXIT_OK;
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            return EXIT_USAGE;
        } catch (ShortenerException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private static void startConsole(AppConfig config) throws IOException {
        try (StorageLock lock = StorageLock.acquire(config.storageDir())) {
            Wiring wiring = Wiring.create(config, Clock.systemUTC());
            ConsoleApp console = createConsole(config, wiring);
            try (
                CleanupScheduler scheduler = new CleanupScheduler(wiring.service(), console::deliverNotifications, e ->
                    System.err.println("Cleanup failed: " + e.getMessage())
                )
            ) {
                scheduler.start(config.cleanupInterval());
                console.run();
            }
        }
    }

    private static ConsoleApp createConsole(AppConfig config, Wiring wiring) {
        return new ConsoleApp(
            new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset())),
            new PrintWriter(System.out, true, System.out.charset()),
            wiring.service(),
            wiring.session(),
            wiring.inbox(),
            new DesktopBrowser(),
            new ShortLinkFormat(config.shortDomain()),
            ZoneId.systemDefault()
        );
    }
}
