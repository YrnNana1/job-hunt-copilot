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

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Entry point for Job Hunt Copilot.
 *
 * For now this just proves each phase's pieces are wired together correctly:
 * Phase 0 was the Maven skeleton, Phase 1 added config + the database, and
 * Phase 2 (this one) fetches real postings from Adzuna. Later phases
 * (scoring, the JavaFX GUI) will wire in behind this and eventually replace
 * it as the way the app is launched.
 */
public class Main {

    public static void main(String[] args) throws SQLException {
        System.out.println("Job Hunt Copilot — Phase 2: job fetching\n");

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
        summaries.forEach(Main::printSummary);

        int totalInserted = summaries.stream().mapToInt(FetchSummary::inserted).sum();
        int callsLast24h = apiCallRepository.countCallsSince(Instant.now().minus(Duration.ofDays(1)));
        int totalStored = jobRepository.findAll().size();

        System.out.println("\nInserted " + totalInserted + " new posting(s) this run.");
        System.out.println("Adzuna calls in the last 24h: " + callsLast24h + " (free tier allows ~33/day).");
        System.out.println("Total postings stored in data/jobhunt.db: " + totalStored);
    }

    private static void printSummary(FetchSummary summary) {
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
}
