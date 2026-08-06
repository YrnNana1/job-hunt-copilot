package com.jobhuntcopilot.eligibility;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearanceFilterTest {

    @Test
    void excludesAnActiveClearanceRequirement() {
        Optional<String> result = ClearanceFilter.activeClearanceRequirement(
                "Candidates must possess an active Secret/Top Secret clearance.");

        assertTrue(result.isPresent());
    }

    @Test
    void excludesActiveTsSciRequirement() {
        Optional<String> result = ClearanceFilter.activeClearanceRequirement(
                "This role requires an active TS/SCI clearance to start.");

        assertTrue(result.isPresent());
    }

    @Test
    void allowsEligibleToObtainLanguage() {
        Optional<String> result = ClearanceFilter.activeClearanceRequirement(
                "Must be able to obtain a Secret clearance upon hire.");

        assertTrue(result.isEmpty());
    }

    @Test
    void allowsEligibleForLanguageThatMentionsObtaining() {
        Optional<String> result = ClearanceFilter.activeClearanceRequirement(
                "Candidates should be eligible to obtain a government security clearance.");

        assertTrue(result.isEmpty());
    }

    @Test
    void postingsWithNoClearanceMentionAreNotExcluded() {
        Optional<String> result = ClearanceFilter.activeClearanceRequirement(
                "Great opportunity for a recent graduate with a Security+ certification.");

        assertTrue(result.isEmpty());
    }

    @Test
    void mixedDescriptionExcludesOnlyBecauseOfTheActiveSentence() {
        String description = "We offer clearance sponsorship as a benefit.\n"
                + "Candidates must currently hold an active Secret clearance.";

        Optional<String> result = ClearanceFilter.activeClearanceRequirement(description);

        assertTrue(result.isPresent());
        assertTrue(result.get().toLowerCase().contains("active"));
    }

    @Test
    void blankOrMissingDescriptionIsNotExcluded() {
        assertTrue(ClearanceFilter.activeClearanceRequirement(null).isEmpty());
        assertTrue(ClearanceFilter.activeClearanceRequirement("").isEmpty());
    }
}
