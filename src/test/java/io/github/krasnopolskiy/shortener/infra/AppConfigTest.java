package io.github.krasnopolskiy.shortener.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppConfigTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("test.properties"), content);
    }

    @Test
    void loadsBundledDefaults() {
        AppConfig config = AppConfig.load(Optional.empty());
        assertEquals(Duration.ofHours(24), config.linkTtl());
        assertEquals(10, config.defaultClickLimit());
    }

    @Test
    void overrideFileReplacesOnlyItsKeys() throws IOException {
        AppConfig config = AppConfig.load(Optional.of(write("link.ttl=PT1H\nurl.max-length=100\n")));
        assertEquals(Duration.ofHours(1), config.linkTtl());
        assertEquals(100, config.maxUrlLength());
        assertEquals(1000, config.maxClickLimit());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "link.ttl=soon",
            "link.ttl=PT0S",
            "link.ttl=-PT1H",
            "link.ttl=",
            "short.code-length=2",
            "short.code-length=abc",
            "short.domain=bad domain",
            "link.click-limit.default=5000",
            "link.click-limit.max=0",
            "url.max-length=abc",
            "link.tll=PT1H",
        }
    )
    void rejectsBadValues(String line) throws IOException {
        Path file = write(line + "\n");
        assertThrows(ConfigException.class, () -> AppConfig.load(Optional.of(file)));
    }

    @Test
    void missingOverrideFileIsReported() {
        Path missing = dir.resolve("absent.properties");
        assertThrows(ConfigException.class, () -> AppConfig.load(Optional.of(missing)));
    }
}
