package com.jobhuntcopilot.config;

import java.util.List;

/**
 * Hard-exclude rules, not scoring factors — a posting matching any of these isn't "lower fit,"
 * it's something I'm not eligible for, so it never shows up rather than just scoring low.
 * See EligibilityFilter.
 */
public record EligibilityConfig(List<String> excludedTitleKeywords, int maxYearsExperience) {
}
