package com.jobhuntcopilot.eligibility;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceRequirementParserTest {

    @Test
    void parsesAPlusPattern() {
        OptionalInt result = ExperienceRequirementParser.minRequiredYears("5+ years of experience required.");

        assertEquals(5, result.orElseThrow());
    }

    @Test
    void parsesARangeAndUsesTheLowerBound() {
        OptionalInt result = ExperienceRequirementParser.minRequiredYears("3-5 years of experience in software development.");

        assertEquals(3, result.orElseThrow());
    }

    @Test
    void parsesMinimumOfPhrasing() {
        OptionalInt result = ExperienceRequirementParser.minRequiredYears("Requires a minimum of 4 years' experience.");

        assertEquals(4, result.orElseThrow());
    }

    @Test
    void takesTheLowestOfMultipleMentions() {
        String description = "5+ years of experience with Java is a plus. 1+ years of experience with SQL is required.";

        OptionalInt result = ExperienceRequirementParser.minRequiredYears(description);

        assertEquals(1, result.orElseThrow());
    }

    @Test
    void ignoresYearsMentionsThatArentAboutExperience() {
        // Company history / founding date, not a requirement — no "experience" nearby.
        OptionalInt result = ExperienceRequirementParser.minRequiredYears(
                "Founded 20 years ago, our company has grown into an industry leader.");

        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresCompanyHistoryCopyThatHappensToMentionExperienceBeforehand() {
        // Caught on a real live Adzuna posting (Zions Bancorporation): "experience" appeared
        // a few words *before* an unrelated "150 years" — company-history marketing copy, not
        // a job requirement. Checking only forward from the number fixes this; see the class
        // javadoc for the full story.
        OptionalInt result = ExperienceRequirementParser.minRequiredYears(
                "Committed to providing the best experience possible for over 150 years. "
                        + "Help us transform our workforce of the future.");

        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresAStaffingAgencyBraggingAboutItsOwnYearsOfExperience() {
        // Also caught live (a BC Forward posting): "experience" right after the number this
        // time, so only the "over"/"more than" qualifier check catches it.
        OptionalInt result = ExperienceRequirementParser.minRequiredYears(
                "TSR is a trusted staffing partner with more than 50 years of experience "
                        + "delivering highly qualified talent to support clients' critical initiatives.");

        assertTrue(result.isEmpty());
    }

    @Test
    void stillCatchesARealRequirementThatHappensToUseAtLeastPhrasing() {
        // "at least" is semantically close to "more than" but is how real requirements are
        // phrased ("at least 3 years") — must not get caught by the over/more-than guard.
        OptionalInt result = ExperienceRequirementParser.minRequiredYears(
                "Candidates must have at least 3 years of professional experience.");

        assertEquals(3, result.orElseThrow());
    }

    @Test
    void entryLevelPostingWithNoYearsLanguageIsNotExcluded() {
        OptionalInt result = ExperienceRequirementParser.minRequiredYears(
                "Recent graduates welcome. No prior experience required to apply.");

        assertTrue(result.isEmpty());
    }

    @Test
    void blankOrMissingDescriptionReturnsEmpty() {
        assertTrue(ExperienceRequirementParser.minRequiredYears(null).isEmpty());
        assertTrue(ExperienceRequirementParser.minRequiredYears("").isEmpty());
    }
}
