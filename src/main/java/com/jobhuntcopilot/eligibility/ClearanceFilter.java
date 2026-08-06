package com.jobhuntcopilot.eligibility;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Flags postings that require an already-active security clearance ("must possess an active
 * Secret clearance", "active TS/SCI required"), but not ones that just require being *eligible*
 * to obtain one ("must be able to obtain a clearance") — the latter is normal for entry-level
 * GRC/cybersecurity roles and fine given a Security+ certification.
 *
 * Splits the description into lines/sentences and checks each one individually, since a posting
 * can legitimately contain both kinds of language in different sentences (e.g. a benefits blurb
 * mentioning clearance sponsorship alongside a hard requirement elsewhere).
 */
public class ClearanceFilter {

    private static final Pattern CLEARANCE_MENTION = Pattern.compile(
            "\\b(clearance|ts/sci|top secret)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTIVE_SIGNAL = Pattern.compile(
            "\\b(active|current|currently hold|must possess|must hold)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBTAIN_SIGNAL = Pattern.compile("obtain", Pattern.CASE_INSENSITIVE);

    private ClearanceFilter() {
    }

    /** Returns the matched sentence/line, if any — kept so callers can log why a posting was excluded. */
    public static Optional<String> activeClearanceRequirement(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }

        for (String line : description.split("(?<=[.!?\\n])")) {
            if (!CLEARANCE_MENTION.matcher(line).find()) {
                continue;
            }
            if (OBTAIN_SIGNAL.matcher(line).find()) {
                continue; // "eligible/able to obtain" — allowed, not an active-clearance requirement
            }
            if (ACTIVE_SIGNAL.matcher(line).find()) {
                return Optional.of(line.trim());
            }
        }
        return Optional.empty();
    }
}
