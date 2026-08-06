package com.jobhuntcopilot.pipeline;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.config.LocationPreference;
import com.jobhuntcopilot.config.RecencyRule;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.config.SalaryTarget;
import com.jobhuntcopilot.config.ScoringConfig;
import com.jobhuntcopilot.config.ScoringWeights;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.fetch.AdzunaSearchClient;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import com.jobhuntcopilot.score.ScoredJob;
import com.jobhuntcopilot.score.ScoringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fetching itself is covered by JobFetchServiceTest — this focuses on the filtering/scoring/dismiss logic that's new here. */
class JobPipelineTest {

    private static final EligibilityConfig NO_ELIGIBILITY_RULES = new EligibilityConfig(List.of(), 99);

    private static final RolesConfig ROLES_CONFIG = new RolesConfig(
            List.of(), new LocationPreference(List.of(), true), new RecencyRule(14),
            new ScoringConfig(new ScoringWeights(0.35, 0.30, 0.20, 0.15), new SalaryTarget(80_000, 85_000, 90_000, "USD")),
            NO_ELIGIBILITY_RULES);

    private Database database;
    private JobRepository jobRepository;
    private ApiCallRepository apiCallRepository;
    private EligibilityExclusionRepository eligibilityExclusionRepository;
    private JobPipeline pipeline;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        jobRepository = new JobRepository(database);
        apiCallRepository = new ApiCallRepository(database);
        eligibilityExclusionRepository = new EligibilityExclusionRepository(database);

        pipeline = buildPipeline(ROLES_CONFIG);
    }

    private JobPipeline buildPipeline(RolesConfig rolesConfig) {
        AdzunaSearchClient neverCalled = (what, maxDaysOld, resultsPerPage) -> {
            throw new UnsupportedOperationException("fetch should not be invoked by these tests");
        };
        BlocklistConfig blocklist = new BlocklistConfig(List.of("Blocked Inc"));
        JobFetchService fetchService = new JobFetchService(
                neverCalled, jobRepository, apiCallRepository, eligibilityExclusionRepository, blocklist,
                rolesConfig.recency(), rolesConfig.eligibility());
        ScoringEngine scoringEngine = new ScoringEngine(Set.of(), rolesConfig);

        // Resume tailoring (Phase 6) isn't exercised by these tests — see ResumeTailoringServiceTest.
        return new JobPipeline(rolesConfig, blocklist, jobRepository, fetchService, apiCallRepository,
                eligibilityExclusionRepository, scoringEngine, null);
    }

    @Test
    void excludesDismissedJobsFromTheLoadedList() throws SQLException {
        Job dismissed = save(job("adzuna", "1", "Solutions Engineer", "Acme Corp"));
        jobRepository.updateStatus(dismissed.getId(), JobStatus.DISMISSED);
        save(job("adzuna", "2", "AI Engineer", "Other Corp"));

        List<ScoredJob> loaded = pipeline.loadScoredJobs();

        assertEquals(1, loaded.size());
        assertEquals("2", loaded.get(0).job().getExternalId());
    }

    @Test
    void excludesBlocklistedCompaniesEvenIfAlreadyStored() throws SQLException {
        save(job("adzuna", "1", "Solutions Engineer", "Blocked Inc"));
        save(job("adzuna", "2", "AI Engineer", "Fine Corp"));

        List<ScoredJob> loaded = pipeline.loadScoredJobs();

        assertEquals(1, loaded.size());
        assertEquals("Fine Corp", loaded.get(0).job().getCompany());
    }

    @Test
    void sortsByScoreDescending() throws SQLException {
        save(jobWithSalary("adzuna", "1", "Low", "Acme Corp", 40_000.0));
        save(jobWithSalary("adzuna", "2", "High", "Acme Corp", 100_000.0));

        List<ScoredJob> loaded = pipeline.loadScoredJobs();

        assertTrue(loaded.get(0).breakdown().totalScore() >= loaded.get(1).breakdown().totalScore());
        assertEquals("High", loaded.get(0).job().getTitle());
    }

    @Test
    void persistsTheComputedScoreBackToTheDatabase() throws SQLException {
        Job saved = save(job("adzuna", "1", "Solutions Engineer", "Acme Corp"));

        List<ScoredJob> loaded = pipeline.loadScoredJobs();

        Job reloaded = jobRepository.findAll().stream().filter(j -> j.getId().equals(saved.getId())).findFirst().orElseThrow();
        assertEquals(loaded.get(0).breakdown().totalScore(), reloaded.getScore().intValue());
    }

    @Test
    void dismissRemovesAJobFromFutureLoads() throws SQLException {
        Job saved = save(job("adzuna", "1", "Solutions Engineer", "Acme Corp"));
        assertEquals(1, pipeline.loadScoredJobs().size());

        pipeline.dismiss(saved);

        assertTrue(pipeline.loadScoredJobs().isEmpty());
        assertFalse(jobRepository.findAll().isEmpty()); // still in the DB, just filtered from the view
    }

    @Test
    void markViewedAdvancesNewToViewed() throws SQLException {
        Job saved = save(job("adzuna", "1", "Solutions Engineer", "Acme Corp"));
        assertEquals(JobStatus.NEW, saved.getStatus());

        pipeline.markViewed(saved);

        Job reloaded = jobRepository.findAll().get(0);
        assertEquals(JobStatus.VIEWED, reloaded.getStatus());
    }

    @Test
    void markViewedDoesNotOverwriteApplied() throws SQLException {
        Job saved = save(job("adzuna", "1", "Solutions Engineer", "Acme Corp"));
        jobRepository.updateStatus(saved.getId(), JobStatus.APPLIED);
        saved.setStatus(JobStatus.APPLIED);

        pipeline.markViewed(saved);

        Job reloaded = jobRepository.findAll().get(0);
        assertEquals(JobStatus.APPLIED, reloaded.getStatus());
    }

    @Test
    void retroactivelyExcludesAnAlreadyStoredPostingThatFailsEligibilityUnderCurrentConfig() throws SQLException {
        // Saved directly via the repository, bypassing JobFetchService's own fetch-time filter —
        // simulates a posting that was already stored before this eligibility rule existed.
        save(job("adzuna", "1", "Senior Solutions Engineer", "Acme Corp"));

        EligibilityConfig excludesSenior = new EligibilityConfig(List.of("Senior"), 99);
        RolesConfig tightenedConfig = new RolesConfig(
                List.of(), new LocationPreference(List.of(), true), new RecencyRule(14),
                ROLES_CONFIG.scoring(), excludesSenior);
        JobPipeline tightenedPipeline = buildPipeline(tightenedConfig);

        List<ScoredJob> loaded = tightenedPipeline.loadScoredJobs();

        assertTrue(loaded.isEmpty());
        List<EligibilityExclusionRepository.ExclusionLogEntry> exclusions = eligibilityExclusionRepository.findAll();
        assertEquals(1, exclusions.size());
        assertEquals("SENIORITY", exclusions.get(0).reason());
    }

    @Test
    void reloadingDoesNotDuplicateTheExclusionLogRow() throws SQLException {
        save(job("adzuna", "1", "Senior Solutions Engineer", "Acme Corp"));

        EligibilityConfig excludesSenior = new EligibilityConfig(List.of("Senior"), 99);
        RolesConfig tightenedConfig = new RolesConfig(
                List.of(), new LocationPreference(List.of(), true), new RecencyRule(14),
                ROLES_CONFIG.scoring(), excludesSenior);
        JobPipeline tightenedPipeline = buildPipeline(tightenedConfig);

        tightenedPipeline.loadScoredJobs();
        tightenedPipeline.loadScoredJobs();

        assertEquals(1, eligibilityExclusionRepository.findAll().size());
    }

    private Job save(Job job) throws SQLException {
        jobRepository.save(job);
        return job;
    }

    private Job job(String source, String externalId, String title, String company) {
        return jobWithSalary(source, externalId, title, company, 90_000.0);
    }

    private Job jobWithSalary(String source, String externalId, String title, String company, double salary) {
        return new Job(source, externalId, title, company, "Remote", true,
                "desc", "https://example.com/" + externalId, salary, salary, "USD", LocalDate.now(), Instant.now());
    }
}
