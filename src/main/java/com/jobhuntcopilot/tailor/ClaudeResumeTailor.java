package com.jobhuntcopilot.tailor;

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
import com.jobhuntcopilot.resume.ResumeBullet;
import com.jobhuntcopilot.resume.ResumeDocument;
import com.jobhuntcopilot.resume.ResumeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls Claude to decide how to tailor base_resume.tex's Experience and Technical Projects
 * bullets to a specific posting — reordering and rewording REAL content to surface keywords from
 * the posting. Claude never sees entry headers (titles/dates/employers/degree/GPA/certifications)
 * as editable fields — only bullet text — so those stay unchanged by construction, not just by
 * prompting. Every returned bullet must reference an existing original bullet id and every
 * original id must be explicitly accounted for, so there's no way for a bullet to silently appear,
 * disappear, or get fabricated without failing validation.
 */
public class ClaudeResumeTailor {

    private static final String MODEL = "claude-opus-4-5";
    private static final long MAX_TOKENS = 8000L;

    private static final String SYSTEM_PROMPT = """
            You are helping a real job candidate tailor the Experience and Technical Projects bullets of \
            their resume to a specific job posting. These rules are non-negotiable:

            1. Never invent, exaggerate, or add any skill, tool, metric, employer, or accomplishment that \
            is not already present in the bullet text you are given. Every bullet you return must be \
            traceable to the original text of that exact bullet ID.
            2. You may only reorder and reword bullets WITHIN their own entry (job or project) — never \
            move a bullet to a different entry. You are never shown job titles, employers, dates, degree, \
            GPA, or certifications; those are fixed and not part of this task.
            3. Rewording means surfacing keywords or terminology from the posting for something the \
            candidate genuinely did, described more precisely — not embellished. Never soften a bullet \
            into something vaguer, and never add a claim, number, or tool that was not already there.
            4. You may drop a bullet if it is the least relevant and space is tight, but every entry \
            (job, or a project you keep) must retain at least one bullet.
            5. For Technical Projects only, you may reorder which project appears first, and may drop an \
            entire project if needed to help the resume fit on one page — but never drop every project, \
            and prefer trimming bullets over dropping a whole project when either would work.
            6. Never drop an entire Experience (job) entry — only trim its bullets. Real work history \
            stays intact.
            7. You must explicitly account for every entry and every bullet ID given to you, marking each \
            kept, reworded, or dropped — do not silently omit any ID from your response.
            8. Respond with ONLY the JSON object described in the user message. No markdown code fences, \
            no prose before or after it.
            """;

    private final AnthropicClient client;
    private final Gson gson = new Gson();

    public ClaudeResumeTailor(String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public TailoringResult tailor(Job job, ResumeDocument resume) {
        String prompt = buildPrompt(job, resume);
        String responseText = callClaude(prompt);
        ClaudeTailorResponse response = parseResponse(responseText);
        return buildResult(resume, response);
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
            throw new TailoringException("Claude API rate limit hit while tailoring resume: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new TailoringException("Claude API error while tailoring resume: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new TailoringException("Network error calling Claude API: " + e.getMessage(), e);
        }

        StringBuilder text = new StringBuilder();
        response.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(block -> text.append(block.text()));
        if (text.isEmpty()) {
            throw new TailoringException("Claude returned no text content while tailoring resume "
                    + "(stop reason: " + response.stopReason() + ")");
        }
        return text.toString();
    }

    private String buildPrompt(Job job, ResumeDocument resume) {
        PromptPayload payload = new PromptPayload(
                new PromptPosting(job.getTitle(), job.getCompany(),
                        job.getDescription() == null ? "" : job.getDescription()),
                resume.experienceEntries().stream().map(this::toPromptEntry).toList(),
                resume.projectEntries().stream().map(this::toPromptEntry).toList());

        return """
                Tailor these resume bullets to the job posting below. Respond with a single JSON object \
                of this exact shape (one object per entry, one bullet object per original bullet ID — \
                every ID must appear exactly once):

                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "kept or reworded text", "dropped": false, "reason": "why reworded, omit if unchanged"},
                      {"bulletId": "exp1-b2", "text": "original text unchanged", "dropped": true, "reason": "why dropped, required if dropped"}
                    ]}
                  ],
                  "projects": [
                    {"entryId": "proj2", "dropped": false, "reason": "why kept/prioritized, omit if unchanged position",
                     "bullets": [{"bulletId": "proj2-b1", "text": "...", "dropped": false}]}
                  ]
                }

                Every experience entry (exp1..expN) and every project entry (proj1..projN) given below \
                must appear exactly once in your response. For experience, list order doesn't matter — \
                jobs always render in their original order. For projects, list order is the display \
                order you want. Within each entry, list every one of its original bullet IDs exactly \
                once, in the order you want them displayed.

                Resume content and job posting (JSON):
                %s
                """.formatted(gson.toJson(payload));
    }

    private PromptEntry toPromptEntry(ResumeEntry entry) {
        // Claude only ever sees/produces plain text — never LaTeX escape sequences like \& — so
        // rewording can't accidentally corrupt them. buildEntryPlan() re-escapes on the way back.
        List<PromptBullet> bullets = entry.bullets().stream()
                .map(b -> new PromptBullet(b.id(), LatexTextExtractor.toPlainText(b.text())))
                .toList();
        return new PromptEntry(entry.id(), contextLabel(entry), bullets);
    }

    private String contextLabel(ResumeEntry entry) {
        return LatexTextExtractor.toPlainText(entry.headerLatex());
    }

    ClaudeTailorResponse parseResponse(String text) {
        String json = extractJson(text);
        try {
            ClaudeTailorResponse response = gson.fromJson(json, ClaudeTailorResponse.class);
            if (response == null || response.experience() == null || response.projects() == null) {
                throw new TailoringException(
                        "Claude's response was missing the experience/projects sections:\n" + text);
            }
            return response;
        } catch (JsonSyntaxException e) {
            throw new TailoringException("Could not parse Claude's tailoring response as JSON:\n" + text, e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new TailoringException("Claude's response did not contain a JSON object:\n" + text);
        }
        return text.substring(start, end + 1);
    }

    TailoringResult buildResult(ResumeDocument resume, ClaudeTailorResponse response) {
        List<TailoringChange> changes = new ArrayList<>();

        Map<String, ClaudeEntryResponse> experienceByEntryId =
                indexByEntryId(response.experience(), resume.experienceEntries(), "experience");
        List<EntryPlan> experiencePlan = new ArrayList<>();
        for (ResumeEntry original : resume.experienceEntries()) {
            ClaudeEntryResponse entryResponse = experienceByEntryId.get(original.id());
            experiencePlan.add(buildEntryPlan(original, entryResponse, "Experience", contextLabel(original), changes));
        }

        Map<String, ClaudeEntryResponse> projectsByEntryId =
                indexByEntryId(response.projects(), resume.projectEntries(), "projects");
        Map<String, ResumeEntry> projectsById = resume.projectEntries().stream()
                .collect(Collectors.toMap(ResumeEntry::id, e -> e, (a, b) -> a, LinkedHashMap::new));

        List<EntryPlan> projectPlan = new ArrayList<>();
        List<String> keptProjectIdsInResponseOrder = new ArrayList<>();
        for (ClaudeEntryResponse entryResponse : response.projects()) {
            ResumeEntry original = projectsById.get(entryResponse.entryId());
            String label = contextLabel(original);
            if (Boolean.TRUE.equals(entryResponse.dropped())) {
                changes.add(new TailoringChange("Technical Projects", label, null,
                        TailoringChange.ChangeType.ENTRY_DROPPED, null, null, entryResponse.reason(), List.of()));
                continue;
            }
            projectPlan.add(buildEntryPlan(original, entryResponse, "Technical Projects", label, changes));
            keptProjectIdsInResponseOrder.add(original.id());
        }
        if (projectPlan.isEmpty()) {
            throw new TailoringException("Claude's response dropped every project — at least one must remain");
        }

        List<String> originalKeptProjectOrder = resume.projectEntries().stream()
                .map(ResumeEntry::id)
                .filter(keptProjectIdsInResponseOrder::contains)
                .toList();
        if (!originalKeptProjectOrder.equals(keptProjectIdsInResponseOrder)) {
            for (String entryId : keptProjectIdsInResponseOrder) {
                ClaudeEntryResponse entryResponse = projectsByEntryId.get(entryId);
                changes.add(new TailoringChange("Technical Projects", contextLabel(projectsById.get(entryId)), null,
                        TailoringChange.ChangeType.ENTRY_REORDERED, null, null, entryResponse.reason(), List.of()));
            }
        }

        return new TailoringResult(new TailoringPlan(experiencePlan, projectPlan), changes);
    }

    private Map<String, ClaudeEntryResponse> indexByEntryId(
            List<ClaudeEntryResponse> responses, List<ResumeEntry> originals, String sectionName) {
        Map<String, ResumeEntry> originalsById = originals.stream()
                .collect(Collectors.toMap(ResumeEntry::id, e -> e, (a, b) -> a, LinkedHashMap::new));
        Map<String, ClaudeEntryResponse> byId = new LinkedHashMap<>();
        for (ClaudeEntryResponse entryResponse : responses) {
            if (entryResponse.entryId() == null || !originalsById.containsKey(entryResponse.entryId())) {
                throw new TailoringException(
                        "Claude's response referenced an unknown " + sectionName + " entry id: "
                                + entryResponse.entryId());
            }
            if (byId.put(entryResponse.entryId(), entryResponse) != null) {
                throw new TailoringException(
                        "Claude's response listed " + sectionName + " entry " + entryResponse.entryId()
                                + " more than once");
            }
        }
        if (!byId.keySet().equals(originalsById.keySet())) {
            throw new TailoringException("Claude's response did not account for every " + sectionName
                    + " entry (expected " + originalsById.keySet() + ", got " + byId.keySet() + ")");
        }
        return byId;
    }

    private EntryPlan buildEntryPlan(
            ResumeEntry original, ClaudeEntryResponse entryResponse, String sectionName, String label,
            List<TailoringChange> changes) {
        if (entryResponse.bullets() == null) {
            throw new TailoringException("Claude's response for entry " + original.id() + " has no bullets array");
        }
        Map<String, ResumeBullet> originalBulletsById = original.bullets().stream()
                .collect(Collectors.toMap(ResumeBullet::id, b -> b, (a, b) -> a, LinkedHashMap::new));

        Map<String, ClaudeBulletResponse> seen = new LinkedHashMap<>();
        for (ClaudeBulletResponse bulletResponse : entryResponse.bullets()) {
            if (bulletResponse.bulletId() == null || !originalBulletsById.containsKey(bulletResponse.bulletId())) {
                throw new TailoringException("Claude's response referenced an unknown bullet id "
                        + bulletResponse.bulletId() + " for entry " + original.id());
            }
            if (seen.put(bulletResponse.bulletId(), bulletResponse) != null) {
                throw new TailoringException(
                        "Claude's response listed bullet " + bulletResponse.bulletId() + " more than once");
            }
            if (bulletResponse.text() == null || bulletResponse.text().isBlank()) {
                throw new TailoringException("Claude's response has empty text for bullet " + bulletResponse.bulletId());
            }
        }
        if (!seen.keySet().equals(originalBulletsById.keySet())) {
            throw new TailoringException("Claude's response for entry " + original.id()
                    + " did not account for every original bullet (expected " + originalBulletsById.keySet()
                    + ", got " + seen.keySet() + ")");
        }

        List<BulletPlan> kept = new ArrayList<>();
        List<String> keptIdsInResponseOrder = new ArrayList<>();
        for (ClaudeBulletResponse bulletResponse : entryResponse.bullets()) {
            ResumeBullet original_ = originalBulletsById.get(bulletResponse.bulletId());
            if (bulletResponse.dropped()) {
                changes.add(new TailoringChange(sectionName, label, bulletResponse.bulletId(),
                        TailoringChange.ChangeType.DROPPED, original_.text(), null, bulletResponse.reason(),
                        List.of()));
                continue;
            }
            // Claude returned plain text (see toPromptEntry) — re-escape LaTeX special characters
            // before this becomes part of the .tex source, rather than trust Claude to preserve
            // escape sequences it was never shown in the first place.
            String rewordedText = LatexTextExtractor.escapeSpecialCharacters(bulletResponse.text());
            kept.add(new BulletPlan(bulletResponse.bulletId(), rewordedText));
            keptIdsInResponseOrder.add(bulletResponse.bulletId());
            if (!rewordedText.equals(original_.text())) {
                List<String> newNumbers = FabricationHeuristic.newNumbersIn(original_.text(), rewordedText);
                changes.add(new TailoringChange(sectionName, label, bulletResponse.bulletId(),
                        TailoringChange.ChangeType.REWORDED, original_.text(), rewordedText,
                        bulletResponse.reason(), newNumbers));
            }
        }
        if (kept.isEmpty()) {
            throw new TailoringException("Claude's response dropped every bullet for entry " + original.id()
                    + " — at least one must remain");
        }

        List<String> originalKeptOrder = original.bullets().stream()
                .map(ResumeBullet::id)
                .filter(keptIdsInResponseOrder::contains)
                .toList();
        if (!originalKeptOrder.equals(keptIdsInResponseOrder)) {
            for (String bulletId : keptIdsInResponseOrder) {
                boolean alreadyReworded = changes.stream().anyMatch(c ->
                        bulletId.equals(c.bulletId()) && c.type() == TailoringChange.ChangeType.REWORDED);
                if (!alreadyReworded) {
                    ResumeBullet original_ = originalBulletsById.get(bulletId);
                    changes.add(new TailoringChange(sectionName, label, bulletId,
                            TailoringChange.ChangeType.REORDERED, original_.text(), original_.text(),
                            seen.get(bulletId).reason(), List.of()));
                }
            }
        }

        return new EntryPlan(original.id(), kept);
    }

    private record PromptPosting(String title, String company, String description) {
    }

    private record PromptBullet(String bulletId, String text) {
    }

    private record PromptEntry(String entryId, String context, List<PromptBullet> bullets) {
    }

    private record PromptPayload(PromptPosting posting, List<PromptEntry> experience, List<PromptEntry> projects) {
    }

    record ClaudeBulletResponse(String bulletId, String text, boolean dropped, String reason) {
    }

    record ClaudeEntryResponse(
            String entryId, List<ClaudeBulletResponse> bullets, Boolean dropped, String reason) {
    }

    record ClaudeTailorResponse(List<ClaudeEntryResponse> experience, List<ClaudeEntryResponse> projects) {
    }
}
