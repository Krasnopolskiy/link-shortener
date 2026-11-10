package io.github.krasnopolskiy.shortener.cli;

import io.github.krasnopolskiy.shortener.core.error.ShortenerException;
import io.github.krasnopolskiy.shortener.core.model.Notification;
import io.github.krasnopolskiy.shortener.core.model.NotificationType;
import io.github.krasnopolskiy.shortener.core.port.Browser;
import io.github.krasnopolskiy.shortener.core.port.NotificationInbox;
import io.github.krasnopolskiy.shortener.core.port.UserSession;
import io.github.krasnopolskiy.shortener.core.service.LinkService;
import io.github.krasnopolskiy.shortener.core.service.ShortLinkFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ConsoleApp {

    private static final String PROMPT = "> ";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final BufferedReader in;
    private final PrintWriter out;
    private final UserSession session;
    private final NotificationInbox inbox;
    private final ShortLinkFormat format;
    private final DateTimeFormatter times;
    private final Commands commands;
    private final Object outputLock = new Object();

    private boolean running = true;
    private boolean awaitingInput;

    public ConsoleApp(
        BufferedReader in,
        PrintWriter out,
        LinkService service,
        UserSession session,
        NotificationInbox inbox,
        Browser browser,
        ShortLinkFormat format,
        ZoneId zone
    ) {
        this.in = in;
        this.out = out;
        this.session = session;
        this.inbox = inbox;
        this.format = format;
        this.times = TIME_FORMAT.withZone(zone);
        this.commands = new Commands(
            new LinkCommands(service, session, browser, format, times, out),
            new UserCommands(service, session, out),
            out,
            () -> running = false
        );
    }

    public void run() throws IOException {
        out.println("Link shortener. Type 'help' for commands, 'exit' to leave.");
        deliverNotifications();
        while (running) {
            String line = readLine();
            if (line == null) {
                break;
            }
            synchronized (outputLock) {
                execute(line.strip());
            }
            deliverNotifications();
        }
        out.println("Link shortener stopped.");
        out.flush();
    }

    public void deliverNotifications() {
        synchronized (outputLock) {
            List<Notification> pending = pendingNotifications();
            if (pending.isEmpty()) {
                return;
            }
            if (awaitingInput) {
                out.println();
            }
            pending.forEach(this::printNotice);
            if (awaitingInput) {
                out.print(PROMPT);
            }
            out.flush();
        }
    }

    private String readLine() throws IOException {
        synchronized (outputLock) {
            out.print(PROMPT);
            out.flush();
            awaitingInput = true;
        }
        String line = in.readLine();
        synchronized (outputLock) {
            awaitingInput = false;
        }
        return line;
    }

    private List<Notification> pendingNotifications() {
        return session.current().map(inbox::drain).orElse(List.of());
    }

    private void printNotice(Notification notice) {
        String address = format.format(notice.code());
        String text =
            notice.type() == NotificationType.LIMIT_EXHAUSTED
                ? "used its last click and is now blocked. Raise the limit with: edit " + notice.code() + " limit <n>"
                : "expired at " + times.format(notice.occurredAt()) + " and was deleted.";
        out.println("[notice] Your link " + address + " (" + notice.targetUrl() + ") " + text);
    }

    private void execute(String input) {
        if (input.isEmpty()) {
            return;
        }
        String line = format.parseAddress(input).isPresent() ? "open " + input : input;
        List<String> tokens = List.of(line.split("\\s+"));
        Optional<Command> command = commands.find(tokens.get(0).toLowerCase(Locale.ROOT));
        if (command.isEmpty()) {
            printUnknown(tokens.get(0));
            return;
        }
        runGuarded(command.get(), tokens.subList(1, tokens.size()));
    }

    private void runGuarded(Command command, List<String> arguments) {
        try {
            command.handler().run(arguments);
        } catch (UsageException e) {
            out.println("Error: " + e.getMessage());
            out.println("Usage: " + command.usage());
        } catch (ShortenerException e) {
            out.println("Error: " + e.getMessage());
        } catch (RuntimeException e) {
            out.println("Unexpected error: " + e + ". The program keeps running; your data is safe.");
        }
    }

    private void printUnknown(String word) {
        if (word.contains("://")) {
            out.println("To shorten this address, type: shorten " + word);
            return;
        }
        out.println("Unknown command '" + word + "'. Type 'help' to see all commands.");
    }
}
