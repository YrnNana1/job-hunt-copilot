package com.jobhuntcopilot.tailor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricationHeuristicTest {

    @Test
    void flagsANumberThatAppearsOnlyInTheRewordedText() {
        List<String> flagged = FabricationHeuristic.newNumbersIn(
                "Built a classification system using Naive Bayes.",
                "Built a classification system achieving 94% accuracy using Naive Bayes.");

        assertEquals(List.of("94%"), flagged);
    }

    @Test
    void doesNotFlagNumbersAlreadyPresentInTheOriginal() {
        List<String> flagged = FabricationHeuristic.newNumbersIn(
                "Reduced processing time by 40% using batch vectorization.",
                "Cut processing time 40% via optimized batch vectorization.");

        assertTrue(flagged.isEmpty());
    }

    @Test
    void flagsMultipleNewNumbersIndependently() {
        List<String> flagged = FabricationHeuristic.newNumbersIn(
                "Managed broadcast infrastructure for in-person and online attendees.",
                "Managed broadcast infrastructure for 1,000+ in-person and 200+ online attendees.");

        assertEquals(2, flagged.size());
        assertTrue(flagged.contains("1,000+"));
        assertTrue(flagged.contains("200+"));
    }

    @Test
    void returnsEmptyWhenTextIsIdentical() {
        String text = "Coordinated programs for 50+ scholars.";

        assertTrue(FabricationHeuristic.newNumbersIn(text, text).isEmpty());
    }
}
