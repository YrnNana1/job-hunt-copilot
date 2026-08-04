package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.SalaryTarget;
import com.jobhuntcopilot.model.Job;

/**
 * Scores how well a posting's salary fits the target range, using the
 * midpoint of salaryMin/salaryMax as "the" salary (Adzuna often reports a
 * single predicted figure as both min and max, so midpoint handles that and
 * genuine ranges the same way).
 *
 * Below minimumAcceptable: ramps 0.0 -> 0.3 (deliberately low, but not a hard
 * cliff, so a posting $2k under the floor doesn't score identically to one at
 * $0). Between minimumAcceptable and targetMin: ramps 0.3 -> 1.0. At or above
 * targetMin: 1.0 — "above the target range" is still the best outcome, not
 * penalized for being higher than targetMax.
 */
public class SalaryScorer {

    private static final double MISSING_SALARY_SCORE = 0.5;
    private static final double BELOW_MINIMUM_CEILING = 0.3;

    private SalaryScorer() {
    }

    public static SalaryScoreResult score(Job job, SalaryTarget target) {
        Double min = job.getSalaryMin();
        Double max = job.getSalaryMax();
        if (min == null && max == null) {
            return new SalaryScoreResult(MISSING_SALARY_SCORE, "Salary not listed");
        }
        double effective = min != null && max != null ? (min + max) / 2.0 : (min != null ? min : max);

        if (effective >= target.targetMin()) {
            return new SalaryScoreResult(1.0, "At or above target range ($" + format(effective) + " est.)");
        }
        if (effective >= target.minimumAcceptable()) {
            double fraction = (effective - target.minimumAcceptable())
                    / (target.targetMin() - target.minimumAcceptable());
            double score = BELOW_MINIMUM_CEILING + fraction * (1.0 - BELOW_MINIMUM_CEILING);
            return new SalaryScoreResult(score, "Between minimum acceptable and target ($" + format(effective) + " est.)");
        }
        double fraction = Math.max(0.0, effective / target.minimumAcceptable());
        double score = fraction * BELOW_MINIMUM_CEILING;
        return new SalaryScoreResult(score, "Below minimum acceptable salary of $" + format(target.minimumAcceptable())
                + " ($" + format(effective) + " est.)");
    }

    private static String format(double value) {
        return String.format("%,.0f", value);
    }
}
