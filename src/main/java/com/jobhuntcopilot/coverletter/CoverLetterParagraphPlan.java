package com.jobhuntcopilot.coverletter;

/** A body paragraph to render — either the original text or a reworded version, keyed to a real paragraph id. */
public record CoverLetterParagraphPlan(String paragraphId, String text) {
}
