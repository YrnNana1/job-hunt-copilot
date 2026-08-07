package com.jobhuntcopilot.coverletter;

/**
 * One paragraph of the base cover letter. {@code heading} is the fixed {@code \textbf{...}} label
 * above a body paragraph (e.g. "Technical Impact and Systems Development") and is null for the
 * opening/closing paragraphs, which have no heading. Only {@code text} is ever sent to Claude or
 * reworded — the heading is fixed, same as resume entry headers in Phase 6.
 */
public record CoverLetterParagraph(String id, String heading, String text) {
}
