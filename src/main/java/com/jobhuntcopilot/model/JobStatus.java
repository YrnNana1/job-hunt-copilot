package com.jobhuntcopilot.model;

/** Where a posting is in my review pipeline. Matches the CHECK constraint on jobs.status in schema.sql. */
public enum JobStatus {
    NEW,
    VIEWED,
    APPLIED,
    DISMISSED;

    /** Lowercase form stored in the database, e.g. "dismissed". */
    public String dbValue() {
        return name().toLowerCase();
    }

    public static JobStatus fromDbValue(String value) {
        return JobStatus.valueOf(value.toUpperCase());
    }
}
