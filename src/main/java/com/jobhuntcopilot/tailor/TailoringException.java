package com.jobhuntcopilot.tailor;

/** Thrown when Claude's tailoring response fails validation, or the Claude API call itself fails. */
public class TailoringException extends RuntimeException {
    public TailoringException(String message) {
        super(message);
    }

    public TailoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
