package com.jobhuntcopilot.eligibility;

import com.jobhuntcopilot.config.EligibilityConfig;
import com.jobhuntcopilot.model.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityFilterTest {

    private static final EligibilityConfig CONFIG = new EligibilityConfig(
            List.of("Senior", "Lead", "Manager", "Director"), 2);

    private final EligibilityFilter filter = new EligibilityFilter(CONFIG);

    @Test
    void anOrdinaryEntryLevelPostingIsEligible() {
        Job job = job("Solutions Engineer", "Great entry-level opportunity, no experience required.");

        assertTrue(filter.evaluate(job).eligible());
    }

    @Test
    void excludesOnSeniorTitle() {
        Job job = job("Senior Solutions Engineer", "Great opportunity.");

        EligibilityResult result = filter.evaluate(job);

        assertEquals(false, result.eligible());
        assertEquals("SENIORITY", result.reason());
    }

    @Test
    void excludesOnExcessiveExperienceRequirement() {
        Job job = job("Solutions Engineer", "Requires 5+ years of experience in enterprise software.");

        EligibilityResult result = filter.evaluate(job);

        assertEquals(false, result.eligible());
        assertEquals("EXPERIENCE", result.reason());
    }

    @Test
    void doesNotExcludeWhenRequiredExperienceIsAtTheThreshold() {
        Job job = job("Solutions Engineer", "2+ years of experience preferred but not required.");

        assertTrue(filter.evaluate(job).eligible());
    }

    @Test
    void excludesOnActiveClearanceRequirement() {
        Job job = job("Cybersecurity Analyst GRC", "Must possess an active Secret clearance.");

        EligibilityResult result = filter.evaluate(job);

        assertEquals(false, result.eligible());
        assertEquals("CLEARANCE", result.reason());
    }

    @Test
    void doesNotExcludeOnEligibleToObtainClearanceLanguage() {
        Job job = job("Cybersecurity Analyst GRC", "Must be able to obtain a Secret clearance. Security+ preferred.");

        assertTrue(filter.evaluate(job).eligible());
    }

    @Test
    void seniorityIsCheckedBeforeExperienceOrClearance() {
        // A posting that fails multiple checks should report the seniority reason, since that's checked first.
        Job job = job("Senior Cybersecurity Analyst",
                "Requires 8+ years of experience and an active TS/SCI clearance.");

        EligibilityResult result = filter.evaluate(job);

        assertEquals("SENIORITY", result.reason());
    }

    private Job job(String title, String description) {
        return new Job("adzuna", "1", title, "Acme Corp", "Remote", true,
                description, "https://example.com", 90_000.0, 90_000.0, "USD", LocalDate.now(), Instant.now());
    }
}
