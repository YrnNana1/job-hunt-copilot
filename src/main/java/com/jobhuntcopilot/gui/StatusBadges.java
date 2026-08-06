package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.model.JobStatus;

/** Shared between JobListView's status column and JobDetailView's header so both render the same colors. */
public class StatusBadges {

    private StatusBadges() {
    }

    public static String colorFor(JobStatus status) {
        return switch (status) {
            case NEW -> "#3b82f6";
            case VIEWED -> "#6b7280";
            case APPLIED -> "#16a34a";
            case DISMISSED -> "#dc2626";
        };
    }
}
