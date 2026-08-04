package com.jobhuntcopilot;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.ConfigLoader;
import com.jobhuntcopilot.config.EnvLoader;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.fetch.AdzunaClient;
import com.jobhuntcopilot.fetch.FetchSummary;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.resume.ResumeKeywordExtractor;
import com.jobhuntcopilot.score.ScoreBreakdown;
import com.jobhuntcopilot.score.ScoreFactor;
import com.jobhuntcopilot.score.ScoringEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Entry point for Job Hunt Copilot.
 *
 * For now this just proves each phase's pieces are wired together correctly:
 * Phase 0 was the Maven skeleton, Phase 1 added config + the database,
 * Phase 2 fetches real postings from Adzuna, and Phase 3 (this one) scores
 * them. Later phases (the JavaFX GUI) will wire in behind this and
 * eventually replace it as the way the app is launched.
 */
public class Main {

    private static final int DETAILED_BREAKDOWN_COUNT = 5;

    public static void main(String[] args) throws SQLException, IOException {
        System.out.println("Job Hunt Copilot — Phase 3: scoring engine\n");

        RolesConfig roles = ConfigLoader.loadRolesConfig();
        BlocklistConfig blocklist = ConfigLoader.loadBlocklistConfig();

        Database database = new Database(Path.of("data", "jobhunt.db"));
        database.initSchema();

        JobRepository jobRepository = new JobRepository(database);
        ApiCallRepository apiCallRepository = new ApiCallRepository(database);

        AdzunaClient adzunaClient = new AdzunaClient(
                EnvLoader.require("ADZUNA_APP_ID"), EnvLoader.require("ADZUNA_APP_KEY"));

        JobFetchService fetchService = new JobFetchService(
                adzunaClient, jobRepository, apiCallRepository, blocklist, roles.recency());

        System.out.println("Fetching postings for " + roles.searchTerms().size() + " search terms "
                + "(nothing older than " + roles.recency().maxDaysOld() + " days)...\n");

        List<FetchSummary> summaries = fetchService.fetchAll(roles.searchTerms());
        summaries.forEach(Main::printFetchSummary);

        int totalInserted = summaries.stream().mapToInt(FetchSummary::inserted).sum();
        int callsLast24h = apiCallRepository.countCallsSince(Instant.now().minus(Duration.ofDays(1)));
        System.out.println("\nInserted " + totalInserted + " new posting(s) this run.");
        System.out.println("Adzuna calls in the last 24h: " + callsLast24h + " (free tier allows ~33/day).");

        Set<String> resumeKeywords = ResumeKeywordExtractor.extractKeywords(Path.of("resources", "base_resume.tex"));
        ScoringEngine scoringEngine = new ScoringEngine(resumeKeywords, roles);

        List<Job> allJobs = jobRepository.findAll();
        List<ScoredJob> ranked = allJobs.stream()
                .map(job -> new ScoredJob(job, scoringEngine.score(job)))
                .sorted(Comparator.comparingInt((ScoredJob sj) -> sj.breakdown().totalScore()).reversed())
                .toList();

        for (ScoredJob scoredJob : ranked) {
            jobRepository.updateScore(scoredJob.job().getId(), scoredJob.breakdown().totalScore());
        }

        System.out.println("\nScored " + ranked.size() + " total posting(s), ranked by fit:\n");
        for (int i = 0; i < ranked.size(); i++) {
            printRankedRow(i + 1, ranked.get(i));
        }

        System.out.println("\nFull breakdown for the top " + Math.min(DETAILED_BREAKDOWN_COUNT, ranked.size()) + ":");
        ranked.stream().limit(DETAILED_BREAKDOWN_COUNT).forEach(Main::printDetailedBreakdown);
    }

    private record ScoredJob(Job job, ScoreBreakdown breakdown) {
    }

    private static void printFetchSummary(FetchSummary summary) {
        switch (summary.status()) {
            case FETCHED -> System.out.printf(
                    "  %-30s fetched=%-3d inserted=%-3d duplicates=%-3d blocklisted=%-3d stale=%-3d%n",
                    summary.term(), summary.fetched(), summary.inserted(), summary.duplicates(),
                    summary.blocklisted(), summary.stale());
            case SKIPPED_COOLDOWN -> System.out.printf(
                    "  %-30s skipped — fetched within the last 6h%n", summary.term());
            case FAILED -> System.out.printf(
                    "  %-30s FAILED: %s%n", summary.term(), summary.errorMessage());
        }
    }

    private static void printRankedRow(int rank, ScoredJob scoredJob) {
        Job job = scoredJob.job();
        System.out.printf("  %2d. [%3d] %-45s %-25s %s%n",
                rank, scoredJob.breakdown().totalScore(), truncate(job.getTitle(), 45), truncate(job.getCompany(), 25),
                job.getLocation() == null ? "" : job.getLocation());
    }

    private static void printDetailedBreakdown(ScoredJob scoredJob) {
        Job job = scoredJob.job();
        System.out.printf("%n%s — %s (score: %d/100)%n", job.getTitle(), job.getCompany(),
                scoredJob.breakdown().totalScore());
        for (ScoreFactor factor : scoredJob.breakdown().factors()) {
            System.out.printf("  %-20s %5.1f pts (weight %2.0f%%, raw %3.0f%%) — %s%n",
                    factor.name(), factor.points(), factor.weight() * 100, factor.rawScore() * 100,
                    factor.explanation());
        }
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }
}
