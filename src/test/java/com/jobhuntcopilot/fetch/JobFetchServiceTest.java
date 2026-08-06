package com.jobhuntcopilot.fetch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.config.RecencyRule;
import com.jobhuntcopilot.config.SearchTerm;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises JobFetchService against a fake AdzunaSearchClient — no live network calls, no
 * quota spent. Response objects are built via Gson (like the real client does) rather than
 * hand-constructing AdzunaResult, so the fixtures look like real API responses.
 */
class JobFetchServiceTest {

    private static final Gson GSON = new Gson();
    private static final RecencyRule FOURTEEN_DAYS = new RecencyRule(14);
    private static final EligibilityConfig NO_ELIGIBILITY_RULES = new EligibilityConfig(List.of(), 99);

    private JobRepository jobRepository;
    private ApiCallRepository apiCallRepository;
    private EligibilityExclusionRepository eligibilityExclusionRepository;
    private FakeAdzunaSearchClient fakeClient;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        jobRepository = new JobRepository(database);
        apiCallRepository = new ApiCallRepository(database);
        eligibilityExclusionRepository = new EligibilityExclusionRepository(database);
        fakeClient = new FakeAdzunaSearchClient();
    }

    @Test
    void filtersBlocklistedAndStaleResultsBeforeSaving() throws SQLException {
        fakeClient.respondWith("Solutions Engineer", buildResponse(
                jobJson("1", "Solutions Engineer", "Acme Corp", "Remote", 1),
                jobJson("2", "Solutions Engineer", "Blocked Inc", "Austin, TX", 1),
                jobJson("3", "Solutions Engineer", "Acme Corp", "Austin, TX", 30)));

        BlocklistConfig blocklist = new BlocklistConfig(List.of("Blocked Inc"));
        JobFetchService service = newService(blocklist);

        List<FetchSummary> summaries = service.fetchAll(List.of(new SearchTerm("Solutions Engineer", "test")));

        FetchSummary summary = summaries.get(0);
        assertEquals(FetchSummary.Status.FETCHED, summary.status());
        assertEquals(3, summary.fetched());
        assertEquals(1, summary.inserted());
        assertEquals(1, summary.blocklisted());
        assertEquals(1, summary.stale());
        assertEquals(1, jobRepository.findAll().size());
    }

    @Test
    void excludesIneligiblePostingsAndLogsWhy() throws SQLException {
        fakeClient.respondWith("Solutions Engineer", buildResponse(
                jobJson("1", "Senior Solutions Engineer", "Acme Corp", "Remote", 1),
                jobJson("2", "Solutions Engineer", "Acme Corp", "Remote", 1)));

        EligibilityConfig eligibility = new EligibilityConfig(List.of("Senior"), 99);
        JobFetchService service = newService(new BlocklistConfig(List.of()), eligibility);

        List<FetchSummary> summaries = service.fetchAll(List.of(new SearchTerm("Solutions Engineer", "test")));

        assertEquals(1, summaries.get(0).ineligible());
        assertEquals(1, summaries.get(0).inserted());
        assertEquals(1, jobRepository.findAll().size());
        assertEquals("Solutions Engineer", jobRepository.findAll().get(0).getTitle());

        var exclusions = eligibilityExclusionRepository.findAll();
        assertEquals(1, exclusions.size());
        assertEquals("SENIORITY", exclusions.get(0).reason());
    }

    @Test
    void cleansLiteralBackslashNSequencesInDescriptions() throws SQLException {
        // Some real Adzuna descriptions contain the literal two characters "\n" instead of an
        // actual newline — caught while reviewing the Phase 5 detail view, where it rendered
        // as visible "\n" text in the middle of the description.
        fakeClient.respondWith("Solutions Engineer", buildResponse(
                jobJson("1", "Solutions Engineer", "Acme Corp", "Remote", 1, "Line one.\\nLine two.")));

        JobFetchService service = newService(new BlocklistConfig(List.of()));
        service.fetchAll(List.of(new SearchTerm("Solutions Engineer", "test")));

        String description = jobRepository.findAll().get(0).getDescription();
        assertEquals("Line one.\nLine two.", description);
    }

    @Test
    void aPostingAlreadyInTheDatabaseIsCountedAsADuplicateNotInserted() throws SQLException {
        Job existing = new Job("adzuna", "42", "AI Engineer", "Acme Corp", "Remote", true,
                "desc", "https://example.com/job/42", 80000.0, 100000.0, "USD", LocalDate.now(), Instant.now());
        jobRepository.save(existing);

        fakeClient.respondWith("AI Engineer", buildResponse(
                jobJson("42", "AI Engineer", "Acme Corp", "Remote", 1)));

        JobFetchService service = newService(new BlocklistConfig(List.of()));
        List<FetchSummary> summaries = service.fetchAll(List.of(new SearchTerm("AI Engineer", "test")));

        assertEquals(1, summaries.get(0).duplicates());
        assertEquals(0, summaries.get(0).inserted());
        assertEquals(1, jobRepository.findAll().size());
    }

    @Test
    void skipsATermFetchedWithinTheCooldownWindow() throws SQLException {
        fakeClient.respondWith("Cybersecurity Analyst GRC", buildResponse(
                jobJson("7", "Cybersecurity Analyst GRC", "Acme Corp", "Remote", 1)));

        JobFetchService service = newService(new BlocklistConfig(List.of()));
        List<SearchTerm> terms = List.of(new SearchTerm("Cybersecurity Analyst GRC", "test"));

        service.fetchAll(terms);
        List<FetchSummary> secondRun = service.fetchAll(terms);

        assertEquals(FetchSummary.Status.SKIPPED_COOLDOWN, secondRun.get(0).status());
    }

    @Test
    void aFailedTermDoesNotStopOtherTermsFromFetching() throws SQLException {
        fakeClient.failWith("Broken Term", new AdzunaApiException("HTTP 401"));
        fakeClient.respondWith("Solutions Engineer", buildResponse(
                jobJson("1", "Solutions Engineer", "Acme Corp", "Remote", 1)));

        JobFetchService service = newService(new BlocklistConfig(List.of()));
        List<FetchSummary> summaries = service.fetchAll(List.of(
                new SearchTerm("Broken Term", "test"), new SearchTerm("Solutions Engineer", "test")));

        assertEquals(FetchSummary.Status.FAILED, summaries.get(0).status());
        assertEquals("HTTP 401", summaries.get(0).errorMessage());
        assertEquals(FetchSummary.Status.FETCHED, summaries.get(1).status());
        assertEquals(1, summaries.get(1).inserted());
    }

    private JobFetchService newService(BlocklistConfig blocklist) {
        return newService(blocklist, NO_ELIGIBILITY_RULES);
    }

    private JobFetchService newService(BlocklistConfig blocklist, EligibilityConfig eligibility) {
        return new JobFetchService(fakeClient, jobRepository, apiCallRepository, eligibilityExclusionRepository,
                blocklist, FOURTEEN_DAYS, eligibility);
    }

    private JsonObject jobJson(String id, String title, String company, String location, int daysAgo) {
        return jobJson(id, title, company, location, daysAgo, "A great job description.");
    }

    private JsonObject jobJson(
            String id, String title, String company, String location, int daysAgo, String description) {
        JsonObject job = new JsonObject();
        job.addProperty("id", id);
        job.addProperty("title", title);
        job.addProperty("description", description);
        job.addProperty("redirect_url", "https://example.com/job/" + id);
        job.addProperty("salary_min", 80000.0);
        job.addProperty("salary_max", 100000.0);
        job.addProperty("created", Instant.now().minus(daysAgo, ChronoUnit.DAYS).toString());

        JsonObject companyJson = new JsonObject();
        companyJson.addProperty("display_name", company);
        job.add("company", companyJson);

        JsonObject locationJson = new JsonObject();
        locationJson.addProperty("display_name", location);
        job.add("location", locationJson);

        return job;
    }

    private AdzunaSearchResponse buildResponse(JsonObject... jobs) {
        JsonArray results = new JsonArray();
        for (JsonObject job : jobs) {
            results.add(job);
        }
        JsonObject response = new JsonObject();
        response.addProperty("count", jobs.length);
        response.add("results", results);
        return GSON.fromJson(response, AdzunaSearchResponse.class);
    }

    private static class FakeAdzunaSearchClient implements AdzunaSearchClient {
        private final Map<String, AdzunaSearchResponse> responses = new HashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();

        void respondWith(String term, AdzunaSearchResponse response) {
            responses.put(term, response);
        }

        void failWith(String term, RuntimeException exception) {
            failures.put(term, exception);
        }

        @Override
        public AdzunaSearchResponse search(String what, int maxDaysOld, int resultsPerPage) {
            if (failures.containsKey(what)) {
                throw failures.get(what);
            }
            AdzunaSearchResponse response = responses.get(what);
            if (response == null) {
                throw new IllegalStateException("No fake response configured for term: " + what);
            }
            return response;
        }
    }
}
