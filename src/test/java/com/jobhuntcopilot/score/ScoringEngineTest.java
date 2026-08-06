package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.config.LocationPreference;
import com.jobhuntcopilot.config.RecencyRule;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.config.SalaryTarget;
import com.jobhuntcopilot.config.ScoringConfig;
import com.jobhuntcopilot.config.ScoringWeights;
import com.jobhuntcopilot.config.SearchTerm;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** End-to-end: hand-computes the expected total from known inputs and checks ScoringEngine matches it exactly. */
class ScoringEngineTest {

    private static final ScoringWeights WEIGHTS = new ScoringWeights(0.35, 0.30, 0.20, 0.15);
    private static final SalaryTarget SALARY_TARGET = new SalaryTarget(80_000, 85_000, 90_000, "USD");
    private static final LocationPreference LOCATION_PREFERENCE = new LocationPreference(List.of("Virginia"), true);
    private static final RecencyRule RECENCY_RULE = new RecencyRule(14);
    private static final List<SearchTerm> SEARCH_TERMS = List.of(new SearchTerm("Solutions Engineer", "test"));

    private static final RolesConfig ROLES_CONFIG = new RolesConfig(
            SEARCH_TERMS, LOCATION_PREFERENCE, RECENCY_RULE, new ScoringConfig(WEIGHTS, SALARY_TARGET),
            new EligibilityConfig(List.of(), 99));

    @Test
    void combinesAllFourFactorsUsingTheConfiguredWeights() {
        // Resume knows 5 keywords, all 5 appear in the posting -> keyword overlap = 5/10 = 0.5.
        // Title is an exact match -> title score = 1.0. Skill factor = 0.7*0.5 + 0.3*1.0 = 0.65.
        // Salary ($90k) is at/above targetMin ($85k) -> 1.0. Posted today -> recency 1.0. Remote -> location 1.0.
        // Total = 0.65*35 + 1.0*30 + 1.0*20 + 1.0*15 = 22.75 + 30 + 20 + 15 = 87.75 -> rounds to 88.
        ScoringEngine engine = new ScoringEngine(Set.of("java", "python", "sql", "azure", "git"), ROLES_CONFIG);
        Job job = new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", "Remote", true,
                "Looking for Java, Python, SQL, Azure, and Git experience.", "https://example.com",
                90_000.0, 90_000.0, "USD", LocalDate.now(), Instant.now());

        ScoreBreakdown breakdown = engine.score(job);

        assertEquals(88, breakdown.totalScore());
        assertEquals(4, breakdown.factors().size());
    }

    @Test
    void aPerfectMatchAcrossAllFactorsScoresOneHundred() {
        // FULL_SCORE_MATCH_COUNT is 10, so 10 distinct matched keywords are needed for a perfect skill score.
        Set<String> tenKeywords = Set.of(
                "java", "python", "sql", "azure", "git", "agile", "docker", "kubernetes", "react", "node");
        ScoringEngine engine = new ScoringEngine(tenKeywords, ROLES_CONFIG);
        Job job = new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", "Remote", true,
                String.join(", ", tenKeywords) + " experience required.", "https://example.com",
                100_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());

        assertEquals(100, engine.score(job).totalScore());
    }

    @Test
    void weightsInTheBreakdownMatchConfig() {
        ScoringEngine engine = new ScoringEngine(Set.of(), ROLES_CONFIG);
        Job job = new Job("adzuna", "1", "Unrelated Title", "Acme Corp", "Nowhere", false,
                "desc", "https://example.com", null, null, null, LocalDate.now(), Instant.now());

        ScoreBreakdown breakdown = engine.score(job);

        double weightSum = breakdown.factors().stream().mapToDouble(ScoreFactor::weight).sum();
        assertEquals(1.0, weightSum, 0.0001);
    }
}
