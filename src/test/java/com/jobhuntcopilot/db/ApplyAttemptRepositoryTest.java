package com.jobhuntcopilot.db;

import com.jobhuntcopilot.apply.AtsType;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyAttemptRepositoryTest {

    private ApplyAttemptRepository repository;
    private JobRepository jobRepository;
    private Job job;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new ApplyAttemptRepository(database);
        jobRepository = new JobRepository(database);

        job = new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", "Remote", true,
                "desc", "https://example.com/1", 90_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());
        jobRepository.save(job);
    }

    @Test
    void findByJobIdIsEmptyBeforeAnythingIsSaved() throws SQLException {
        assertEquals(List.of(), repository.findByJobId(job.getId()));
        assertEquals(Optional.empty(), repository.findLatestByJobId(job.getId()));
    }

    @Test
    void savedAttemptCanBeReadBackByJobId() throws SQLException {
        long attemptId = repository.save(job.getId(), AtsType.GREENHOUSE, "https://boards.greenhouse.io/acme/jobs/1",
                "[]", "PREPARED", Instant.now());

        Optional<ApplyAttemptRepository.ApplyAttemptRecord> record = repository.findLatestByJobId(job.getId());

        assertTrue(record.isPresent());
        assertEquals(attemptId, record.get().id());
        assertEquals(AtsType.GREENHOUSE, record.get().atsType());
        assertEquals("PREPARED", record.get().outcome());
        assertEquals(null, record.get().finishedAt());
    }

    @Test
    void updateOutcomeSetsOutcomeAndFinishedAt() throws SQLException {
        long attemptId = repository.save(job.getId(), AtsType.LEVER, "https://jobs.lever.co/acme/1",
                "[]", "PREPARED", Instant.now());
        Instant finishedAt = Instant.now();

        repository.updateOutcome(attemptId, "SUBMITTED", finishedAt);

        ApplyAttemptRepository.ApplyAttemptRecord record = repository.findLatestByJobId(job.getId()).orElseThrow();
        assertEquals("SUBMITTED", record.outcome());
        assertEquals(finishedAt, record.finishedAt());
    }

    @Test
    void multipleAttemptsForTheSameJobAreAllKeptOrderedMostRecentFirst() throws SQLException {
        repository.save(job.getId(), AtsType.GREENHOUSE, "https://boards.greenhouse.io/acme/jobs/1",
                "[]", "UNSUPPORTED_ATS", Instant.parse("2026-01-01T00:00:00Z"));
        long second = repository.save(job.getId(), AtsType.GREENHOUSE, "https://boards.greenhouse.io/acme/jobs/1",
                "[]", "PREPARED", Instant.parse("2026-01-02T00:00:00Z"));

        List<ApplyAttemptRepository.ApplyAttemptRecord> records = repository.findByJobId(job.getId());

        assertEquals(2, records.size());
        assertEquals(second, records.get(0).id());
    }
}
