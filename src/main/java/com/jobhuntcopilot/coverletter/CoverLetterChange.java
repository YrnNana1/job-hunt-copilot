package com.jobhuntcopilot.coverletter;

import java.util.List;

/**
 * One entry in the diff/summary shown alongside the tailored cover letter PDF. {@code type} and the
 * before/after text are computed programmatically by comparing the tailored plan against the
 * original letter — not trusted from Claude's self-report — so the mechanical facts (what actually
 * changed) are reliable even if Claude's stated {@code reason} is off. {@code suspiciousNewNumbers}
 * flags any numeric token that appears in a reworded paragraph but not in the original, as an extra
 * signal to check before trusting the rewording.
 */
public record CoverLetterChange(
        String paragraphId,
        String heading,
        ChangeType type,
        String originalText,
        String newText,
        String reason,
        List<String> suspiciousNewNumbers) {

    public enum ChangeType {
        REWORDED,
        REORDERED,
        DROPPED
    }
}
