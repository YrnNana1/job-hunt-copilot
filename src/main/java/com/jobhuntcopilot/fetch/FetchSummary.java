package com.jobhuntcopilot.fetch;

/** Per-search-term result of one fetch run, for the console printout and future debugging. */
public record FetchSummary(
        String term,
        Status status,
        int fetched,
        int inserted,
        int duplicates,
        int blocklisted,
        int stale,
        int invalid,
        String errorMessage) {

    public enum Status {
        FETCHED,
        SKIPPED_COOLDOWN,
        FAILED
    }

    public static FetchSummary fetched(
            String term, int fetched, int inserted, int duplicates, int blocklisted, int stale, int invalid) {
        return new FetchSummary(term, Status.FETCHED, fetched, inserted, duplicates, blocklisted, stale, invalid, null);
    }

    public static FetchSummary skippedCooldown(String term) {
        return new FetchSummary(term, Status.SKIPPED_COOLDOWN, 0, 0, 0, 0, 0, 0, null);
    }

    public static FetchSummary failed(String term, String errorMessage) {
        return new FetchSummary(term, Status.FAILED, 0, 0, 0, 0, 0, 0, errorMessage);
    }
}
