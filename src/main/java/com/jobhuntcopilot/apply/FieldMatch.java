package com.jobhuntcopilot.apply;

/**
 * The resolved answer (or lack of one) for a single {@link FormField}, and everything the review
 * screen needs to show about it. When {@code source} is UNMATCHED, {@code resolvedValue}/
 * {@code resolvedOptionText} are null and {@code flagged} is true — this is the "leave it blank and
 * flag it rather than guess" outcome, and {@link ApplicationFormFiller} skips these entirely.
 */
public record FieldMatch(
        FormField field,
        MatchSource source,
        String resolvedValue,
        String resolvedOptionText,
        boolean flagged,
        String flagReason) {

    public static FieldMatch unmatched(FormField field, String reason) {
        return new FieldMatch(field, MatchSource.UNMATCHED, null, null, true, reason);
    }
}
