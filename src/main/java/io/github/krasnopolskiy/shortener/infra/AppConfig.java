package io.github.krasnopolskiy.shortener.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public record AppConfig(
    String shortDomain,
    int codeLength,
    Duration linkTtl,
    int defaultClickLimit,
    int maxClickLimit,
    Duration tombstoneRetention,
    int maxUrlLength,
    Duration cleanupInterval,
    Path storageDir
) {
    private static final String DEFAULTS_RESOURCE = "/application.properties";
    private static final int MIN_POSITIVE = 1;
    private static final int MIN_CODE_LENGTH = 4;
    private static final int MAX_CODE_LENGTH = 32;
    private static final int LIMIT_NOT_BOUNDED = Integer.MAX_VALUE;

    public static AppConfig load(Optional<Path> overrideFile) {
        Properties properties = new Properties();
        try (InputStream defaults = AppConfig.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (defaults == null) {
                throw new ConfigException("Bundled " + DEFAULTS_RESOURCE + " is missing.");
            }
            properties.load(defaults);
        } catch (IOException e) {
            throw new ConfigException("Cannot read bundled defaults: " + e.getMessage(), e);
        }
        Set<String> knownKeys = Set.copyOf(properties.stringPropertyNames());
        overrideFile.ifPresent(file -> readOverrides(file, properties));
        Set<String> unknown = new TreeSet<>(properties.stringPropertyNames());
        unknown.removeAll(knownKeys);
        if (!unknown.isEmpty()) {
            throw new ConfigException("Unknown configuration keys: " + unknown);
        }
        return from(properties);
    }

    static AppConfig from(Properties properties) {
        Values values = new Values(properties);
        int maxClickLimit = values.integer("link.click-limit.max", MIN_POSITIVE, LIMIT_NOT_BOUNDED);
        return new AppConfig(
            values.domain("short.domain"),
            values.integer("short.code-length", MIN_CODE_LENGTH, MAX_CODE_LENGTH),
            values.positiveDuration("link.ttl"),
            values.integer("link.click-limit.default", MIN_POSITIVE, maxClickLimit),
            maxClickLimit,
            values.positiveDuration("link.tombstone-retention"),
            values.integer("url.max-length", MIN_POSITIVE, LIMIT_NOT_BOUNDED),
            values.positiveDuration("cleanup.interval"),
            Path.of(values.text("storage.dir"))
        );
    }

    private static void readOverrides(Path file, Properties properties) {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfigException("Cannot read configuration file " + file + ": " + e.getMessage(), e);
        }
    }

    private record Values(Properties properties) {
        String text(String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new ConfigException("Configuration key " + key + " is missing or empty.");
            }
            return value.strip();
        }

        int integer(String key, int min, int max) {
            String value = text(key);
            int number;
            try {
                number = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new ConfigException(key + ": expected a whole number, got '" + value + "'.", e);
            }
            if (number < min || number > max) {
                throw new ConfigException(key + ": must be between " + min + " and " + max + ", got " + number + ".");
            }
            return number;
        }

        Duration positiveDuration(String key) {
            String value = text(key);
            Duration duration;
            try {
                duration = Duration.parse(value);
            } catch (DateTimeParseException e) {
                throw new ConfigException(
                    key + ": expected an ISO-8601 duration such as PT24H, got '" + value + "'.",
                    e
                );
            }
            if (duration.isZero() || duration.isNegative()) {
                throw new ConfigException(key + ": must be positive, got " + value + ".");
            }
            return duration;
        }

        String domain(String key) {
            String value = text(key);
            if (!value.matches("[A-Za-z0-9.-]+")) {
                throw new ConfigException(key + ": not a valid domain: '" + value + "'.");
            }
            return value;
        }
    }
}
