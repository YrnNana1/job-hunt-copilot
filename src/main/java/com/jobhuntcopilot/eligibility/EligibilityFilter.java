package com.jobhuntcopilot.eligibility;

import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.model.Job;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Combines the three hard-exclude checks (seniority title, years of experience, active
 * clearance) into one evaluation per posting. Checked in this order mainly so the logged
 * reason is the most obvious one first — a "Senior" title is a simpler thing to explain than a
 * parsed years-of-experience number.
 */
public class EligibilityFilter {

    private final EligibilityConfig config;

    public EligibilityFilter(EligibilityConfig config) {
        this.config = config;
    }

    public EligibilityResult evaluate(Job job) {
        Optional<String> seniorKeyword = SeniorityTitleFilter.matchedKeyword(
                job.getTitle(), config.excludedTitleKeywords());
        if (seniorKeyword.isPresent()) {
            return EligibilityResult.excluded(
                    EligibilityResult.Reason.SENIORITY, "Title contains \"" + seniorKeyword.get() + "\"");
        }

        OptionalInt requiredYears = ExperienceRequirementParser.minRequiredYears(job.getDescription());
        if (requiredYears.isPresent() && requiredYears.getAsInt() > config.maxYearsExperience()) {
            return EligibilityResult.excluded(EligibilityResult.Reason.EXPERIENCE,
                    "Requires " + requiredYears.getAsInt() + "+ years (max " + config.maxYearsExperience() + ")");
        }

        Optional<String> clearanceSnippet = ClearanceFilter.activeClearanceRequirement(job.getDescription());
        if (clearanceSnippet.isPresent()) {
            return EligibilityResult.excluded(EligibilityResult.Reason.CLEARANCE, clearanceSnippet.get());
        }

        return EligibilityResult.allowed();
    }
}
