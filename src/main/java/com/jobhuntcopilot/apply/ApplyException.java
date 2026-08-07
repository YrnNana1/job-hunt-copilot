package com.jobhuntcopilot.apply;

/** Thrown when an apply attempt can't proceed — browser launch failure, scan failure, or the Claude API call failing. */
public class ApplyException extends RuntimeException {
    public ApplyException(String message) {
        super(message);
    }

    public ApplyException(String message, Throwable cause) {
        super(message, cause);
    }
}
