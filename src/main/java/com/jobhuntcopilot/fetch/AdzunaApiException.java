package com.jobhuntcopilot.fetch;

/** Unchecked so a single search term failing (bad network, rate limit, etc.) doesn't force every caller to handle it — JobFetchService catches it per-term and keeps going. */
public class AdzunaApiException extends RuntimeException {

    public AdzunaApiException(String message) {
        super(message);
    }

    public AdzunaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
