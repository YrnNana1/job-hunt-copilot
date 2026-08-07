package com.jobhuntcopilot.apply;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jobhuntcopilot.config.ProfileConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback for fields FieldMatcher couldn't recognize by keyword/pattern — unfamiliar label
 * wording, not unfamiliar answers. Claude is only ever allowed to pick from the fixed, enumerated
 * list of the user's own real profile answer keys given to it in the prompt; it cannot author new
 * answer text for open-ended questions. If it says "unknown", names a key that wasn't offered, or
 * the call fails, the result is MatchSource.UNMATCHED — the same blank-and-flag outcome as a failed
 * pattern match, never a guess.
 */
public class ClaudeFieldInterpreter {

    private static final String MODEL = "claude-opus-4-5";
    private static final long MAX_TOKENS = 500L;

    private static final String SYSTEM_PROMPT = """
            You are helping match a job application form field to a real job candidate's own profile \
            data. These rules are non-negotiable:

            1. You may only choose one of the "availableAnswers" keys given to you in the user message, \
            or the literal string "unknown". Never invent a key, and never invent answer text of your \
            own — you are matching a field to an existing answer, not writing new content.
            2. If the field is an open-ended question (e.g. "Why do you want to work here?") that isn't \
            genuinely answered by one of the available keys, respond "unknown" — do not try to compose \
            a response to it.
            3. If the field is a SELECT or RADIO_GROUP with an "options" list, also choose which literal \
            option from that list best matches the resolved answer, in "matchedOption". If none of the \
            options are a confident match, respond "unknown" instead of picking the closest one.
            4. Respond with ONLY the JSON object described in the user message. No markdown code fences, \
            no prose before or after it.
            """;

    private final AnthropicClient client;
    private final Gson gson = new Gson();

    public ClaudeFieldInterpreter(String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public FieldMatch interpret(FormField field, ProfileConfig profile, Path resumePdfPath, Path coverLetterPdfPath) {
        Map<String, Object> availableAnswers = availableAnswers(profile, resumePdfPath, coverLetterPdfPath);
        String prompt = buildPrompt(field, availableAnswers);
        String responseText;
        try {
            responseText = callClaude(prompt);
        } catch (ApplyException e) {
            return FieldMatch.unmatched(field, "Claude field interpretation failed: " + e.getMessage());
        }
        ClaudeFieldResponse response = parseResponse(responseText);
        return buildResult(field, response, availableAnswers);
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
            throw new ApplyException("Claude API rate limit hit while interpreting a form field: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new ApplyException("Claude API error while interpreting a form field: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new ApplyException("Network error calling Claude API: " + e.getMessage(), e);
        }

        StringBuilder text = new StringBuilder();
        response.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(block -> text.append(block.text()));
        if (text.isEmpty()) {
            throw new ApplyException("Claude returned no text content while interpreting a form field "
                    + "(stop reason: " + response.stopReason() + ")");
        }
        return text.toString();
    }

    private String buildPrompt(FormField field, Map<String, Object> availableAnswers) {
        PromptPayload payload = new PromptPayload(
                field.labelText(), field.fieldType().name(), field.required(), field.options(), availableAnswers);

        return """
                Which of the availableAnswers keys (if any) does this form field correspond to? Respond \
                with a single JSON object of this exact shape:

                {"matchedKey": "personal.email", "matchedOption": "option text if select/radio, else omit", "reason": "why"}

                or, if none of the available answers confidently answer this field:

                {"matchedKey": "unknown", "reason": "why nothing matched"}

                Field and available answers (JSON):
                %s
                """.formatted(gson.toJson(payload));
    }

    private Map<String, Object> availableAnswers(ProfileConfig profile, Path resumePdfPath, Path coverLetterPdfPath) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("personal.firstName", profile.personal().firstName());
        answers.put("personal.lastName", profile.personal().lastName());
        answers.put("personal.fullName", profile.personal().fullName());
        answers.put("personal.email", profile.personal().email());
        answers.put("personal.phone", profile.personal().phone());
        answers.put("personal.linkedInUrl", profile.personal().linkedInUrl());
        answers.put("personal.websiteUrl", profile.personal().websiteUrl());
        answers.put("personal.location", profile.personal().location());
        answers.put("workAuthorization.authorizedToWorkInUs", profile.workAuthorization().authorizedToWorkInUs());
        answers.put("workAuthorization.requiresSponsorshipNow", profile.workAuthorization().requiresSponsorshipNow());
        answers.put("workAuthorization.requiresSponsorshipFuture", profile.workAuthorization().requiresSponsorshipFuture());
        if (resumePdfPath != null) {
            answers.put("documents.resumeUpload", resumePdfPath.toAbsolutePath().toString());
        }
        if (coverLetterPdfPath != null) {
            answers.put("documents.coverLetterUpload", coverLetterPdfPath.toAbsolutePath().toString());
        }
        return answers;
    }

    ClaudeFieldResponse parseResponse(String text) {
        String json = extractJson(text);
        try {
            ClaudeFieldResponse response = gson.fromJson(json, ClaudeFieldResponse.class);
            if (response == null || response.matchedKey() == null) {
                throw new ApplyException("Claude's response was missing matchedKey:\n" + text);
            }
            return response;
        } catch (JsonSyntaxException e) {
            throw new ApplyException("Could not parse Claude's field-interpretation response as JSON:\n" + text, e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new ApplyException("Claude's response did not contain a JSON object:\n" + text);
        }
        return text.substring(start, end + 1);
    }

    FieldMatch buildResult(FormField field, ClaudeFieldResponse response, Map<String, Object> availableAnswers) {
        if ("unknown".equalsIgnoreCase(response.matchedKey())) {
            return FieldMatch.unmatched(field, "Claude found no confident match"
                    + (response.reason() == null ? "" : ": " + response.reason()));
        }
        if (!availableAnswers.containsKey(response.matchedKey())) {
            return FieldMatch.unmatched(field, "Claude referenced an answer key that wasn't offered to it: "
                    + response.matchedKey());
        }

        Object value = availableAnswers.get(response.matchedKey());
        boolean isSelectLike = field.fieldType() == FieldType.SELECT || field.fieldType() == FieldType.RADIO_GROUP;
        if (isSelectLike) {
            if (response.matchedOption() == null || !field.options().contains(response.matchedOption())) {
                return FieldMatch.unmatched(field, "Claude matched a key but not a real option on this form");
            }
            return new FieldMatch(field, MatchSource.CLAUDE, String.valueOf(value), response.matchedOption(),
                    false, response.reason());
        }
        return new FieldMatch(field, MatchSource.CLAUDE, String.valueOf(value), null, false, response.reason());
    }

    private record PromptPayload(
            String labelText, String fieldType, boolean required, List<String> options, Map<String, Object> availableAnswers) {
    }

    record ClaudeFieldResponse(String matchedKey, String matchedOption, String reason) {
    }
}
