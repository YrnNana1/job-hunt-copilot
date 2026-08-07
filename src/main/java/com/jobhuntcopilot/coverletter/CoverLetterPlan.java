package com.jobhuntcopilot.coverletter;

import java.util.List;

/**
 * What to render for a tailored cover letter. {@code openingText}/{@code closingText} are the
 * (possibly reworded) opening/closing paragraphs, always rendered first/last. {@code bodyParagraphs}
 * lists only the KEPT body paragraphs, in the desired display order (dropped ones are simply absent).
 */
public record CoverLetterPlan(String openingText, List<CoverLetterParagraphPlan> bodyParagraphs, String closingText) {
}
