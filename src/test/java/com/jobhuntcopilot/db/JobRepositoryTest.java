package com.jobhuntcopilot.db;

import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses a real (temp file) SQLite database rather than mocking JDBC, so the schema and SQL are actually exercised. */
class JobRepositoryTest {

    private JobRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new JobRepository(database);
    }

    @Test
    void insertsANewJob() throws SQLException {
        Job job = sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", LocalDate.now());

        JobRepository.SaveOutcome outcome = repository.save(job);

        assertEquals(JobRepository.SaveOutcome.INSERTED, outcome);
        assertTrue(job.getId() != null && job.getId() > 0);
        assertEquals(JobStatus.NEW, job.getStatus());
    }

    @Test
    void skipsExactDuplicateFromSameSource() throws SQLException {
        Job first = sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", LocalDate.now());
        Job duplicate = sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", LocalDate.now());

        repository.save(first);
        JobRepository.SaveOutcome outcome = repository.save(duplicate);

        assertEquals(JobRepository.SaveOutcome.DUPLICATE_SKIPPED, outcome);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void skipsSameJobReappearingThroughADifferentSource() throws SQLException {
        LocalDate postedDate = LocalDate.now();
        Job fromAdzuna = sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", postedDate);
        Job fromGreenhouse = sampleJob("greenhouse", "acme-se-42", "Solutions Engineer", "Acme Corp", postedDate);

        repository.save(fromAdzuna);
        JobRepository.SaveOutcome outcome = repository.save(fromGreenhouse);

        assertEquals(JobRepository.SaveOutcome.DUPLICATE_SKIPPED, outcome);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void distinctPostingsAreBothInserted() throws SQLException {
        repository.save(sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", LocalDate.now()));
        repository.save(sampleJob("adzuna", "456", "AI Engineer", "Other Corp", LocalDate.now()));

        List<Job> jobs = repository.findAll();

        assertEquals(2, jobs.size());
    }

    @Test
    void updatesStatus() throws SQLException {
        Job job = sampleJob("adzuna", "123", "Solutions Engineer", "Acme Corp", LocalDate.now());
        repository.save(job);

        repository.updateStatus(job.getId(), JobStatus.DISMISSED);

        Job reloaded = repository.findAll().get(0);
        assertEquals(JobStatus.DISMISSED, reloaded.getStatus());
    }

    private Job sampleJob(String source, String externalId, String title, String company, LocalDate postedDate) {
        return new Job(
                source,
                externalId,
                title,
                company,
                "Remote",
                true,
                "A great job description.",
                "https://example.com/job/" + externalId,
                80000.0,
                100000.0,
                "USD",
                postedDate,
                Instant.now());
    }
}
