package com.jobhuntcopilot.config;

/**
 * Relative weight of each factor in the 0-100 fit score (see ScoringEngine).
 * keywordMatch covers both resume keyword overlap and job-title-vs-search-term
 * match — they're combined into one "skill match" factor rather than two,
 * since that's how the weighting was specified.
 */
public record ScoringWeights(
        double keywordMatch,
        double salary,
        double recency,
        double locationFit) {
}
