package io.github.krasnopolskiy.shortener.core.service;

import java.util.Optional;
import java.util.regex.Pattern;

public class ShortLinkFormat {

    private static final Pattern CODE = Pattern.compile("[0-9A-Za-z]+");
    private static final Pattern SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private final String prefix;

    public ShortLinkFormat(String domain) {
        this.prefix = domain + "/";
    }

    public String format(String code) {
        return prefix + code;
    }

    public Optional<String> parseAddress(String input) {
        String address = SCHEME.matcher(input).replaceFirst("");
        if (!address.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return Optional.empty();
        }
        String code = address.substring(prefix.length());
        return CODE.matcher(code).matches() ? Optional.of(code) : Optional.empty();
    }

    public String toCode(String input) {
        return parseAddress(input).orElse(input);
    }
}
