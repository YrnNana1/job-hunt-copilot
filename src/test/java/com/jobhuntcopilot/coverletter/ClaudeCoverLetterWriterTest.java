package com.jobhuntcopilot.coverletter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the JSON parsing and validation logic directly — no live Claude API call. Constructing
 * ClaudeCoverLetterWriter with a fake key is safe (the SDK client only talks to the network on
 * .create(), which these tests never call); parseResponse/buildResult are pure and package-visible
 * specifically so this validation logic can be tested without hitting the network.
 */
class ClaudeCoverLetterWriterTest {

    private final ClaudeCoverLetterWriter writer = new ClaudeCoverLetterWriter("test-api-key");

    private final CoverLetterDocument document = new CoverLetterDocument(
            "PREFIX",
            new CoverLetterParagraph("opening", null, "Opening paragraph."),
            List.of(
                    new CoverLetterParagraph("body1", "Heading One", "Body one text."),
                    new CoverLetterParagraph("body2", "Heading Two", "Body two text.")),
            new CoverLetterParagraph("closing", null, "Closing paragraph."),
            "SUFFIX");

    @Test
    void detectsRewordingOfOpeningAndBodyAndReorderingOfKeptParagraphs() {
        String json = """
                Here you go:
                ```json
                {
                  "opening": {"text": "Opening paragraph, aligned with the posting.", "reason": "surfaces keyword"},
                  "bodyParagraphs": [
                    {"paragraphId": "body2", "text": "Body two text with 5 microservices.", "dropped": false, "reason": "more relevant, moved first"},
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": false}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                ```
                """;

        ClaudeCoverLetterWriter.ClaudeCoverLetterResponse response = writer.parseResponse(json);
        CoverLetterResult result = writer.buildResult(document, response);

        assertEquals("Opening paragraph, aligned with the posting.", result.plan().openingText());
        assertEquals("Closing paragraph.", result.plan().closingText());
        assertEquals(List.of("body2", "body1"),
                result.plan().bodyParagraphs().stream().map(CoverLetterParagraphPlan::paragraphId).toList());

        List<CoverLetterChange> changes = result.changes();
        assertTrue(changes.stream().anyMatch(c -> c.type() == CoverLetterChange.ChangeType.REWORDED
                && "opening".equals(c.paragraphId())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == CoverLetterChange.ChangeType.REWORDED
                && "body2".equals(c.paragraphId())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == CoverLetterChange.ChangeType.REORDERED
                && "body1".equals(c.paragraphId())));
        assertTrue(changes.stream().noneMatch(c -> "closing".equals(c.paragraphId())));

        CoverLetterChange rewordedBody = changes.stream()
                .filter(c -> "body2".equals(c.paragraphId()) && c.type() == CoverLetterChange.ChangeType.REWORDED)
                .findFirst().orElseThrow();
        assertEquals(List.of("5"), rewordedBody.suspiciousNewNumbers());
    }

    @Test
    void dropsABodyParagraphAndRecordsDropped() {
        String json = """
                {
                  "opening": {"text": "Opening paragraph."},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": true, "reason": "least relevant"},
                    {"paragraphId": "body2", "text": "Body two text.", "dropped": false}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                """;

        CoverLetterResult result = writer.buildResult(document, writer.parseResponse(json));

        assertEquals(List.of("body2"),
                result.plan().bodyParagraphs().stream().map(CoverLetterParagraphPlan::paragraphId).toList());
        assertTrue(result.changes().stream().anyMatch(c -> c.type() == CoverLetterChange.ChangeType.DROPPED
                && "body1".equals(c.paragraphId())));
    }

    @Test
    void throwsWhenABodyParagraphIsMissingFromTheResponse() {
        String json = """
                {
                  "opening": {"text": "Opening paragraph."},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": false}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                """;

        ClaudeCoverLetterWriter.ClaudeCoverLetterResponse response = writer.parseResponse(json);
        assertThrows(CoverLetterException.class, () -> writer.buildResult(document, response));
    }

    @Test
    void throwsWhenAnUnknownParagraphIdIsReferenced() {
        String json = """
                {
                  "opening": {"text": "Opening paragraph."},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": false},
                    {"paragraphId": "body99", "text": "Fabricated paragraph.", "dropped": false}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                """;

        ClaudeCoverLetterWriter.ClaudeCoverLetterResponse response = writer.parseResponse(json);
        CoverLetterException exception = assertThrows(CoverLetterException.class,
                () -> writer.buildResult(document, response));
        assertTrue(exception.getMessage().contains("unknown body paragraph id"));
    }

    @Test
    void throwsWhenEveryBodyParagraphIsDropped() {
        String json = """
                {
                  "opening": {"text": "Opening paragraph."},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": true, "reason": "trim"},
                    {"paragraphId": "body2", "text": "Body two text.", "dropped": true, "reason": "trim"}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                """;

        ClaudeCoverLetterWriter.ClaudeCoverLetterResponse response = writer.parseResponse(json);
        assertThrows(CoverLetterException.class, () -> writer.buildResult(document, response));
    }

    @Test
    void throwsWhenOpeningTextIsBlank() {
        String json = """
                {
                  "opening": {"text": "  "},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "Body one text.", "dropped": false},
                    {"paragraphId": "body2", "text": "Body two text.", "dropped": false}
                  ],
                  "closing": {"text": "Closing paragraph."}
                }
                """;

        ClaudeCoverLetterWriter.ClaudeCoverLetterResponse response = writer.parseResponse(json);
        assertThrows(CoverLetterException.class, () -> writer.buildResult(document, response));
    }

    @Test
    void throwsOnMalformedJson() {
        assertThrows(CoverLetterException.class, () -> writer.parseResponse("not json at all"));
    }
}
