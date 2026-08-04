package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.SearchTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleMatchScorerTest {

    private static final List<SearchTerm> TERMS = List.of(
            new SearchTerm("Solutions Engineer", "test"),
            new SearchTerm("Cybersecurity Analyst GRC", "test"));

    @Test
    void exactMatchScoresOne() {
        assertEquals(1.0, TitleMatchScorer.score("Solutions Engineer", TERMS), 0.0001);
    }

    @Test
    void extraWordsInTitleStillScoreFullMatch() {
        assertEquals(1.0, TitleMatchScorer.score("Senior Solutions Engineer - Enterprise", TERMS), 0.0001);
    }

    @Test
    void partialWordOverlapScoresProportionally() {
        // "Cybersecurity Analyst GRC" is 3 tokens; a title with only "Cybersecurity Analyst" matches 2 of 3.
        assertEquals(2.0 / 3.0, TitleMatchScorer.score("Cybersecurity Analyst", TERMS), 0.0001);
    }

    @Test
    void noOverlapScoresZero() {
        assertEquals(0.0, TitleMatchScorer.score("Marketing Coordinator", TERMS), 0.0001);
    }

    @Test
    void bestMatchAcrossAllTermsWins() {
        assertEquals(1.0, TitleMatchScorer.score("GRC Cybersecurity Analyst", TERMS), 0.0001);
    }
}
