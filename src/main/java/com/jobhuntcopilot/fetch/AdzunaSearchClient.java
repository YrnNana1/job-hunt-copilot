package com.jobhuntcopilot.fetch;

/**
 * Pulled out as an interface so JobFetchService can be tested against a fake
 * that returns canned results, instead of every test hitting the live
 * Adzuna API (slow, uses quota, and flaky in CI).
 */
public interface AdzunaSearchClient {

    AdzunaSearchResponse search(String what, int maxDaysOld, int resultsPerPage);
}
