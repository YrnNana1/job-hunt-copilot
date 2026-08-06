package com.jobhuntcopilot.eligibility;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans a posting description for "years of experience" requirements (e.g. "5+ years",
 * "3-5 years of experience", "minimum of 4 years' experience") and returns the lowest such
 * number found — for a range like "3-5 years", 3 is the actual bar to clear, not 5.
 *
 * This is regex pattern-matching over free text, not real NLP, so it has real limits: it
 * requires the word "experience" (or "exp.") shortly *after* the number — not just nearby in
 * either direction — specifically because a live run against real Adzuna postings caught a
 * false positive from checking both directions: "...providing the best experience possible for
 * over 150 years. Help us..." is company-history marketing copy, not a job requirement, but
 * "experience" appeared a few words *before* "150 years" and would've wrongly excluded a
 * perfectly good entry-level GRC posting. Checking only forward from the number avoids that
 * without losing real phrasings like "5+ years of experience", at the cost of not catching the
 * rarer "Experience required: 5+ years" ordering — a missed exclusion is a much smaller problem
 * than a wrongly hidden posting. Even with that fix, this is a heuristic, not a guarantee — see
 * EligibilityExclusionRepository, which exists specifically so exclusions here can be
 * spot-checked and the config tuned if this still mis-fires on a real posting.
 *
 * If multiple "N years ... experience" mentions are found, the minimum across all of them is
 * used, since a stray higher number elsewhere (e.g. "5+ years in X is a plus") shouldn't
 * override a lower, primary requirement stated elsewhere in the same posting.
 *
 * A second real false positive turned up live: "TSR is a trusted staffing partner with more
 * than 50 years of experience delivering..." — "experience" right after the number this time,
 * so the forward-only fix above didn't catch it. This one is a staffing agency describing its
 * own history, not a candidate requirement, and "50 years of experience" is locally
 * indistinguishable from a real requirement without understanding who the sentence is about.
 * The tell both false positives share: the number is preceded by "over" or "more than" — how
 * you brag about accumulated history, not how candidate requirements get phrased ("5+ years",
 * "minimum of 4 years"). Excluding matches preceded by that narrow pattern fixes both without
 * rejecting real requirement phrasings (verified against every case in the test file).
 */
public class ExperienceRequirementParser {

    private static final Pattern YEARS_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:(?:-|to)\\s*(\\d+))?\\+?\\s*years?", Pattern.CASE_INSENSITIVE);

    private static final int EXPERIENCE_CONTEXT_WINDOW = 30;
    private static final int APPROXIMATE_QUALIFIER_WINDOW = 15;

    private ExperienceRequirementParser() {
    }

    public static OptionalInt minRequiredYears(String description) {
        if (description == null || description.isBlank()) {
            return OptionalInt.empty();
        }

        Matcher matcher = YEARS_PATTERN.matcher(description);
        int min = Integer.MAX_VALUE;
        boolean found = false;

        while (matcher.find()) {
            if (!mentionsExperienceNearby(description, matcher)) {
                continue;
            }
            if (precededByApproximateQualifier(description, matcher)) {
                continue;
            }
            int lowerBound = Integer.parseInt(matcher.group(1));
            min = Math.min(min, lowerBound);
            found = true;
        }

        return found ? OptionalInt.of(min) : OptionalInt.empty();
    }

    private static boolean mentionsExperienceNearby(String description, Matcher matcher) {
        int windowEnd = Math.min(description.length(), matcher.end() + EXPERIENCE_CONTEXT_WINDOW);
        String context = description.substring(matcher.end(), windowEnd).toLowerCase();
        return context.contains("experience") || context.contains("exp.");
    }

    /** Catches "with over 150 years..." / "more than 50 years..." — accumulated-history bragging, not a requirement. */
    private static boolean precededByApproximateQualifier(String description, Matcher matcher) {
        int windowStart = Math.max(0, matcher.start() - APPROXIMATE_QUALIFIER_WINDOW);
        String preceding = description.substring(windowStart, matcher.start()).trim().toLowerCase();
        return preceding.endsWith("over") || preceding.endsWith("more than");
    }
}
