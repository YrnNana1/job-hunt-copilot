package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.model.Job;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Shared between JobListView's columns and JobDetailView's metadata section. */
public class JobFormatting {

    private JobFormatting() {
    }

    public static String formatSalary(Job job) {
        Double min = job.getSalaryMin();
        Double max = job.getSalaryMax();
        if (min == null && max == null) {
            return "—";
        }
        if (min == null || max == null || min.equals(max)) {
            return "$" + formatThousands(min != null ? min : max);
        }
        return "$" + formatThousands(min) + " - $" + formatThousands(max);
    }

    public static String formatPosted(LocalDate postedDate) {
        long daysOld = ChronoUnit.DAYS.between(postedDate, LocalDate.now());
        if (daysOld <= 0) {
            return "Today";
        }
        return daysOld == 1 ? "1 day ago" : daysOld + " days ago";
    }

    private static String formatThousands(double value) {
        return Math.round(value / 1000) + "k";
    }
}
