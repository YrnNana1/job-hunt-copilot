package com.jobhuntcopilot.db;

import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TailoredResumeRepositoryTest {

    private TailoredResumeRepository repository;
    private JobRepository jobRepository;
    private Job job;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new TailoredResumeRepository(database);
        jobRepository = new JobRepository(database);

        job = new Job("adzuna", "1", "Solutions Engineer", "Acme Corp", "Remote", true,
                "desc", "https://example.com/1", 90_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());
        jobRepository.save(job);
    }

    @Test
    void findByJobIdIsEmptyBeforeAnythingIsSaved() throws SQLException {
        assertEquals(Optional.empty(), repository.findByJobId(job.getId()));
    }

    @Test
    void savedRecordCanBeReadBackByJobId() throws SQLException {
        repository.save(job.getId(), "\\documentclass{article}", "/tmp/out.pdf", "[]", "claude-opus-4-5");

        Optional<TailoredResumeRepository.TailoredResumeRecord> record = repository.findByJobId(job.getId());

        assertTrue(record.isPresent());
        assertEquals("\\documentclass{article}", record.get().latex());
        assertEquals("/tmp/out.pdf", record.get().pdfPath());
        assertEquals("[]", record.get().changesJson());
        assertEquals("claude-opus-4-5", record.get().model());
    }

    @Test
    void savingTwiceForTheSameJobUpdatesInPlaceRatherThanDuplicating() throws SQLException {
        repository.save(job.getId(), "first version", "/tmp/first.pdf", "[]", "claude-opus-4-5");
        repository.save(job.getId(), "second version", "/tmp/second.pdf", "[]", "claude-opus-4-5");

        Optional<TailoredResumeRepository.TailoredResumeRecord> record = repository.findByJobId(job.getId());

        assertTrue(record.isPresent());
        assertEquals("second version", record.get().latex());
        assertEquals("/tmp/second.pdf", record.get().pdfPath());
    }
}
