package com.jobhuntcopilot.config;

/**
 * Relative weight of each factor in the 0-100 fit score (Phase 3 implements the
 * formula itself; this is just the configurable input to it).
 */
public record ScoringWeights(
        double keywordMatch,
        double titleMatch,
        double salary,
        double recency,
        double locationFit) {
}
