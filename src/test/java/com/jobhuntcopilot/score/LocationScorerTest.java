package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.LocationPreference;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationScorerTest {

    private static final LocationPreference PREFERENCE = new LocationPreference(
            List.of("Virginia", "VA", "North Carolina", "NC", "Atlanta"), true);

    @Test
    void remoteJobScoresFullWhenRemoteIsOk() {
        assertEquals(1.0, LocationScorer.score(job("Remote", true), PREFERENCE).score(), 0.0001);
    }

    @Test
    void abbreviationMatchesAsAStandaloneToken() {
        assertEquals(1.0, LocationScorer.score(job("Reston, VA", false), PREFERENCE).score(), 0.0001);
    }

    @Test
    void multiWordMetroRequiresAllItsTokens() {
        assertEquals(1.0, LocationScorer.score(job("Charlotte, North Carolina", false), PREFERENCE).score(), 0.0001);
    }

    @Test
    void abbreviationDoesNotFalsePositiveInsideAnUnrelatedWord() {
        // "Las Vegas, NV" contains the substring "va" inside "Vegas" — must NOT match the "VA" preference.
        LocationScoreResult result = LocationScorer.score(job("Las Vegas, NV", false), PREFERENCE);

        assertEquals(0.4, result.score(), 0.0001);
    }

    @Test
    void nonPreferredLocationScoresLowerButNotZero() {
        assertEquals(0.4, LocationScorer.score(job("Boise, ID", false), PREFERENCE).score(), 0.0001);
    }

    @Test
    void missingLocationScoresTheOpenToRelocateBaseline() {
        assertEquals(0.4, LocationScorer.score(job(null, false), PREFERENCE).score(), 0.0001);
    }

    private Job job(String location, boolean remote) {
        return new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", location, remote,
                "desc", "https://example.com", 90_000.0, 90_000.0, "USD", LocalDate.now(), Instant.now());
    }
}
