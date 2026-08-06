package com.jobhuntcopilot.tailor;

import java.util.List;

/**
 * One entry in the diff/summary shown alongside the tailored PDF. {@code type} and the before/after
 * text are computed programmatically by comparing the tailored plan against the original resume —
 * not trusted from Claude's self-report — so the mechanical facts (what actually changed) are
 * reliable even if Claude's stated {@code reason} is off. {@code suspiciousNewNumbers} flags any
 * numeric token (count, percentage, dollar amount) that appears in a reworded bullet but not in the
 * original, as an extra signal to check before trusting the rewording.
 */
public record TailoringChange(
        String section,
        String entryLabel,
        String bulletId,
        ChangeType type,
        String originalText,
        String newText,
        String reason,
        List<String> suspiciousNewNumbers) {

    public enum ChangeType {
        REWORDED,
        REORDERED,
        DROPPED,
        ENTRY_DROPPED,
        ENTRY_REORDERED
    }
}
