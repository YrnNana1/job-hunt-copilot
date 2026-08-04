package com.jobhuntcopilot.pipeline;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.fetch.FetchSummary;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import com.jobhuntcopilot.score.ScoredJob;
import com.jobhuntcopilot.score.ScoringEngine;

import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

/**
 * The non-UI orchestration behind the GUI: fetching new postings, scoring
 * and persisting what's stored, and dismissing a posting. Kept separate from
 * JobListView so it can be unit tested without JavaFX and reused if a
 * second UI (or Phase 9's CSV export) ever needs the same operations.
 */
public class JobPipeline {

    private final RolesConfig rolesConfig;
    private final BlocklistConfig blocklistConfig;
    private final JobRepository jobRepository;
    private final JobFetchService fetchService;
    private final ApiCallRepository apiCallRepository;
    private final ScoringEngine scoringEngine;

    public JobPipeline(
            RolesConfig rolesConfig,
            BlocklistConfig blocklistConfig,
            JobRepository jobRepository,
            JobFetchService fetchService,
            ApiCallRepository apiCallRepository,
            ScoringEngine scoringEngine) {
        this.rolesConfig = rolesConfig;
        this.blocklistConfig = blocklistConfig;
        this.jobRepository = jobRepository;
        this.fetchService = fetchService;
        this.apiCallRepository = apiCallRepository;
        this.scoringEngine = scoringEngine;
    }

    /**
     * The current board: every non-dismissed, non-blocklisted posting, freshly scored against
     * today's config (not whatever was last persisted) and re-saved so the DB stays current.
     * Blocklist filtering here is a defensive second pass — JobFetchService already filters at
     * fetch time, but this also catches a company added to the blocklist after it was stored.
     */
    public List<ScoredJob> loadScoredJobs() throws SQLException {
        List<ScoredJob> scored = jobRepository.findAll().stream()
                .filter(job -> job.getStatus() != JobStatus.DISMISSED)
                .filter(job -> !isBlocklisted(job.getCompany()))
                .map(job -> new ScoredJob(job, scoringEngine.score(job)))
                .sorted(Comparator.comparingInt((ScoredJob scoredJob) -> scoredJob.breakdown().totalScore())
                        .reversed())
                .toList();

        for (ScoredJob scoredJob : scored) {
            jobRepository.updateScore(scoredJob.job().getId(), scoredJob.breakdown().totalScore());
        }
        return scored;
    }

    /** Runs the actual Adzuna fetch (uses quota, subject to the per-term cooldown) — nothing scored or filtered yet. */
    public List<FetchSummary> fetchNewPostings() throws SQLException {
        return fetchService.fetchAll(rolesConfig.searchTerms());
    }

    public void dismiss(Job job) throws SQLException {
        jobRepository.updateStatus(job.getId(), JobStatus.DISMISSED);
    }

    public int apiCallsInLast24Hours() throws SQLException {
        return apiCallRepository.countCallsSince(Instant.now().minus(Duration.ofDays(1)));
    }

    private boolean isBlocklisted(String company) {
        return blocklistConfig.blockedCompanies().stream().anyMatch(blocked -> blocked.equalsIgnoreCase(company));
    }
}
