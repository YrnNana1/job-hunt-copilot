package com.jobhuntcopilot.tailor;

import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.db.TailoredResumeRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.resume.ResumeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real parse -> assemble -> tectonic-compile -> page-count -> cache pipeline against a
 * fake Claude response (a ClaudeResumeTailor subclass that skips the network call), so this
 * exercises real LaTeX compilation and the caching behavior without needing a live API key.
 */
class ResumeTailoringServiceTest {

    private Database database;
    private TailoredResumeRepository repository;
    private JobRepository jobRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new TailoredResumeRepository(database);
        jobRepository = new JobRepository(database);
    }

    @Test
    void generatesAOnePageTailoredPdfAndCachesIt(@TempDir Path tempDir) throws SQLException {
        AtomicInteger tailorCalls = new AtomicInteger();
        FakeClaudeResumeTailor fakeTailor = new FakeClaudeResumeTailor(tailorCalls);
        ResumeTailoringService service = new ResumeTailoringService(
                Path.of("resources", "base_resume.tex"), tempDir.resolve("out"), fakeTailor,
                new TectonicCompiler(), repository);

        Job job = job();
        jobRepository.save(job);

        TailoredResumeView first = service.tailor(job);

        assertFalse(first.cached());
        assertTrue(first.pdfPath().toFile().exists());
        assertEquals(1, tailorCalls.get());
        assertTrue(first.changes().stream().anyMatch(c -> c.type() == TailoringChange.ChangeType.REWORDED));

        TailoredResumeView second = service.tailor(job);
        assertTrue(second.cached());
        assertEquals(1, tailorCalls.get(), "second call should hit the cache, not call Claude again");
        assertEquals(first.pdfPath(), second.pdfPath());
    }

    private Job job() {
        return new Job("adzuna", "42", "Solutions Engineer", "Acme Corp", "Remote", true,
                "Looking for someone with Agile and DevOps experience.",
                "https://example.com/42", 90_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());
    }

    /** Skips the network call — reworks one real bullet per entry, keeps everything else unchanged. */
    private static class FakeClaudeResumeTailor extends ClaudeResumeTailor {
        private final AtomicInteger callCount;

        FakeClaudeResumeTailor(AtomicInteger callCount) {
            super("test-api-key");
            this.callCount = callCount;
        }

        @Override
        public TailoringResult tailor(Job job, ResumeDocument resume) {
            callCount.incrementAndGet();

            List<EntryPlan> experience = resume.experienceEntries().stream()
                    .map(entry -> new EntryPlan(entry.id(), entry.bullets().stream()
                            .map(b -> new BulletPlan(b.id(), b.equals(entry.bullets().get(0))
                                    ? b.text() + " (Agile)" : b.text()))
                            .toList()))
                    .toList();
            List<EntryPlan> projects = resume.projectEntries().stream()
                    .map(entry -> new EntryPlan(entry.id(), entry.bullets().stream()
                            .map(b -> new BulletPlan(b.id(), b.text()))
                            .toList()))
                    .toList();

            String firstBulletId = experience.get(0).bullets().get(0).bulletId();
            List<TailoringChange> changes = List.of(new TailoringChange("Experience", "fake entry", firstBulletId,
                    TailoringChange.ChangeType.REWORDED, "original", experience.get(0).bullets().get(0).text(),
                    "test reword", List.of()));

            return new TailoringResult(new TailoringPlan(experience, projects), changes);
        }
    }
}
