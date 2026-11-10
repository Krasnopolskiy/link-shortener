package io.github.krasnopolskiy.shortener.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortLinkFormatTest {

    private final ShortLinkFormat format = new ShortLinkFormat("lnk.local");

    @Test
    void formatsAddress() {
        assertEquals("lnk.local/aB3", format.format("aB3"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "lnk.local/aB3", "http://lnk.local/aB3", "https://lnk.local/aB3", "LNK.local/aB3" })
    void parsesAddressForms(String input) {
        assertEquals(Optional.of("aB3"), format.parseAddress(input));
    }

    @ParameterizedTest
    @ValueSource(strings = { "aB3", "lnk.local/", "lnk.local/a/b", "lnk.local/a-b", "other.io/aB3", "lnk.localx/aB3" })
    void rejectsAnythingElseAsAddress(String input) {
        assertTrue(format.parseAddress(input).isEmpty());
    }

    @Test
    void toCodeAcceptsBareCode() {
        assertEquals("aB3", format.toCode("aB3"));
        assertEquals("aB3", format.toCode("lnk.local/aB3"));
    }
}
