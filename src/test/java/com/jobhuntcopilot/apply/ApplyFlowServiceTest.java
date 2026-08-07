package com.jobhuntcopilot.apply;

import com.jobhuntcopilot.config.DisabilityStatus;
import com.jobhuntcopilot.config.EeoAnswers;
import com.jobhuntcopilot.config.PersonalInfo;
import com.jobhuntcopilot.config.ProfileConfig;
import com.jobhuntcopilot.config.RaceEthnicity;
import com.jobhuntcopilot.config.VeteranStatus;
import com.jobhuntcopilot.config.WorkAuthorization;
import com.jobhuntcopilot.db.ApplyAttemptRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriver;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the orchestration logic (detect/scan short-circuit, field resolution, filling, attempt
 * logging, outcome recording) against a fake ApplyFlowService that overrides only launchAndScan —
 * the one Selenium-touching method — and a fake ApplicationFormFiller that records what it was
 * asked to fill instead of touching a browser. Everything else (FieldMatcher, ApplyAttemptRepository,
 * JobRepository) runs for real against a temp SQLite file.
 */
class ApplyFlowServiceTest {

    private Database database;
    private ApplyAttemptRepository applyAttemptRepository;
    private JobRepository jobRepository;
    private RecordingFormFiller filler;

    private final ProfileConfig profile = new ProfileConfig(
            new PersonalInfo("Jane Example", "Jane", "Example", "jane@example.com", "555-5555",
                    "https://linkedin.com/in/jane", "https://jane.dev", "Remote, USA"),
            new WorkAuthorization(true, false, false),
            new EeoAnswers(DisabilityStatus.NOT_DISABLED, VeteranStatus.NOT_VETERAN,
                    RaceEthnicity.BLACK_OR_AFRICAN_AMERICAN, null));

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        applyAttemptRepository = new ApplyAttemptRepository(database);
        jobRepository = new JobRepository(database);
        filler = new RecordingFormFiller();
    }

    @Test
    void resolvesFieldsFillsAndLogsAPreparedAttempt() throws SQLException {
        List<FormField> fields = List.of(
                new FormField("id:email", "Email", FieldType.EMAIL, true, List.of()),
                new FormField("id:mystery", "Favorite algorithm?", FieldType.TEXTAREA, false, List.of()));
        FakeApplyFlowService service = new FakeApplyFlowService(
                profile, filler, applyAttemptRepository, jobRepository, AtsType.GREENHOUSE, fields);

        Job job = job();
        jobRepository.save(job);

        ApplyAttemptView view = service.start(job, Path.of("resume.pdf"), Path.of("cover-letter.pdf"));

        assertEquals(AtsType.GREENHOUSE, view.atsType());
        assertEquals(2, view.matches().size());
        assertTrue(view.matches().stream().anyMatch(m -> m.source() == MatchSource.PATTERN));
        assertTrue(view.matches().stream().anyMatch(m -> m.source() == MatchSource.UNMATCHED));
        assertEquals(1, filler.callCount());
        assertEquals(view.matches(), filler.lastMatches());

        ApplyAttemptRepository.ApplyAttemptRecord record = applyAttemptRepository.findLatestByJobId(job.getId()).orElseThrow();
        assertEquals("PREPARED", record.outcome());
    }

    @Test
    void unsupportedAtsShortCircuitsWithoutFilling() throws SQLException {
        FakeApplyFlowService service = new FakeApplyFlowService(
                profile, filler, applyAttemptRepository, jobRepository, AtsType.UNKNOWN, List.of());

        Job job = job();
        jobRepository.save(job);

        ApplyAttemptView view = service.start(job, null, null);

        assertEquals(AtsType.UNKNOWN, view.atsType());
        assertTrue(view.matches().isEmpty());
        assertEquals(0, filler.callCount());

        ApplyAttemptRepository.ApplyAttemptRecord record = applyAttemptRepository.findLatestByJobId(job.getId()).orElseThrow();
        assertEquals("UNSUPPORTED_ATS", record.outcome());
    }

    @Test
    void recordOutcomeSubmittedUpdatesAttemptAndJobStatus() throws SQLException {
        FakeApplyFlowService service = new FakeApplyFlowService(
                profile, filler, applyAttemptRepository, jobRepository, AtsType.LEVER, List.of());

        Job job = job();
        jobRepository.save(job);
        ApplyAttemptView view = service.start(job, Path.of("resume.pdf"), Path.of("cover-letter.pdf"));

        service.recordOutcome(view.attemptId(), job, true);

        ApplyAttemptRepository.ApplyAttemptRecord record = applyAttemptRepository.findLatestByJobId(job.getId()).orElseThrow();
        assertEquals("SUBMITTED", record.outcome());
        Job reloaded = jobRepository.findAll().stream().filter(j -> j.getId().equals(job.getId())).findFirst().orElseThrow();
        assertEquals(JobStatus.APPLIED, reloaded.getStatus());
    }

    @Test
    void recordOutcomeNotSubmittedDoesNotChangeJobStatus() throws SQLException {
        FakeApplyFlowService service = new FakeApplyFlowService(
                profile, filler, applyAttemptRepository, jobRepository, AtsType.LEVER, List.of());

        Job job = job();
        jobRepository.save(job);
        ApplyAttemptView view = service.start(job, Path.of("resume.pdf"), Path.of("cover-letter.pdf"));

        service.recordOutcome(view.attemptId(), job, false);

        ApplyAttemptRepository.ApplyAttemptRecord record = applyAttemptRepository.findLatestByJobId(job.getId()).orElseThrow();
        assertEquals("NOT_SUBMITTED", record.outcome());
        Job reloaded = jobRepository.findAll().stream().filter(j -> j.getId().equals(job.getId())).findFirst().orElseThrow();
        assertFalse(reloaded.getStatus() == JobStatus.APPLIED);
    }

    private Job job() {
        return new Job("adzuna", "42", "Solutions Engineer", "Acme Corp", "Remote", true,
                "Looking for someone with Agile and DevOps experience.",
                "https://boards.greenhouse.io/acme/jobs/42", 90_000.0, 100_000.0, "USD", LocalDate.now(), Instant.now());
    }

    private static class FakeApplyFlowService extends ApplyFlowService {
        private final AtsType cannedAtsType;
        private final List<FormField> cannedFields;

        FakeApplyFlowService(
                ProfileConfig profile, ApplicationFormFiller filler, ApplyAttemptRepository applyAttemptRepository,
                JobRepository jobRepository, AtsType cannedAtsType, List<FormField> cannedFields) {
            super(profile, new FieldMatcher(), new FakeClaudeFieldInterpreter(), filler,
                    applyAttemptRepository, jobRepository);
            this.cannedAtsType = cannedAtsType;
            this.cannedFields = cannedFields;
        }

        @Override
        AtsScanResult launchAndScan(String url) {
            return new AtsScanResult(null, cannedAtsType, cannedFields);
        }
    }

    /** Avoids a real Claude API call from the FieldMatcher-unmatched fallback path during this orchestration test. */
    private static class FakeClaudeFieldInterpreter extends ClaudeFieldInterpreter {
        FakeClaudeFieldInterpreter() {
            super("test-api-key");
        }

        @Override
        public FieldMatch interpret(FormField field, ProfileConfig profile, Path resumePdfPath, Path coverLetterPdfPath) {
            return FieldMatch.unmatched(field, "test stub — Claude not called");
        }
    }

    private static class RecordingFormFiller extends ApplicationFormFiller {
        private int callCount;
        private List<FieldMatch> lastMatches;

        @Override
        public void fill(WebDriver driver, List<FieldMatch> matches) {
            callCount++;
            lastMatches = matches;
        }

        int callCount() {
            return callCount;
        }

        List<FieldMatch> lastMatches() {
            return lastMatches;
        }
    }
}
