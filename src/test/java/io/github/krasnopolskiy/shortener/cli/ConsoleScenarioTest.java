package io.github.krasnopolskiy.shortener.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.krasnopolskiy.shortener.core.port.Browser;
import io.github.krasnopolskiy.shortener.core.service.ShortLinkFormat;
import io.github.krasnopolskiy.shortener.infra.AppConfig;
import io.github.krasnopolskiy.shortener.infra.Wiring;
import io.github.krasnopolskiy.shortener.support.MutableClock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleScenarioTest {

    private static final String URL = "https://www.baeldung.com/java-9-http-client";
    private static final Pattern SHORT_LINK = Pattern.compile("Short link: lnk\\.local/(\\w+)");
    private static final Pattern USER_ID = Pattern.compile("Your user ID is ([0-9a-f-]{36})");

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock();
    private final List<URI> opened = new ArrayList<>();

    private AppConfig config() {
        return new AppConfig(
            "lnk.local",
            7,
            Duration.ofHours(24),
            10,
            1000,
            Duration.ofDays(7),
            2048,
            Duration.ofSeconds(30),
            dir
        );
    }

    private String session(String... commands) {
        return sessionWith(uri -> opened.add(uri), commands);
    }

    private String sessionWith(Browser browser, String... commands) {
        Wiring wiring = Wiring.create(config(), clock);
        StringWriter output = new StringWriter();
        ConsoleApp console = new ConsoleApp(
            new BufferedReader(new StringReader(String.join("\n", commands) + "\n")),
            new PrintWriter(output, true),
            wiring.service(),
            wiring.session(),
            wiring.inbox(),
            browser,
            new ShortLinkFormat("lnk.local"),
            ZoneOffset.UTC
        );
        try {
            console.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toString();
    }

    private static String extract(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        assertTrue(matcher.find(), output);
        return matcher.group(1);
    }

    private String createLink(String url, String... limit) {
        String command = "shorten " + url + (limit.length > 0 ? " " + limit[0] : "");
        return extract(SHORT_LINK, session(command));
    }

    @Test
    void uuidAppearsOnFirstLinkOnlyAndPersists() {
        assertTrue(session("whoami").contains("No user yet"));
        String first = session("shorten " + URL);
        String user = extract(USER_ID, first);
        assertFalse(session("shorten " + URL).contains("Your user ID is"));
        assertTrue(session("whoami").contains(user));
    }

    @Test
    void invalidFirstLinkDoesNotCreateAUser() {
        assertTrue(session("shorten notaurl").contains("Error: URL has no scheme"));
        assertTrue(session("shorten https://a.io abc").contains("not a whole number"));
        assertTrue(session("shorten https://a.io 0").contains("between 1 and 1000"));
        assertTrue(session("whoami").contains("No user yet"));
    }

    @Test
    void twoUsersGetDifferentLinksToSameUrlAndSeeOnlyTheirOwn() {
        String first = session("shorten " + URL);
        String aliceCode = extract(SHORT_LINK, first);
        String alice = extract(USER_ID, first);
        session("logout");
        String second = session("shorten " + URL);
        String bobCode = extract(SHORT_LINK, second);
        assertNotEquals(aliceCode, bobCode);

        String bobList = session("list");
        assertTrue(bobList.contains(bobCode));
        assertFalse(bobList.contains(aliceCode));

        String aliceList = session("login " + alice, "list");
        assertTrue(aliceList.contains(aliceCode));
        assertFalse(aliceList.contains(bobCode));
    }

    @Test
    void oneUserKeepsSeveralLinks() {
        String one = createLink("https://example.com/one");
        String two = createLink("https://example.com/two");
        String list = session("list");
        assertTrue(list.contains(one));
        assertTrue(list.contains(two));
    }

    @Test
    void pastingShortLinkOpensBrowser() {
        String code = createLink(URL);
        String output = session("lnk.local/" + code);
        assertEquals(List.of(URI.create(URL)), opened);
        assertTrue(output.contains("Opened " + URL));
    }

    @Test
    void openAcceptsBareCodeAndFullAddress() {
        String code = createLink(URL);
        session("open " + code, "open https://lnk.local/" + code);
        assertEquals(2, opened.size());
    }

    @Test
    void limitBlocksFurtherClicksAndNotifiesOwner() {
        String code = createLink(URL, "2");
        String output = session("open " + code, "open " + code, "open " + code, "list");
        assertEquals(2, opened.size());
        assertTrue(output.contains("[notice] Your link lnk.local/" + code));
        assertTrue(output.contains("used its last click"));
        assertTrue(output.contains("Error: Link '" + code + "' is unavailable: the click limit is used up (2/2)"));
        assertTrue(output.contains("BLOCKED"));
    }

    @Test
    void limitSurvivesRestart() {
        String code = createLink(URL, "1");
        session("open " + code);
        assertTrue(session("open " + code).contains("click limit is used up"));
        assertEquals(1, opened.size());
    }

    @Test
    void ownerRaisesLimitAndLinkWorksAgain() {
        String code = createLink(URL, "1");
        String output = session("open " + code, "edit " + code + " limit 3", "open " + code);
        assertEquals(2, opened.size());
        assertTrue(output.contains("is now 3"));
    }

    @Test
    void ttlExpiryDeletesLinkAndTellsBothOwnerAndVisitor() {
        String code = createLink(URL);
        clock.advance(Duration.ofHours(24));
        String output = session("open " + code, "list", "open " + code);
        assertTrue(output.contains("Error: Link '" + code + "' is unavailable: its lifetime ended"));
        assertTrue(output.contains("expired at 2026-01-16 12:00:00 Z and was deleted"));
        assertTrue(output.contains("You have no links"));
        assertTrue(opened.isEmpty());
    }

    @Test
    void linkStaysAliveUntilItsLastSecond() {
        String code = createLink(URL);
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        session("open " + code);
        assertEquals(1, opened.size());
    }

    @Test
    void offlineOwnerGetsNoticeAtNextLogin() {
        String first = session("shorten " + URL);
        String alice = extract(USER_ID, first);
        session("logout");
        session("shorten https://example.com");
        clock.advance(Duration.ofHours(25));
        String bobView = session("list");
        assertTrue(bobView.contains("[notice]"));
        assertFalse(bobView.contains(URL));
        assertTrue(session("login " + alice).contains(URL));
    }

    @Test
    void strangerCannotEditOrDeleteThroughConsole() {
        String code = createLink(URL, "5");
        session("logout");
        session("shorten https://example.com");
        String output = session("edit " + code + " limit 50", "edit " + code + " url https://x.io", "delete " + code);
        assertEquals(3, output.split("belongs to another user", -1).length - 1);
    }

    @Test
    void anonymousUserCannotEdit() {
        assertTrue(session("delete abc").contains("needs your user ID"));
    }

    @Test
    void ownerDeletesLink() {
        String code = createLink(URL);
        String output = session("delete " + code, "open " + code);
        assertTrue(output.contains("Deleted lnk.local/" + code));
        assertTrue(output.contains("does not exist"));
    }

    @Test
    void badInputNeverStopsTheProgram() {
        String output = session(
            "",
            "nonsense",
            "https://example.com",
            "shorten",
            "shorten ftp://a.b",
            "edit x",
            "edit x color red",
            "login not-a-uuid",
            "open",
            "open missing",
            "help nothing",
            "whoami"
        );
        assertTrue(output.contains("Unknown command 'nonsense'"));
        assertTrue(output.contains("To shorten this address, type: shorten https://example.com"));
        assertTrue(output.contains("Usage: shorten <url> [click-limit]"));
        assertTrue(output.contains("Only http and https"));
        assertTrue(output.contains("is not a valid UUID"));
        assertTrue(output.contains("does not exist"));
        assertTrue(output.contains("No user yet"));
        assertTrue(output.endsWith("Link shortener stopped." + System.lineSeparator()));
    }

    @Test
    void browserFailureStillShowsAddress() {
        String code = createLink(URL);
        String output = sessionWith(uri -> false, "open " + code);
        assertTrue(output.contains("Open this address yourself"));
        assertTrue(output.contains(URL));
    }

    @Test
    void helpListsEveryCommandAndShowsExamples() {
        String all = session("help");
        for (String command : List.of("shorten", "open", "list", "edit", "delete", "whoami", "login", "logout")) {
            assertTrue(all.contains(command), command);
        }
        assertTrue(session("help edit").contains("edit aB3dE9x limit 20"));
    }
}
