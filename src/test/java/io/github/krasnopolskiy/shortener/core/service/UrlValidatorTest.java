package io.github.krasnopolskiy.shortener.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.krasnopolskiy.shortener.core.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator(60, "lnk.local");

    @ParameterizedTest
    @ValueSource(
        strings = {
            "http://example.com",
            "https://example.com/a/b?x=1&y=2#top",
            "HTTPS://EXAMPLE.COM",
            "https://example.com:8443/path",
            "http://127.0.0.1:8080",
            "http://[::1]:8080/",
            "https://example.com/путь",
        }
    )
    void acceptsValidUrls(String url) {
        assertEquals(url, validator.normalize(url));
    }

    @Test
    void trimsSurroundingSpace() {
        assertEquals("https://example.com", validator.normalize("  https://example.com "));
    }

    @Test
    void convertsInternationalHostToAscii() {
        assertEquals("https://xn--e1afmkfd.xn--p1ai/page", validator.normalize("https://пример.рф/page"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {
            " ",
            "example.com",
            "ftp://example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "mailto:a@b.c",
            "http://",
            "https:///path",
            "http://exa mple.com",
            "http://user:pw@example.com",
            "https://example.com@evil.test",
            "http://example.com:99999",
            "https://lnk.local/abc",
            "https://LNK.LOCAL/abc",
            "https://[bad",
        }
    )
    void rejectsInvalidUrls(String url) {
        assertThrows(ValidationException.class, () -> validator.normalize(url));
    }

    @Test
    void enforcesLengthBoundary() {
        String atLimit = "https://example.com/" + "a".repeat(40);
        assertEquals(atLimit, validator.normalize(atLimit));
        assertThrows(ValidationException.class, () -> validator.normalize(atLimit + "a"));
    }
}
