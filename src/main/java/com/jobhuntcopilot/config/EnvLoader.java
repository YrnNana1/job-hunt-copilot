package com.jobhuntcopilot.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads secrets from environment variables, falling back to a local .env file
 * (see .env.example). Real environment variables win if both are set, which
 * matters once this runs somewhere other than my machine (CI, etc.) where
 * there's no .env file to read.
 */
public class EnvLoader {

    private static final Map<String, String> DOTENV_VALUES = loadDotEnvFile(Path.of(".env"));

    public static String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return DOTENV_VALUES.get(key);
    }

    /** Same as {@link #get(String)} but fails fast with a clear message instead of returning null. */
    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + key
                            + ". Copy .env.example to .env and fill it in.");
        }
        return value;
    }

    /** Parses ".env"-style lines (KEY=VALUE, blank lines and #comments ignored, quotes stripped). */
    static Map<String, String> parseDotEnvLines(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, equalsIndex).trim();
            String value = stripQuotes(trimmed.substring(equalsIndex + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            boolean wrappedInMatchingQuotes = (first == '"' && last == '"') || (first == '\'' && last == '\'');
            if (wrappedInMatchingQuotes) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static Map<String, String> loadDotEnvFile(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            return parseDotEnvLines(Files.readAllLines(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }
}
