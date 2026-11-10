package io.github.krasnopolskiy.shortener.cli;

import io.github.krasnopolskiy.shortener.core.error.ValidationException;
import io.github.krasnopolskiy.shortener.core.model.Link;
import io.github.krasnopolskiy.shortener.core.port.Browser;
import io.github.krasnopolskiy.shortener.core.port.UserSession;
import io.github.krasnopolskiy.shortener.core.service.LinkService;
import io.github.krasnopolskiy.shortener.core.service.ShortLinkFormat;
import java.io.PrintWriter;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

class LinkCommands {

    private static final String LIST_ROW = "%-26s %-9s %-10s %-25s %s%n";
    private static final int SHORTEN_MAX_ARGUMENTS = 2;
    private static final int EDIT_ARGUMENTS = 3;

    private final LinkService service;
    private final UserSession session;
    private final Browser browser;
    private final ShortLinkFormat format;
    private final DateTimeFormatter times;
    private final PrintWriter out;

    LinkCommands(
        LinkService service,
        UserSession session,
        Browser browser,
        ShortLinkFormat format,
        DateTimeFormatter times,
        PrintWriter out
    ) {
        this.service = service;
        this.session = session;
        this.browser = browser;
        this.format = format;
        this.times = times;
        this.out = out;
    }

    List<Command> all() {
        return List.of(
            new Command(
                "shorten",
                "shorten <url> [click-limit]",
                "Create a short link. Without a limit the configured default is used.",
                List.of("shorten https://www.baeldung.com/java-9-http-client", "shorten https://example.com 5"),
                this::shorten
            ),
            new Command(
                "open",
                "open <short-link>",
                "Open the original page in your browser. Pasting a short link alone does the same.",
                List.of("open lnk.local/aB3dE9x", "open aB3dE9x"),
                this::open
            ),
            new Command("list", "list", "Show your links with clicks, status and expiry.", List.of("list"), this::list),
            new Command(
                "edit",
                "edit <short-link> limit <n> | edit <short-link> url <new-url>",
                "Change the click limit or the destination of your link. The lifetime is fixed by the system.",
                List.of("edit aB3dE9x limit 20", "edit aB3dE9x url https://example.org"),
                this::edit
            ),
            new Command("delete", "delete <short-link>", "Delete your link.", List.of("delete aB3dE9x"), this::delete)
        );
    }

    private void shorten(List<String> args) {
        if (args.isEmpty() || args.size() > SHORTEN_MAX_ARGUMENTS) {
            throw new UsageException("Expected a URL and an optional click limit.");
        }
        Optional<UUID> currentUser = session.current();
        UUID user = currentUser.orElseGet(UUID::randomUUID);
        Link link =
            args.size() == SHORTEN_MAX_ARGUMENTS
                ? service.create(user, args.get(0), Arguments.parseNumber(args.get(1)))
                : service.create(user, args.get(0));
        if (currentUser.isEmpty()) {
            session.switchTo(user);
            out.println("Welcome! Your user ID is " + user);
            out.println("Keep it: 'login <uuid>' brings you back to your links.");
        }
        out.println("Short link: " + format.format(link.code()));
        out.println("  Opens " + link.targetUrl());
        out.println("  Clicks allowed: " + link.clickLimit() + ", expires " + times.format(link.expiresAt()));
    }

    private void open(List<String> args) {
        Arguments.requireCount(args, 1);
        Link link = service.follow(format.toCode(args.get(0)));
        String clicks = "(click " + link.clicks() + " of " + link.clickLimit() + ")";
        if (browser.open(URI.create(link.targetUrl()))) {
            out.println("Opened " + link.targetUrl() + " in your browser " + clicks);
            return;
        }
        out.println("Could not start a browser here. Open this address yourself " + clicks + ":");
        out.println("  " + link.targetUrl());
    }

    private void list(List<String> args) {
        Arguments.requireCount(args, 0);
        Optional<UUID> user = session.current();
        if (user.isEmpty()) {
            out.println(UserCommands.NO_USER);
            return;
        }
        List<Link> links = service.list(user.get());
        if (links.isEmpty()) {
            out.println("You have no links. Create one with: shorten <url>");
            return;
        }
        out.printf(LIST_ROW, "SHORT LINK", "CLICKS", "STATUS", "EXPIRES", "TARGET");
        for (Link link : links) {
            out.printf(
                LIST_ROW,
                format.format(link.code()),
                link.clicks() + "/" + link.clickLimit(),
                status(link),
                times.format(link.expiresAt()),
                link.targetUrl()
            );
        }
    }

    private void edit(List<String> args) {
        Arguments.requireCount(args, EDIT_ARGUMENTS);
        String code = format.toCode(args.get(0));
        UUID user = requireUser();
        switch (args.get(1).toLowerCase(Locale.ROOT)) {
            case "limit" -> {
                Link link = service.updateClickLimit(user, code, Arguments.parseNumber(args.get(2)));
                out.println("Click limit of " + format.format(code) + " is now " + link.clickLimit() + ".");
            }
            case "url" -> {
                Link link = service.updateTargetUrl(user, code, args.get(2));
                out.println(format.format(code) + " now opens " + link.targetUrl());
            }
            default -> throw new UsageException("Can edit 'limit' or 'url', not '" + args.get(1) + "'.");
        }
    }

    private void delete(List<String> args) {
        Arguments.requireCount(args, 1);
        String code = format.toCode(args.get(0));
        service.delete(requireUser(), code);
        out.println("Deleted " + format.format(code) + ".");
    }

    private UUID requireUser() {
        return session
            .current()
            .orElseThrow(() ->
                new ValidationException("This needs your user ID. Run 'login <uuid>' or create a link first.")
            );
    }

    private static String status(Link link) {
        return link.isExhausted() ? "BLOCKED" : "ACTIVE";
    }
}
