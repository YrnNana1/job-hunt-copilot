package com.jobhuntcopilot.score;

import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordMatcherTest {

    @Test
    void countsDistinctMatchedKeywords() {
        KeywordMatcher matcher = new KeywordMatcher(Set.of("java", "python", "sql", "azure"));
        Job job = job("Java Developer", "Looking for Java and SQL experience, Azure a plus.");

        KeywordMatchResult result = matcher.match(job);

        assertEquals(Set.of("java", "sql", "azure"), result.matchedKeywords());
    }

    @Test
    void scoreScalesLinearlyUpToTheFullScoreCount() {
        // FULL_SCORE_MATCH_COUNT is 10 — 5 distinct matches should score 0.5.
        KeywordMatcher matcher = new KeywordMatcher(Set.of("java", "python", "sql", "azure", "git"));
        Job job = job("Java Developer", "Java, Python, SQL, Azure, Git required.");

        assertEquals(0.5, matcher.match(job).score(), 0.0001);
    }

    @Test
    void noOverlapScoresZeroWithNoMatchedKeywords() {
        KeywordMatcher matcher = new KeywordMatcher(Set.of("java", "python"));
        Job job = job("Marketing Coordinator", "Social media and event planning.");

        KeywordMatchResult result = matcher.match(job);

        assertEquals(0.0, result.score(), 0.0001);
        assertTrue(result.matchedKeywords().isEmpty());
    }

    private Job job(String title, String description) {
        return new Job("adzuna", "1", title, "Acme Corp", "Remote", true,
                description, "https://example.com", 90_000.0, 90_000.0, "USD", LocalDate.now(), Instant.now());
    }
}
