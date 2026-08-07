package com.jobhuntcopilot.coverletter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reassembles a tailored .tex source from a CoverLetterDocument and a CoverLetterPlan. Everything
 * outside the opening/body/closing paragraph text — preamble, header, salutation, and every body
 * paragraph's heading — is copied through byte-for-byte from the original; only paragraph text,
 * body paragraph order, and body paragraph inclusion change.
 *
 * The opening and closing paragraphs are always rendered first and last (only their text may
 * differ from the original) — the plan has no way to reorder or drop them, so that's enforced by
 * this class's shape rather than by trusting the plan to behave.
 */
public final class CoverLetterAssembler {

    private CoverLetterAssembler() {
    }

    public static String assemble(CoverLetterDocument document, CoverLetterPlan plan) {
        StringBuilder tex = new StringBuilder();
        tex.append(document.prefix());
        tex.append(plan.openingText()).append("\n\n");
        tex.append(renderBodyParagraphs(document.bodyParagraphs(), plan.bodyParagraphs()));
        tex.append(plan.closingText()).append('\n');
        tex.append(document.suffix());
        return tex.toString();
    }

    private static String renderBodyParagraphs(
            List<CoverLetterParagraph> original, List<CoverLetterParagraphPlan> plans) {
        Map<String, CoverLetterParagraph> originalById = original.stream()
                .collect(Collectors.toMap(CoverLetterParagraph::id, p -> p));

        StringBuilder body = new StringBuilder();
        for (CoverLetterParagraphPlan plan : plans) {
            CoverLetterParagraph entry = originalById.get(plan.paragraphId());
            if (entry == null) {
                throw new IllegalArgumentException(
                        "CoverLetterPlan referenced unknown body paragraph id: " + plan.paragraphId());
            }
            body.append("\\textbf{").append(entry.heading()).append("} \\\\\n");
            body.append(plan.text()).append("\n\n");
        }
        return body.toString();
    }
}
