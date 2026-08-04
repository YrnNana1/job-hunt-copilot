package com.jobhuntcopilot.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the pure ".env" line parser directly — no filesystem or real env vars involved. */
class EnvLoaderTest {

    @Test
    void parsesSimpleKeyValuePairs() {
        Map<String, String> values = EnvLoader.parseDotEnvLines(List.of("FOO=bar", "BAZ=qux"));

        assertEquals("bar", values.get("FOO"));
        assertEquals("qux", values.get("BAZ"));
    }

    @Test
    void ignoresBlankLinesAndComments() {
        Map<String, String> values = EnvLoader.parseDotEnvLines(List.of(
                "# a comment", "", "   ", "FOO=bar"));

        assertEquals(1, values.size());
        assertEquals("bar", values.get("FOO"));
    }

    @Test
    void stripsSurroundingQuotes() {
        Map<String, String> values = EnvLoader.parseDotEnvLines(List.of(
                "DOUBLE=\"quoted value\"", "SINGLE='quoted value'", "UNQUOTED=plain"));

        assertEquals("quoted value", values.get("DOUBLE"));
        assertEquals("quoted value", values.get("SINGLE"));
        assertEquals("plain", values.get("UNQUOTED"));
    }

    @Test
    void ignoresLinesWithoutAnEqualsSign() {
        Map<String, String> values = EnvLoader.parseDotEnvLines(List.of("NOT_A_VALID_LINE"));

        assertTrue(values.isEmpty());
    }

    @Test
    void blankValueIsAllowed() {
        Map<String, String> values = EnvLoader.parseDotEnvLines(List.of("EMPTY="));

        assertTrue(values.containsKey("EMPTY"));
        assertEquals("", values.get("EMPTY"));
    }
}
