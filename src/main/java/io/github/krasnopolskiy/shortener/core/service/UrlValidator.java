package io.github.krasnopolskiy.shortener.core.service;

import io.github.krasnopolskiy.shortener.core.error.ValidationException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_PORT = 65535;

    private final int maxLength;
    private final String shortDomain;

    public UrlValidator(int maxLength, String shortDomain) {
        this.maxLength = maxLength;
        this.shortDomain = shortDomain;
    }

    public String normalize(String raw) {
        String url = checkedText(raw);
        URI uri = parse(url);
        checkScheme(uri);
        checkNoUserInfo(uri);
        String asciiUrl = uri.getHost() == null ? withAsciiHost(url, uri) : url;
        checkHostAndPort(parse(asciiUrl), asciiUrl);
        return asciiUrl;
    }

    private String checkedText(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("URL is empty.");
        }
        String url = raw.strip();
        if (url.length() > maxLength) {
            throw new ValidationException(
                "URL is too long: " + url.length() + " characters, the maximum is " + maxLength + "."
            );
        }
        return url;
    }

    private void checkHostAndPort(URI uri, String url) {
        if (uri.getHost() == null) {
            throw new ValidationException("URL has no valid host: " + url);
        }
        if (uri.getPort() > MAX_PORT) {
            throw new ValidationException("URL has an invalid port: " + uri.getPort());
        }
        if (uri.getHost().equalsIgnoreCase(shortDomain)) {
            throw new ValidationException("This is already a short link. Enter the original URL instead.");
        }
    }

    private static void checkNoUserInfo(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority != null && authority.contains("@")) {
            throw new ValidationException("URLs with a user name or password (user@host) are not allowed.");
        }
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException("Not a valid URL: " + e.getReason() + " (position " + e.getIndex() + ").");
        }
    }

    private static void checkScheme(URI uri) {
        if (uri.getScheme() == null) {
            throw new ValidationException("URL has no scheme. Start it with http:// or https://");
        }
        if (!ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Only http and https URLs are supported, got '" + uri.getScheme() + "'.");
        }
    }

    private static String withAsciiHost(String url, URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null) {
            throw new ValidationException("URL has no host: " + url);
        }
        int portSeparator = authority.lastIndexOf(':');
        String host = portSeparator < 0 ? authority : authority.substring(0, portSeparator);
        try {
            return url.replace(host, IDN.toASCII(host));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("URL has no valid host: " + url);
        }
    }
}
