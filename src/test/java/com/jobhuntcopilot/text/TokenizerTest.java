package com.jobhuntcopilot.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenizerTest {

    @Test
    void lowercasesAndSplitsOnPunctuation() {
        Set<String> tokens = Tokenizer.tokenize("Java, Python, SQL!");

        assertTrue(tokens.containsAll(Set.of("java", "python", "sql")));
    }

    @Test
    void keepsPlusAndHashSoCPlusPlusAndCSharpSurvive() {
        Set<String> tokens = Tokenizer.tokenize("C/C++, C#, JavaScript");

        assertTrue(tokens.contains("c++"));
        assertTrue(tokens.contains("c#"));
    }

    @Test
    void dropsStopwordsAndShortNoise() {
        Set<String> tokens = Tokenizer.tokenize("Kubernetes with the and for a of");

        assertFalse(tokens.contains("the"));
        assertFalse(tokens.contains("and"));
        assertFalse(tokens.contains("for"));
        assertFalse(tokens.contains("a"));
        assertFalse(tokens.contains("of"));
        assertTrue(tokens.contains("kubernetes"));
    }

    @Test
    void dropsResumeJobPostingBoilerplate() {
        Set<String> tokens = Tokenizer.tokenize("5+ years of experience required, including strong technical skills");

        assertFalse(tokens.contains("experience"));
        assertFalse(tokens.contains("required"));
        assertFalse(tokens.contains("including"));
        assertFalse(tokens.contains("technical"));
        assertFalse(tokens.contains("skills"));
        assertTrue(tokens.contains("strong"));
    }

    @Test
    void blankInputReturnsEmptySet() {
        assertEquals(Set.of(), Tokenizer.tokenize(""));
        assertEquals(Set.of(), Tokenizer.tokenize(null));
    }
}
