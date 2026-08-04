package com.jobhuntcopilot.text;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turns free text into a set of lowercase keyword tokens. Used on both sides
 * of a keyword-overlap comparison (resume text and job posting text, or a
 * location string and a preferred-metro entry) so both are normalized the
 * same way.
 */
public class Tokenizer {

    /** Kept low enough to preserve short but meaningful tokens like "c#" or "ai"; stopwords catch the noise. */
    private static final int MIN_TOKEN_LENGTH = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on", "for", "with",
            "at", "by", "from", "is", "are", "was", "were", "be", "been", "being", "as", "it", "its", "this",
            "that", "these", "those", "i", "you", "we", "they", "he", "she", "his", "her", "their", "our",
            "your", "my", "me", "him", "them", "us", "not", "no", "yes", "do", "does", "did", "have", "has",
            "had", "will", "would", "can", "could", "should", "may", "might", "into", "over", "under", "out",
            "up", "down", "about", "than", "so", "such", "also", "more", "most", "other", "some", "all", "any",
            "each", "which", "who", "whom", "what", "when", "where", "why", "how",
            // Resume/job-posting boilerplate: technically real words, but they show up in nearly every
            // resume and posting regardless of actual fit, so they add noise instead of signal.
            "experience", "experienced", "including", "include", "includes", "detailed", "during", "both",
            "skills", "skill", "technical", "quality", "changing", "using", "required", "requires",
            "requirement", "requirements", "years", "year", "real", "time");

    private Tokenizer() {
    }

    /** Lowercases, splits on anything that isn't a letter/digit/+/# (so "c++" and "c#" survive as single tokens), drops stopwords and short noise. */
    public static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String candidate : text.toLowerCase().split("[^a-z0-9+#]+")) {
            if (candidate.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(candidate)) {
                tokens.add(candidate);
            }
        }
        return tokens;
    }
}
