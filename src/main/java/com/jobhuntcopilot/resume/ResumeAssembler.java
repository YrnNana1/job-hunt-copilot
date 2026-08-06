package com.jobhuntcopilot.resume;

import com.jobhuntcopilot.tailor.BulletPlan;
import com.jobhuntcopilot.tailor.EntryPlan;
import com.jobhuntcopilot.tailor.TailoringPlan;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reassembles a tailored .tex source from a ResumeDocument and a TailoringPlan. Everything outside
 * the Experience/Projects bullet text — preamble, macros, header, Education, Certifications,
 * Technical Skills, Leadership, and every entry's header line (title/dates/employer) — is copied
 * through byte-for-byte from the original; only bullet text, bullet order, and (for projects)
 * entry order/inclusion change.
 *
 * ResumeDocument.prefix()/betweenExperienceAndProjects()/suffix() already carry the original
 * `\resumeSubHeadingListStart`/`\resumeSubHeadingListEnd` wrapper tags verbatim (ResumeParser only
 * anchors on the per-entry macros, not the list wrapper) — this class renders just the entries
 * that go inside those wrappers, not the wrappers themselves.
 *
 * Experience entries are always rendered in their original count and order (see
 * ResumeDocument) — only bullets differ. Projects follow the plan's order and only include kept
 * entries; every kept project except the last gets a trailing `\vspace{-18pt}`, matching the
 * spacing convention already used between projects in base_resume.tex.
 */
public final class ResumeAssembler {

    private ResumeAssembler() {
    }

    public static String assemble(ResumeDocument document, TailoringPlan plan) {
        StringBuilder tex = new StringBuilder();
        tex.append(document.prefix());
        tex.append(renderExperience(document.experienceEntries(), plan.experience()));
        tex.append(document.betweenExperienceAndProjects());
        tex.append(renderProjects(document.projectEntries(), plan.projects()));
        tex.append(document.suffix());
        return tex.toString();
    }

    private static String renderExperience(List<ResumeEntry> original, List<EntryPlan> plans) {
        Map<String, EntryPlan> planByEntryId = plans.stream()
                .collect(Collectors.toMap(EntryPlan::entryId, p -> p));

        StringBuilder body = new StringBuilder();
        for (ResumeEntry entry : original) {
            EntryPlan plan = planByEntryId.getOrDefault(entry.id(), unchangedPlan(entry));
            body.append("    ").append(entry.headerLatex()).append('\n');
            body.append(renderBulletList(plan));
            body.append('\n');
        }
        return body.toString();
    }

    private static String renderProjects(List<ResumeEntry> original, List<EntryPlan> plans) {
        Map<String, ResumeEntry> originalById = original.stream()
                .collect(Collectors.toMap(ResumeEntry::id, e -> e));

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < plans.size(); i++) {
            EntryPlan plan = plans.get(i);
            ResumeEntry entry = originalById.get(plan.entryId());
            if (entry == null) {
                throw new IllegalArgumentException("TailoringPlan referenced unknown project entry id: " + plan.entryId());
            }
            body.append("      ").append(entry.headerLatex()).append('\n');
            body.append(renderBulletList(plan));
            body.append('\n');
            if (i < plans.size() - 1) {
                body.append("      \\vspace{-18pt}\n");
            }
        }
        return body.toString();
    }

    private static String renderBulletList(EntryPlan plan) {
        StringBuilder body = new StringBuilder();
        body.append("      \\resumeItemListStart\n");
        for (BulletPlan bullet : plan.bullets()) {
            body.append("        \\resumeItem{").append(bullet.text()).append("}\n");
        }
        body.append("      \\resumeItemListEnd");
        return body.toString();
    }

    private static EntryPlan unchangedPlan(ResumeEntry entry) {
        List<BulletPlan> bullets = entry.bullets().stream()
                .map(b -> new BulletPlan(b.id(), b.text()))
                .toList();
        return new EntryPlan(entry.id(), bullets);
    }
}
