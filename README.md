# link-shortener

A console service that turns long URLs into short links with a click limit and a lifetime. A UUID identifies each
user, with no sign-up.

```bash
./gradlew jar                                        # gradlew.bat on Windows
java -jar build/libs/link-shortener.jar
java -jar build/libs/link-shortener.jar --config my.properties
```

You need JDK 21 or newer. The program has no runtime dependencies, and Gradle downloads the build ones. `./gradlew
build` also needs Node.js for the format check.

Exit codes: `1` storage error, `2` bad arguments or configuration.

## Usage

```text
> shorten https://www.baeldung.com/java-9-http-client 2
Welcome! Your user ID is d0eb24fe-1719-412b-9e3a-21a238a224a4
Keep it: 'login <uuid>' brings you back to your links.
Short link: lnk.local/38Y0jWs
  Opens https://www.baeldung.com/java-9-http-client
  Clicks allowed: 2, expires 2026-11-10 19:56:37 CST
> lnk.local/38Y0jWs
Opened https://www.baeldung.com/java-9-http-client in your browser (click 1 of 2)
```

Your first `shorten` creates your UUID, and the program keeps it between runs. Save it: `login <uuid>` brings you
back after `logout`.

`<short-link>` accepts the address (`lnk.local/38Y0jWs`) or the code (`38Y0jWs`).

| Command | Effect |
|---|---|
| `shorten <url> [limit]` | create a short link, with the configured default limit if you give none |
| `open <short-link>` | open the original page and count a click |
| `lnk.local/38Y0jWs` | paste a short address alone to open it |
| `list` | show your links with clicks, status and expiry |
| `edit <short-link> limit <n>` | change the limit; `n` must exceed the clicks already made |
| `edit <short-link> url <url>` | change the destination |
| `delete <short-link>` | delete your link |
| `whoami` | show your UUID |
| `login <uuid>` | continue as that user |
| `logout` | forget the current user; the next `shorten` creates a new one |
| `help [command]` | list commands, or show usage and examples of one |
| `exit` | leave; your links stay |

Anyone can follow a link. Only its creator can edit or delete it. The lifetime comes from the configuration, and no
command changes it.

## Notifications

You get a `[notice]` when your link uses its last click and when the cleanup deletes it, even while you sit at the
prompt. If another user is active, the notice waits for your `login` and survives a restart. A visitor of a blocked
or expired link sees the reason.

## Configuration

`src/main/resources/application.properties` holds the defaults. A file passed with `--config` overrides the keys it
lists. The program refuses to start on an unknown key or a bad value and names the key.

| Key | Default | Meaning |
|---|---|---|
| `short.domain` | `lnk.local` | domain shown in short links |
| `short.code-length` | `7` | code length, 4 to 32 |
| `link.ttl` | `PT24H` | lifetime of a link |
| `link.click-limit.default` | `10` | limit when the user gives none |
| `link.click-limit.max` | `1000` | largest limit a user may set |
| `link.tombstone-retention` | `P7D` | how long the service keeps codes of expired links |
| `url.max-length` | `2048` | longest URL accepted |
| `cleanup.interval` | `PT30S` | period of the background cleanup |
| `storage.dir` | `data` | directory for links, notifications and the current user |

Durations use ISO-8601: `PT1H` is an hour, `P7D` a week.

## Architecture

`core` knows nothing about the console or files. It declares ports, `infra` implements them, and `Wiring` connects
the two through constructors. Tests run `LinkService` without files and move time with a fake `Clock`.

| Module | Contents |
|---|---|
| `core.model` | immutable records `Link` and `Notification` |
| `core.error` | expected failures, with messages written for the user |
| `core.port` | `LinkRepository`, `ExpiredLinkRegistry`, `NotificationInbox`, `CodeGenerator`, `UserSession`, `Browser` |
| `core.service` | `LinkService` with the link rules, `UrlValidator`, `ShortLinkFormat`, `LinkPolicy` |
| `infra` | `AppConfig`, `Base62CodeGenerator`, `DesktopBrowser`, `CleanupScheduler`, `Wiring` |
| `infra.storage` | file implementations of the ports, `TsvFile`, `StorageLock` |
| `cli` | `ConsoleApp` with the input loop and notices, `Commands` with the registry and help, `LinkCommands`, `UserCommands`, `Arguments` |

Decisions:

- **Unique codes.** `Base62CodeGenerator` draws 7 random base62 characters from `SecureRandom`. `LinkService` checks
  each code against existing links inside one lock, so two users cannot receive the same code.
- **Click counting.** `LinkService` synchronizes its public methods, which makes the limit check and the increment
  one step. A test with 400 parallel clicks and a limit of 50 records 50.
- **Expiry.** `CleanupScheduler` deletes expired links every `cleanup.interval`. `LinkService` also checks the
  lifetime on each access, so a link stops working at its expiry moment.
- **Expired codes.** After deletion the service keeps the code and the time, without the URL. A visitor reads
  "lifetime ended", and the generator cannot reissue that code during `link.tombstone-retention`.
- **URL checks.** The address goes to the system browser, so `UrlValidator` allows `http` and `https` alone. It also
  rejects `user@host` addresses, bad ports and links to the service itself, and converts Cyrillic hosts to Punycode.
- **Storage.** `TsvFile` writes to a temporary file and renames it. On a corrupted file the program names the line
  and stops, which keeps the next write from erasing your data. `StorageLock` blocks a second process on the same directory.
- **Access.** The assignment asks for no authentication, so a UUID grants access to its links. Treat it as a password.

## Testing

```bash
./gradlew test    # unit tests and console scenarios
./gradlew build   # compile, Checkstyle, Spotless, tests
```

The suite has 117 tests. The report lands in `build/reports/tests/test/index.html`.

| Class | Covers |
|---|---|
| `LinkServiceTest` | uniqueness, limits, parallel clicks, TTL of 1, 24 and 72 hours, notifications, ownership |
| `UrlValidatorTest` | valid and invalid URLs, the length boundary |
| `ShortLinkFormatTest` | short address parsing |
| `AppConfigTest` | loading and validating the configuration |
| `CleanupSchedulerTest` | deletion with no user action, recovery after a failure |
| `FileStorageTest` | reload after restart, corrupted files, the process lock |
| `ConsoleScenarioTest` | assignment scenarios with real commands and files, a fake clock and a fake browser |

To watch a link expire by hand, run this config, where links live 15 seconds:

```bash
java -jar build/libs/link-shortener.jar --config config/fast-expiry.properties
```

## CI

`.github/workflows/ci.yml` runs `./gradlew build` on each push. Spotless checks the prettier-java format, and
`./gradlew spotlessApply` fixes it. `.editorconfig` holds the editor settings.
