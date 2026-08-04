package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.SalaryTarget;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalaryScorerTest {

    private static final SalaryTarget TARGET = new SalaryTarget(80_000, 85_000, 90_000, "USD");

    @Test
    void atOrAboveTargetMinScoresFull() {
        assertEquals(1.0, SalaryScorer.score(job(90_000.0, 90_000.0), TARGET).score(), 0.0001);
        assertEquals(1.0, SalaryScorer.score(job(85_000.0, 85_000.0), TARGET).score(), 0.0001);
        assertEquals(1.0, SalaryScorer.score(job(100_000.0, 110_000.0), TARGET).score(), 0.0001);
    }

    @Test
    void betweenMinimumAndTargetRampsFromPointThreeToOne() {
        // Midpoint of the 80k-85k band: fraction 0.5 -> 0.3 + 0.5 * 0.7 = 0.65
        assertEquals(0.65, SalaryScorer.score(job(82_500.0, 82_500.0), TARGET).score(), 0.0001);
    }

    @Test
    void belowMinimumIsVeryLowButNotAHardZero() {
        SalaryScoreResult result = SalaryScorer.score(job(40_000.0, 40_000.0), TARGET);

        assertEquals(0.15, result.score(), 0.0001);
        assertTrue(result.note().contains("Below minimum"));
    }

    @Test
    void missingSalaryScoresNeutral() {
        SalaryScoreResult result = SalaryScorer.score(job(null, null), TARGET);

        assertEquals(0.5, result.score(), 0.0001);
        assertEquals("Salary not listed", result.note());
    }

    @Test
    void usesTheProvidedFigureWhenOnlyOneBoundIsListed() {
        assertEquals(1.0, SalaryScorer.score(job(95_000.0, null), TARGET).score(), 0.0001);
    }

    private Job job(Double salaryMin, Double salaryMax) {
        return new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", "Remote", true,
                "desc", "https://example.com", salaryMin, salaryMax, "USD", LocalDate.now(), Instant.now());
    }
}
