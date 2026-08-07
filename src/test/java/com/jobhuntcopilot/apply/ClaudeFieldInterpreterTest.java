package com.jobhuntcopilot.apply;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the JSON parsing and validation logic directly — no live Claude API call. Constructing
 * ClaudeFieldInterpreter with a fake key is safe (the SDK client only talks to the network on
 * .create(), which these tests never call); parseResponse/buildResult are pure and package-visible
 * specifically so this validation logic can be tested without hitting the network.
 */
class ClaudeFieldInterpreterTest {

    private final ClaudeFieldInterpreter interpreter = new ClaudeFieldInterpreter("test-api-key");

    private final Map<String, Object> availableAnswers = new LinkedHashMap<>();

    ClaudeFieldInterpreterTest() {
        availableAnswers.put("personal.email", "jane@example.com");
        availableAnswers.put("workAuthorization.authorizedToWorkInUs", true);
    }

    @Test
    void resolvesATextFieldToAMatchedKey() {
        String json = """
                {"matchedKey": "personal.email", "reason": "field asks for contact email"}
                """;
        FormField field = new FormField("id:contact_email", "Contact Email", FieldType.EMAIL, true, List.of());

        FieldMatch match = interpreter.buildResult(field, interpreter.parseResponse(json), availableAnswers);

        assertEquals(MatchSource.CLAUDE, match.source());
        assertEquals("jane@example.com", match.resolvedValue());
    }

    @Test
    void resolvesASelectFieldToAMatchedKeyAndOption() {
        String json = """
                {"matchedKey": "workAuthorization.authorizedToWorkInUs", "matchedOption": "Yes", "reason": "boolean maps to Yes"}
                """;
        FormField field = new FormField("id:auth", "Eligible to work?", FieldType.SELECT, true, List.of("Yes", "No"));

        FieldMatch match = interpreter.buildResult(field, interpreter.parseResponse(json), availableAnswers);

        assertEquals(MatchSource.CLAUDE, match.source());
        assertEquals("Yes", match.resolvedOptionText());
    }

    @Test
    void treatsExplicitUnknownAsUnmatched() {
        String json = """
                {"matchedKey": "unknown", "reason": "open-ended question, nothing in profile answers it"}
                """;
        FormField field = new FormField("id:why", "Why do you want to work here?", FieldType.TEXTAREA, false, List.of());

        FieldMatch match = interpreter.buildResult(field, interpreter.parseResponse(json), availableAnswers);

        assertEquals(MatchSource.UNMATCHED, match.source());
        assertTrue(match.flagged());
    }

    @Test
    void treatsAReferencedKeyThatWasNotOfferedAsUnmatched() {
        String json = """
                {"matchedKey": "personal.ssn", "reason": "fabricated key"}
                """;
        FormField field = new FormField("id:mystery", "Some field", FieldType.TEXT, false, List.of());

        FieldMatch match = interpreter.buildResult(field, interpreter.parseResponse(json), availableAnswers);

        assertEquals(MatchSource.UNMATCHED, match.source());
    }

    @Test
    void treatsASelectMatchWithNoRealOptionAsUnmatched() {
        String json = """
                {"matchedKey": "workAuthorization.authorizedToWorkInUs", "matchedOption": "Definitely", "reason": "..."}
                """;
        FormField field = new FormField("id:auth", "Eligible to work?", FieldType.SELECT, true, List.of("Yes", "No"));

        FieldMatch match = interpreter.buildResult(field, interpreter.parseResponse(json), availableAnswers);

        assertEquals(MatchSource.UNMATCHED, match.source());
    }

    @Test
    void throwsOnMalformedJson() {
        assertThrows(ApplyException.class, () -> interpreter.parseResponse("not json at all"));
    }
}
