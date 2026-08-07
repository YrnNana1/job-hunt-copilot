package com.jobhuntcopilot.coverletter;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.resume.LatexTextExtractor;
import com.jobhuntcopilot.tailor.FabricationHeuristic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls Claude to decide how to tailor base_cover_letter.tex to a specific posting — reordering
 * and rewording REAL paragraph content to surface keywords from the posting. Claude never sees the
 * header/contact block, salutation, or signature block as editable fields — only paragraph text —
 * so those stay unchanged by construction, not just by prompting. Every returned paragraph must
 * reference an existing original paragraph id and every original id must be explicitly accounted
 * for, so there's no way for a paragraph to silently appear, disappear, or get fabricated without
 * failing validation.
 */
public class ClaudeCoverLetterWriter {

    private static final String MODEL = "claude-opus-4-5";
    private static final long MAX_TOKENS = 4000L;

    private static final String SYSTEM_PROMPT = """
            You are helping a real job candidate tailor their cover letter to a specific job posting. \
            These rules are non-negotiable:

            1. Never invent, exaggerate, or add any skill, tool, metric, employer, credential, or \
            accomplishment that is not already present in the paragraph text you are given. Every \
            paragraph you return must be traceable to the original text of that exact paragraph ID.
            2. The opening and closing paragraphs must always stay first and last — you may reword them \
            for keyword alignment, but you may never drop them or change their position.
            3. You may reorder and/or reword the body paragraphs to prioritize whichever is most \
            relevant to the posting. Each body paragraph has a fixed heading you are not shown as an \
            editable field and cannot change.
            4. Rewording means surfacing keywords or terminology from the posting for something the \
            candidate genuinely stated, described more precisely — not embellished. Never soften a \
            paragraph into something vaguer, and never add a claim, number, or credential that was not \
            already there.
            5. You may drop at most one body paragraph if it is the least relevant to this posting and \
            the letter is running long, but at least one body paragraph must remain, and you may never \
            drop the opening or closing paragraph.
            6. You must explicitly account for every paragraph ID given to you (opening, every body \
            paragraph, and closing), marking each kept, reworded, or dropped — do not silently omit any \
            ID from your response.
            7. Respond with ONLY the JSON object described in the user message. No markdown code fences, \
            no prose before or after it.
            """;

    private final AnthropicClient client;
    private final Gson gson = new Gson();

    public ClaudeCoverLetterWriter(String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public CoverLetterResult write(Job job, CoverLetterDocument document) {
        String prompt = buildPrompt(job, document);
        String responseText = callClaude(prompt);
        ClaudeCoverLetterResponse response = parseResponse(responseText);
        return buildResult(document, response);
    }

    private String callClaude(String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(prompt)
                .build();

        Message response;
        try {
            response = client.messages().create(params);
        } catch (RateLimitException e) {
            throw new CoverLetterException("Claude API rate limit hit while writing cover letter: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new CoverLetterException("Claude API error while writing cover letter: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new CoverLetterException("Network error calling Claude API: " + e.getMessage(), e);
        }

        StringBuilder text = new StringBuilder();
        response.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(block -> text.append(block.text()));
        if (text.isEmpty()) {
            throw new CoverLetterException("Claude returned no text content while writing cover letter "
                    + "(stop reason: " + response.stopReason() + ")");
        }
        return text.toString();
    }

    private String buildPrompt(Job job, CoverLetterDocument document) {
        PromptPayload payload = new PromptPayload(
                new PromptPosting(job.getTitle(), job.getCompany(),
                        job.getDescription() == null ? "" : job.getDescription()),
                new PromptParagraph("opening", null, LatexTextExtractor.toPlainText(document.opening().text())),
                document.bodyParagraphs().stream().map(this::toPromptParagraph).toList(),
                new PromptParagraph("closing", null, LatexTextExtractor.toPlainText(document.closing().text())));

        return """
                Tailor this cover letter to the job posting below. Respond with a single JSON object of \
                this exact shape:

                {
                  "opening": {"text": "kept or reworded opening paragraph", "reason": "why reworded, omit if unchanged"},
                  "bodyParagraphs": [
                    {"paragraphId": "body1", "text": "kept or reworded text", "dropped": false, "reason": "why reworded, omit if unchanged"},
                    {"paragraphId": "body2", "text": "original text unchanged", "dropped": true, "reason": "why dropped, required if dropped"}
                  ],
                  "closing": {"text": "kept or reworded closing paragraph", "reason": "why reworded, omit if unchanged"}
                }

                Every body paragraph ID given below (body1..bodyN) must appear exactly once in your \
                response. List order in "bodyParagraphs" is the display order you want for the ones you \
                keep — dropped ones can appear anywhere in the list.

                Cover letter content and job posting (JSON):
                %s
                """.formatted(gson.toJson(payload));
    }

    private PromptParagraph toPromptParagraph(CoverLetterParagraph paragraph) {
        // Claude only ever sees/produces plain text — never LaTeX escape sequences like \& — so
        // rewording can't accidentally corrupt them. buildResult() re-escapes on the way back.
        return new PromptParagraph(paragraph.id(), LatexTextExtractor.toPlainText(paragraph.heading()),
                LatexTextExtractor.toPlainText(paragraph.text()));
    }

    ClaudeCoverLetterResponse parseResponse(String text) {
        String json = extractJson(text);
        try {
            ClaudeCoverLetterResponse response = gson.fromJson(json, ClaudeCoverLetterResponse.class);
            if (response == null || response.opening() == null || response.bodyParagraphs() == null
                    || response.closing() == null) {
                throw new CoverLetterException(
                        "Claude's response was missing the opening/bodyParagraphs/closing sections:\n" + text);
            }
            return response;
        } catch (JsonSyntaxException e) {
            throw new CoverLetterException("Could not parse Claude's cover letter response as JSON:\n" + text, e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new CoverLetterException("Claude's response did not contain a JSON object:\n" + text);
        }
        return text.substring(start, end + 1);
    }

    CoverLetterResult buildResult(CoverLetterDocument document, ClaudeCoverLetterResponse response) {
        List<CoverLetterChange> changes = new ArrayList<>();

        String openingText = rewordedOrThrow(response.opening().text(), "opening");
        String openingRewordedText = LatexTextExtractor.escapeSpecialCharacters(openingText);
        if (!openingRewordedText.equals(document.opening().text())) {
            List<String> newNumbers = FabricationHeuristic.newNumbersIn(document.opening().text(), openingRewordedText);
            changes.add(new CoverLetterChange("opening", null, CoverLetterChange.ChangeType.REWORDED,
                    document.opening().text(), openingRewordedText, response.opening().reason(), newNumbers));
        }

        String closingText = rewordedOrThrow(response.closing().text(), "closing");
        String closingRewordedText = LatexTextExtractor.escapeSpecialCharacters(closingText);
        if (!closingRewordedText.equals(document.closing().text())) {
            List<String> newNumbers = FabricationHeuristic.newNumbersIn(document.closing().text(), closingRewordedText);
            changes.add(new CoverLetterChange("closing", null, CoverLetterChange.ChangeType.REWORDED,
                    document.closing().text(), closingRewordedText, response.closing().reason(), newNumbers));
        }

        List<CoverLetterParagraphPlan> bodyPlan = buildBodyPlan(document.bodyParagraphs(), response.bodyParagraphs(), changes);

        return new CoverLetterResult(new CoverLetterPlan(openingRewordedText, bodyPlan, closingRewordedText), changes);
    }

    private String rewordedOrThrow(String text, String paragraphId) {
        if (text == null || text.isBlank()) {
            throw new CoverLetterException("Claude's response has empty text for the " + paragraphId + " paragraph");
        }
        return text;
    }

    private List<CoverLetterParagraphPlan> buildBodyPlan(
            List<CoverLetterParagraph> original, List<ClaudeBodyParagraphResponse> responses,
            List<CoverLetterChange> changes) {
        Map<String, CoverLetterParagraph> originalById = original.stream()
                .collect(Collectors.toMap(CoverLetterParagraph::id, p -> p, (a, b) -> a, LinkedHashMap::new));

        Map<String, ClaudeBodyParagraphResponse> seen = new LinkedHashMap<>();
        for (ClaudeBodyParagraphResponse paragraphResponse : responses) {
            if (paragraphResponse.paragraphId() == null || !originalById.containsKey(paragraphResponse.paragraphId())) {
                throw new CoverLetterException(
                        "Claude's response referenced an unknown body paragraph id: " + paragraphResponse.paragraphId());
            }
            if (seen.put(paragraphResponse.paragraphId(), paragraphResponse) != null) {
                throw new CoverLetterException(
                        "Claude's response listed body paragraph " + paragraphResponse.paragraphId() + " more than once");
            }
            if (paragraphResponse.text() == null || paragraphResponse.text().isBlank()) {
                throw new CoverLetterException(
                        "Claude's response has empty text for body paragraph " + paragraphResponse.paragraphId());
            }
        }
        if (!seen.keySet().equals(originalById.keySet())) {
            throw new CoverLetterException("Claude's response did not account for every body paragraph (expected "
                    + originalById.keySet() + ", got " + seen.keySet() + ")");
        }

        List<CoverLetterParagraphPlan> kept = new ArrayList<>();
        List<String> keptIdsInResponseOrder = new ArrayList<>();
        for (ClaudeBodyParagraphResponse paragraphResponse : responses) {
            CoverLetterParagraph originalParagraph = originalById.get(paragraphResponse.paragraphId());
            if (paragraphResponse.dropped()) {
                changes.add(new CoverLetterChange(paragraphResponse.paragraphId(), originalParagraph.heading(),
                        CoverLetterChange.ChangeType.DROPPED, originalParagraph.text(), null,
                        paragraphResponse.reason(), List.of()));
                continue;
            }
            // Claude returned plain text (see toPromptParagraph) — re-escape LaTeX special characters
            // before this becomes part of the .tex source, rather than trust Claude to preserve
            // escape sequences it was never shown in the first place.
            String rewordedText = LatexTextExtractor.escapeSpecialCharacters(paragraphResponse.text());
            kept.add(new CoverLetterParagraphPlan(paragraphResponse.paragraphId(), rewordedText));
            keptIdsInResponseOrder.add(paragraphResponse.paragraphId());
            if (!rewordedText.equals(originalParagraph.text())) {
                List<String> newNumbers = FabricationHeuristic.newNumbersIn(originalParagraph.text(), rewordedText);
                changes.add(new CoverLetterChange(paragraphResponse.paragraphId(), originalParagraph.heading(),
                        CoverLetterChange.ChangeType.REWORDED, originalParagraph.text(), rewordedText,
                        paragraphResponse.reason(), newNumbers));
            }
        }
        if (kept.isEmpty()) {
            throw new CoverLetterException("Claude's response dropped every body paragraph — at least one must remain");
        }

        List<String> originalKeptOrder = original.stream()
                .map(CoverLetterParagraph::id)
                .filter(keptIdsInResponseOrder::contains)
                .toList();
        if (!originalKeptOrder.equals(keptIdsInResponseOrder)) {
            for (String paragraphId : keptIdsInResponseOrder) {
                boolean alreadyReworded = changes.stream().anyMatch(c ->
                        paragraphId.equals(c.paragraphId()) && c.type() == CoverLetterChange.ChangeType.REWORDED);
                if (!alreadyReworded) {
                    CoverLetterParagraph originalParagraph = originalById.get(paragraphId);
                    changes.add(new CoverLetterChange(paragraphId, originalParagraph.heading(),
                            CoverLetterChange.ChangeType.REORDERED, originalParagraph.text(), originalParagraph.text(),
                            seen.get(paragraphId).reason(), List.of()));
                }
            }
        }

        return kept;
    }

    private record PromptPosting(String title, String company, String description) {
    }

    private record PromptParagraph(String paragraphId, String heading, String text) {
    }

    private record PromptPayload(
            PromptPosting posting, PromptParagraph opening, List<PromptParagraph> bodyParagraphs,
            PromptParagraph closing) {
    }

    record ClaudeParagraphResponse(String text, String reason) {
    }

    record ClaudeBodyParagraphResponse(String paragraphId, String text, boolean dropped, String reason) {
    }

    record ClaudeCoverLetterResponse(
            ClaudeParagraphResponse opening, List<ClaudeBodyParagraphResponse> bodyParagraphs,
            ClaudeParagraphResponse closing) {
    }
}
