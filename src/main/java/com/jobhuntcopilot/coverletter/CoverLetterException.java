package com.jobhuntcopilot.coverletter;

/** Thrown when Claude's cover letter response fails validation, or the Claude API call itself fails. */
public class CoverLetterException extends RuntimeException {
    public CoverLetterException(String message) {
        super(message);
    }

    public CoverLetterException(String message, Throwable cause) {
        super(message, cause);
    }
}
