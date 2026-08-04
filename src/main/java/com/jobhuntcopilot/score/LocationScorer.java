package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.LocationPreference;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.text.Tokenizer;

import java.util.List;
import java.util.Set;

/**
 * Scores location fit against the preferred-metros list. Not a hard filter —
 * postings elsewhere still score reasonably, just below preferred ones,
 * since relocating elsewhere is on the table.
 *
 * Matching is token-based rather than raw substring: a preferred metro like
 * "VA" must appear as its own token in the location string, not just
 * anywhere inside it — a naive `location.contains("VA")` would wrongly match
 * a listing in "Nevada" (which contains the substring "va").
 */
public class LocationScorer {

    private static final double PREFERRED_SCORE = 1.0;
    private static final double OPEN_TO_RELOCATE_SCORE = 0.4;

    private LocationScorer() {
    }

    public static LocationScoreResult score(Job job, LocationPreference preference) {
        if (job.isRemote() && preference.remoteOk()) {
            return new LocationScoreResult(PREFERRED_SCORE, "Remote — matches remote preference");
        }
        if (job.getLocation() == null) {
            return new LocationScoreResult(OPEN_TO_RELOCATE_SCORE, "Location not listed");
        }
        if (matchesPreferredMetro(job.getLocation(), preference.acceptableMetros())) {
            return new LocationScoreResult(PREFERRED_SCORE, "Matches preferred location: " + job.getLocation());
        }
        return new LocationScoreResult(OPEN_TO_RELOCATE_SCORE, "Not on the preferred list, but open to relocating");
    }

    private static boolean matchesPreferredMetro(String location, List<String> acceptableMetros) {
        Set<String> locationTokens = Tokenizer.tokenize(location);
        for (String metro : acceptableMetros) {
            Set<String> metroTokens = Tokenizer.tokenize(metro);
            if (!metroTokens.isEmpty() && locationTokens.containsAll(metroTokens)) {
                return true;
            }
        }
        return false;
    }
}
