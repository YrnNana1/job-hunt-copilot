package com.jobhuntcopilot.config;

import java.util.List;

/** Deserialized form of config/roles.json — everything that shapes what we search for and how we score it. */
public record RolesConfig(
        List<SearchTerm> searchTerms,
        LocationPreference location,
        RecencyRule recency,
        ScoringConfig scoring,
        EligibilityConfig eligibility) {
}
