package com.jobhuntcopilot.tailor;

public class TectonicCompilationException extends RuntimeException {
    public TectonicCompilationException(String message) {
        super(message);
    }

    public TectonicCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
