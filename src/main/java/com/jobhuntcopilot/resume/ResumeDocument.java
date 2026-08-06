package com.jobhuntcopilot.resume;

import java.util.List;

/**
 * Structural parse of base_resume.tex, split into the two sections a tailored resume is allowed
 * to change (Experience, Technical Projects) and everything else, kept verbatim.
 *
 * Experience entries keep their original count and order always — only their bullets can be
 * reordered/reworded/trimmed. Project entries may additionally be reordered or dropped entirely
 * for one-page fit (see ResumeAssembler) — dropping a real job would misrepresent work history,
 * but trimming an optional project to make room is normal resume tailoring.
 */
public record ResumeDocument(
        String prefix,
        List<ResumeEntry> experienceEntries,
        String betweenExperienceAndProjects,
        List<ResumeEntry> projectEntries,
        String suffix) {
}
