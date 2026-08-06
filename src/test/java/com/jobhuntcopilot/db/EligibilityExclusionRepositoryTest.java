package com.jobhuntcopilot.db;

import com.jobhuntcopilot.eligibility.EligibilityResult;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityExclusionRepositoryTest {

    private EligibilityExclusionRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new EligibilityExclusionRepository(database);
    }

    @Test
    void logsAnExclusionWithItsReasonAndDetail() throws SQLException {
        Job job = job("1", "Senior Solutions Engineer", "Acme Corp");
        EligibilityResult result = EligibilityResult.excluded(
                EligibilityResult.Reason.SENIORITY, "Title contains \"Senior\"");

        repository.log(job, result);

        List<EligibilityExclusionRepository.ExclusionLogEntry> entries = repository.findAll();
        assertEquals(1, entries.size());
        assertEquals("SENIORITY", entries.get(0).reason());
        assertEquals("Title contains \"Senior\"", entries.get(0).detail());
        assertEquals("Senior Solutions Engineer", entries.get(0).title());
    }

    @Test
    void loggingTheSamePostingTwiceDoesNotDuplicateTheRow() throws SQLException {
        Job job = job("1", "Senior Solutions Engineer", "Acme Corp");
        EligibilityResult result = EligibilityResult.excluded(EligibilityResult.Reason.SENIORITY, "detail");

        repository.log(job, result);
        repository.log(job, result);

        assertEquals(1, repository.findAll().size());
    }

    @Test
    void refusesToLogAnEligibleResult() {
        Job job = job("1", "Solutions Engineer", "Acme Corp");

        assertThrows(IllegalArgumentException.class, () -> repository.log(job, EligibilityResult.allowed()));
    }

    @Test
    void findAllReturnsMostRecentFirst() throws SQLException {
        repository.log(job("1", "Senior Engineer", "Acme"), EligibilityResult.excluded(
                EligibilityResult.Reason.SENIORITY, "a"));
        repository.log(job("2", "Lead Engineer", "Acme"), EligibilityResult.excluded(
                EligibilityResult.Reason.SENIORITY, "b"));

        List<EligibilityExclusionRepository.ExclusionLogEntry> entries = repository.findAll();

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).excludedAt().compareTo(entries.get(1).excludedAt()) >= 0);
    }

    private Job job(String externalId, String title, String company) {
        return new Job("adzuna", externalId, title, company, "Remote", true,
                "desc", "https://example.com/" + externalId, 90_000.0, 90_000.0, "USD", LocalDate.now(), Instant.now());
    }
}
