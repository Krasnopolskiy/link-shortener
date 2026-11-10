package io.github.krasnopolskiy.shortener.cli;

import io.github.krasnopolskiy.shortener.core.port.UserSession;
import io.github.krasnopolskiy.shortener.core.service.LinkService;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;

class UserCommands {

    static final String NO_USER = "No user yet. Create your first link with: shorten <url>";

    private final LinkService service;
    private final UserSession session;
    private final PrintWriter out;

    UserCommands(LinkService service, UserSession session, PrintWriter out) {
        this.service = service;
        this.session = session;
        this.out = out;
    }

    List<Command> all() {
        return List.of(
            new Command(
                "whoami",
                "whoami",
                "Show your user UUID. Keep it to return to your links.",
                List.of("whoami"),
                this::whoami
            ),
            new Command(
                "login",
                "login <uuid>",
                "Continue as the user with this UUID.",
                List.of("login 123e4567-e89b-12d3-a456-426614174000"),
                this::login
            ),
            new Command(
                "logout",
                "logout",
                "Forget the current user. The next new link starts a new one.",
                List.of("logout"),
                this::logout
            )
        );
    }

    private void whoami(List<String> args) {
        Arguments.requireCount(args, 0);
        out.println(
            session
                .current()
                .map(user -> "Your user ID: " + user)
                .orElse(NO_USER)
        );
    }

    private void login(List<String> args) {
        Arguments.requireCount(args, 1);
        UUID user = parseUuid(args.get(0));
        session.switchTo(user);
        out.println("You are now " + user + " (" + service.list(user).size() + " links).");
    }

    private void logout(List<String> args) {
        Arguments.requireCount(args, 0);
        session.clear();
        out.println("Logged out. Your next new link starts a new user.");
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new UsageException("'" + text + "' is not a valid UUID.");
        }
    }
}
