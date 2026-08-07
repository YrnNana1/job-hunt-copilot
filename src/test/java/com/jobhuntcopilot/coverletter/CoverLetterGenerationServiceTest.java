package com.jobhuntcopilot.coverletter;

import com.jobhuntcopilot.db.CoverLetterRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.tailor.TectonicCompiler;
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
 * fake Claude response (a ClaudeCoverLetterWriter subclass that skips the network call), so this
 * exercises real LaTeX compilation and the caching behavior without needing a live API key. This
 * is the test that would have caught a wrapper-duplication-style bug (see ResumeAssembler's Phase 6
 * history) — string-only assertions can't catch a LaTeX structural break, only a real compile can.
 */
class CoverLetterGenerationServiceTest {

    private Database database;
    private CoverLetterRepository repository;
    private JobRepository jobRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new CoverLetterRepository(database);
        jobRepository = new JobRepository(database);
    }

    @Test
    void generatesAOnePageCoverLetterPdfAndCachesIt(@TempDir Path tempDir) throws SQLException {
        AtomicInteger writeCalls = new AtomicInteger();
        FakeClaudeCoverLetterWriter fakeWriter = new FakeClaudeCoverLetterWriter(writeCalls);
        CoverLetterGenerationService service = new CoverLetterGenerationService(
                Path.of("resources", "base_cover_letter.tex"), tempDir.resolve("out"), fakeWriter,
                new TectonicCompiler(), repository);

        Job job = job();
        jobRepository.save(job);

        CoverLetterView first = service.generate(job);

        assertFalse(first.cached());
        assertTrue(first.pdfPath().toFile().exists());
        assertEquals(1, writeCalls.get());
        assertTrue(first.changes().stream().anyMatch(c -> c.type() == CoverLetterChange.ChangeType.REWORDED));

        CoverLetterView second = service.generate(job);
        assertTrue(second.cached());
        assertEquals(1, writeCalls.get(), "second call should hit the cache, not call Claude again");
        assertEquals(first.pdfPath(), second.pdfPath());
    }

    private Job job() {
        return new Job("adzuna", "42", "Solutions Engineer", "Acme Corp", "Remote", true,
                "Looking for someone with Agile and DevOps experience.",
                "https://example.com/42", 90_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());
    }

    /** Skips the network call — reworks the opening paragraph, keeps everything else unchanged. */
    private static class FakeClaudeCoverLetterWriter extends ClaudeCoverLetterWriter {
        private final AtomicInteger callCount;

        FakeClaudeCoverLetterWriter(AtomicInteger callCount) {
            super("test-api-key");
            this.callCount = callCount;
        }

        @Override
        public CoverLetterResult write(Job job, CoverLetterDocument document) {
            callCount.incrementAndGet();

            String rewordedOpening = document.opening().text() + " (Agile)";
            List<CoverLetterParagraphPlan> bodyPlan = document.bodyParagraphs().stream()
                    .map(p -> new CoverLetterParagraphPlan(p.id(), p.text()))
                    .toList();

            List<CoverLetterChange> changes = List.of(new CoverLetterChange("opening", null,
                    CoverLetterChange.ChangeType.REWORDED, document.opening().text(), rewordedOpening,
                    "test reword", List.of()));

            return new CoverLetterResult(
                    new CoverLetterPlan(rewordedOpening, bodyPlan, document.closing().text()), changes);
        }
    }
}
