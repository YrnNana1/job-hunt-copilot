package com.jobhuntcopilot.fetch;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.config.RecencyRule;
import com.jobhuntcopilot.config.SearchTerm;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.eligibility.EligibilityFilter;
import com.jobhuntcopilot.eligibility.EligibilityResult;
import com.jobhuntcopilot.model.Job;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pulls postings for every configured search term, filters out blocklisted
 * companies and stale postings, and hands the rest to JobRepository (which
 * handles dedupe on its own).
 *
 * Two things keep this within Adzuna's free-tier quota (~1,000 calls/month):
 * a single page per term (RESULTS_PER_PAGE results, one HTTP call) and a
 * cooldown that skips a term if it was already fetched recently.
 */
public class JobFetchService {

    private static final String ENDPOINT = "adzuna_search";
    private static final Duration FETCH_COOLDOWN = Duration.ofHours(6);
    private static final int RESULTS_PER_PAGE = 20;

    private final AdzunaSearchClient adzunaClient;
    private final JobRepository jobRepository;
    private final ApiCallRepository apiCallRepository;
    private final EligibilityExclusionRepository eligibilityExclusionRepository;
    private final BlocklistConfig blocklist;
    private final RecencyRule recencyRule;
    private final EligibilityFilter eligibilityFilter;

    public JobFetchService(
            AdzunaSearchClient adzunaClient,
            JobRepository jobRepository,
            ApiCallRepository apiCallRepository,
            EligibilityExclusionRepository eligibilityExclusionRepository,
            BlocklistConfig blocklist,
            RecencyRule recencyRule,
            EligibilityConfig eligibilityConfig) {
        this.adzunaClient = adzunaClient;
        this.jobRepository = jobRepository;
        this.apiCallRepository = apiCallRepository;
        this.eligibilityExclusionRepository = eligibilityExclusionRepository;
        this.blocklist = blocklist;
        this.recencyRule = recencyRule;
        this.eligibilityFilter = new EligibilityFilter(eligibilityConfig);
    }

    public List<FetchSummary> fetchAll(List<SearchTerm> searchTerms) throws SQLException {
        List<FetchSummary> summaries = new ArrayList<>();
        for (SearchTerm searchTerm : searchTerms) {
            summaries.add(fetchOne(searchTerm));
        }
        return summaries;
    }

    private FetchSummary fetchOne(SearchTerm searchTerm) throws SQLException {
        String cooldownKey = searchTerm.term();

        Optional<Instant> lastCall = apiCallRepository.lastCallTime(cooldownKey);
        if (lastCall.isPresent() && Duration.between(lastCall.get(), Instant.now()).compareTo(FETCH_COOLDOWN) < 0) {
            return FetchSummary.skippedCooldown(searchTerm.term());
        }

        apiCallRepository.log(ENDPOINT, cooldownKey);

        AdzunaSearchResponse response;
        try {
            response = adzunaClient.search(searchTerm.term(), recencyRule.maxDaysOld(), RESULTS_PER_PAGE);
        } catch (AdzunaApiException e) {
            return FetchSummary.failed(searchTerm.term(), e.getMessage());
        }

        return saveResults(searchTerm.term(), response);
    }

    private FetchSummary saveResults(String term, AdzunaSearchResponse response) throws SQLException {
        LocalDate cutoff = LocalDate.now().minusDays(recencyRule.maxDaysOld());

        int fetched = 0;
        int inserted = 0;
        int duplicates = 0;
        int blocklistedCount = 0;
        int ineligibleCount = 0;
        int stale = 0;
        int invalid = 0;

        for (AdzunaResult result : response.getResults()) {
            fetched++;

            Job job;
            try {
                job = toJob(result);
            } catch (IllegalArgumentException e) {
                invalid++;
                continue;
            }

            if (isBlocklisted(job.getCompany())) {
                blocklistedCount++;
                continue;
            }
            EligibilityResult eligibility = eligibilityFilter.evaluate(job);
            if (!eligibility.eligible()) {
                eligibilityExclusionRepository.log(job, eligibility);
                ineligibleCount++;
                continue;
            }
            if (job.getPostedDate().isBefore(cutoff)) {
                stale++;
                continue;
            }

            JobRepository.SaveOutcome outcome = jobRepository.save(job);
            if (outcome == JobRepository.SaveOutcome.INSERTED) {
                inserted++;
            } else {
                duplicates++;
            }
        }

        return FetchSummary.fetched(term, fetched, inserted, duplicates, blocklistedCount, ineligibleCount, stale, invalid);
    }

    private boolean isBlocklisted(String company) {
        return blocklist.blockedCompanies().stream().anyMatch(blocked -> blocked.equalsIgnoreCase(company));
    }

    private Job toJob(AdzunaResult result) {
        if (result.getId() == null || result.getTitle() == null || result.getCreated() == null) {
            throw new IllegalArgumentException("Adzuna result missing id/title/created: " + result.getId());
        }

        String company = result.getCompany() != null ? result.getCompany().getDisplayName() : "Unknown";
        String location = result.getLocation() != null ? result.getLocation().getDisplayName() : null;
        boolean remote = location != null && location.toLowerCase().contains("remote");
        LocalDate postedDate = parsePostedDate(result.getCreated());

        return new Job(
                "adzuna",
                result.getId(),
                result.getTitle(),
                company,
                location,
                remote,
                cleanDescription(result.getDescription()),
                result.getRedirectUrl(),
                result.getSalaryMin(),
                result.getSalaryMax(),
                result.getSalaryMin() != null || result.getSalaryMax() != null ? "USD" : null,
                postedDate,
                Instant.now());
    }

    /** Some Adzuna descriptions contain the literal two characters "\n" instead of a real newline — clean that up once here, at the source, rather than in every view/parser that reads Job.description. */
    private static String cleanDescription(String description) {
        return description == null ? null : description.replace("\\n", "\n").trim();
    }

    private LocalDate parsePostedDate(String created) {
        try {
            return Instant.parse(created).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable posted date: " + created, e);
        }
    }
}
