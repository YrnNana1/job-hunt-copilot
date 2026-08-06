package com.jobhuntcopilot.eligibility;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeniorityTitleFilterTest {

    private static final List<String> KEYWORDS = List.of(
            "Senior", "Sr.", "Lead", "Principal", "Staff", "Manager", "Director", "Head of", "VP",
            "Vice President", "Chief", "Executive");

    @Test
    void matchesWholeWordSeniorityTerms() {
        assertTrue(SeniorityTitleFilter.matchedKeyword("Senior Solutions Engineer", KEYWORDS).isPresent());
        assertTrue(SeniorityTitleFilter.matchedKeyword("Lead AI Engineer", KEYWORDS).isPresent());
        assertTrue(SeniorityTitleFilter.matchedKeyword("Director of Engineering", KEYWORDS).isPresent());
        assertTrue(SeniorityTitleFilter.matchedKeyword("VP, Solutions", KEYWORDS).isPresent());
    }

    @Test
    void matchesAbbreviationWithOrWithoutPunctuation() {
        assertTrue(SeniorityTitleFilter.matchedKeyword("Sr Solutions Engineer", KEYWORDS).isPresent());
        assertTrue(SeniorityTitleFilter.matchedKeyword("Sr. Solutions Engineer", KEYWORDS).isPresent());
    }

    @Test
    void doesNotFalsePositiveOnAWordThatMerelyContainsAKeywordAsASubstring() {
        // "Leadership" contains the substring "lead" but tokenizes to a different whole word.
        Optional<String> result = SeniorityTitleFilter.matchedKeyword(
                "Leadership Development Program Associate", KEYWORDS);

        assertTrue(result.isEmpty());
    }

    @Test
    void entryLevelTitlesAreNotFlagged() {
        assertTrue(SeniorityTitleFilter.matchedKeyword("Solutions Engineer", KEYWORDS).isEmpty());
        assertTrue(SeniorityTitleFilter.matchedKeyword("Associate Consultant", KEYWORDS).isEmpty());
        assertTrue(SeniorityTitleFilter.matchedKeyword("AI Engineer", KEYWORDS).isEmpty());
    }

    @Test
    void returnsTheMatchedKeywordForLogging() {
        Optional<String> result = SeniorityTitleFilter.matchedKeyword("Principal Consultant", KEYWORDS);

        assertEquals("Principal", result.orElseThrow());
    }
}
