package com.jobhuntcopilot.score;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecencyScorerTest {

    @Test
    void postedTodayScoresFull() {
        assertEquals(1.0, RecencyScorer.score(LocalDate.now(), 14), 0.0001);
    }

    @Test
    void postedAtTheCutoffScoresZero() {
        assertEquals(0.0, RecencyScorer.score(LocalDate.now().minusDays(14), 14), 0.0001);
    }

    @Test
    void halfwayToTheCutoffScoresHalf() {
        assertEquals(0.5, RecencyScorer.score(LocalDate.now().minusDays(7), 14), 0.0001);
    }

    @Test
    void beyondTheCutoffClampsToZeroRatherThanGoingNegative() {
        assertEquals(0.0, RecencyScorer.score(LocalDate.now().minusDays(30), 14), 0.0001);
    }
}
