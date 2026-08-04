package com.jobhuntcopilot.score;

import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.text.Tokenizer;

import java.util.Set;
import java.util.TreeSet;

/**
 * Scores how much of the resume's keyword vocabulary shows up in a posting's
 * title + description.
 *
 * The score is based on the absolute count of distinct matches, not a ratio
 * against the posting's word count — a longer, more detailed posting
 * shouldn't score lower just because it also contains more filler words.
 * FULL_SCORE_MATCH_COUNT is a judgment call, not derived from anything: 10
 * distinct resume keywords appearing in a single posting is already a very
 * strong overlap for a one-page resume.
 */
public class KeywordMatcher {

    private static final int FULL_SCORE_MATCH_COUNT = 10;

    private final Set<String> resumeKeywords;

    public KeywordMatcher(Set<String> resumeKeywords) {
        this.resumeKeywords = resumeKeywords;
    }

    public KeywordMatchResult match(Job job) {
        String jobText = job.getTitle() + " " + (job.getDescription() == null ? "" : job.getDescription());
        Set<String> jobTokens = Tokenizer.tokenize(jobText);

        Set<String> matched = new TreeSet<>();
        for (String token : jobTokens) {
            if (resumeKeywords.contains(token)) {
                matched.add(token);
            }
        }

        double score = Math.min(1.0, matched.size() / (double) FULL_SCORE_MATCH_COUNT);
        return new KeywordMatchResult(score, matched);
    }
}
