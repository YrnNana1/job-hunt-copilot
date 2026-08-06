package com.jobhuntcopilot.pipeline;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.eligibility.EligibilityFilter;
import com.jobhuntcopilot.eligibility.EligibilityResult;
import com.jobhuntcopilot.fetch.FetchSummary;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import com.jobhuntcopilot.score.ScoredJob;
import com.jobhuntcopilot.score.ScoringEngine;
import com.jobhuntcopilot.tailor.ResumeTailoringService;
import com.jobhuntcopilot.tailor.TailoredResumeView;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    private final EligibilityExclusionRepository eligibilityExclusionRepository;
    private final ScoringEngine scoringEngine;
    private final EligibilityFilter eligibilityFilter;
    private final ResumeTailoringService resumeTailoringService;

    public JobPipeline(
            RolesConfig rolesConfig,
            BlocklistConfig blocklistConfig,
            JobRepository jobRepository,
            JobFetchService fetchService,
            ApiCallRepository apiCallRepository,
            EligibilityExclusionRepository eligibilityExclusionRepository,
            ScoringEngine scoringEngine,
            ResumeTailoringService resumeTailoringService) {
        this.rolesConfig = rolesConfig;
        this.blocklistConfig = blocklistConfig;
        this.jobRepository = jobRepository;
        this.fetchService = fetchService;
        this.apiCallRepository = apiCallRepository;
        this.eligibilityExclusionRepository = eligibilityExclusionRepository;
        this.scoringEngine = scoringEngine;
        this.eligibilityFilter = new EligibilityFilter(rolesConfig.eligibility());
        this.resumeTailoringService = resumeTailoringService;
    }

    /**
     * The current board: every non-dismissed, non-blocklisted, eligible posting, freshly scored
     * against today's config (not whatever was last persisted) and re-saved so the DB stays
     * current.
     *
     * Blocklist and eligibility filtering here are both defensive second passes — JobFetchService
     * already filters both at fetch time, but this also catches a company added to the blocklist,
     * or an eligibility rule tightened, after a posting was already stored. Any newly-caught
     * eligibility exclusion gets logged the same as a fetch-time one (idempotently — see
     * EligibilityExclusionRepository — so re-loading the list doesn't spam duplicate log rows).
     */
    public List<ScoredJob> loadScoredJobs() throws SQLException {
        List<Job> candidates = jobRepository.findAll().stream()
                .filter(job -> job.getStatus() != JobStatus.DISMISSED)
                .filter(job -> !isBlocklisted(job.getCompany()))
                .toList();

        List<ScoredJob> scored = new ArrayList<>();
        for (Job job : candidates) {
            EligibilityResult eligibility = eligibilityFilter.evaluate(job);
            if (!eligibility.eligible()) {
                eligibilityExclusionRepository.log(job, eligibility);
                continue;
            }
            scored.add(new ScoredJob(job, scoringEngine.score(job)));
        }

        scored.sort(Comparator.comparingInt((ScoredJob scoredJob) -> scoredJob.breakdown().totalScore()).reversed());

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

    /** Called when the detail view opens for a posting — only advances NEW to VIEWED, never overwrites APPLIED/DISMISSED. */
    public void markViewed(Job job) throws SQLException {
        if (job.getStatus() == JobStatus.NEW) {
            jobRepository.updateStatus(job.getId(), JobStatus.VIEWED);
        }
    }

    /**
     * Generates (or returns the cached) tailored resume for this posting — only called when the
     * detail view's "Tailor Resume" button is clicked, never for every fetched posting, since it
     * costs a real Claude API call and a LaTeX compile.
     */
    public TailoredResumeView tailorResume(Job job) throws SQLException {
        return resumeTailoringService.tailor(job);
    }

    public int apiCallsInLast24Hours() throws SQLException {
        return apiCallRepository.countCallsSince(Instant.now().minus(Duration.ofDays(1)));
    }

    private boolean isBlocklisted(String company) {
        return blocklistConfig.blockedCompanies().stream().anyMatch(blocked -> blocked.equalsIgnoreCase(company));
    }
}
