package com.jobhuntcopilot.score;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Linear decay from 1.0 (posted today) to 0.0 (at the recency cutoff) — same maxDaysOld the fetcher uses to filter. */
public class RecencyScorer {

    private RecencyScorer() {
    }

    public static double score(LocalDate postedDate, int maxDaysOld) {
        long daysOld = ChronoUnit.DAYS.between(postedDate, LocalDate.now());
        if (daysOld <= 0) {
            return 1.0;
        }
        if (daysOld >= maxDaysOld) {
            return 0.0;
        }
        return 1.0 - (daysOld / (double) maxDaysOld);
    }
}
