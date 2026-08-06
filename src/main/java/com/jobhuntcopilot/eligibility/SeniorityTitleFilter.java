package com.jobhuntcopilot.eligibility;

import com.jobhuntcopilot.text.Tokenizer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flags postings whose title signals a seniority level above entry-level (Senior, Lead,
 * Manager, VP, etc.).
 *
 * Matching is token-based, same as LocationScorer, and for the same reason: a naive
 * `title.contains("Lead")` would false-positive on "Leadership Development Program" (title
 * contains the substring "lead" inside "Leadership") — tokenizing both sides and requiring an
 * exact token match avoids that. Multi-word keywords like "Head of" tokenize to just {"head"}
 * once the stopword "of" is stripped, which still does the right thing in practice (it's
 * looking for the word "head" as a standalone token, which is what "Head of Engineering"-style
 * titles have).
 */
public class SeniorityTitleFilter {

    private SeniorityTitleFilter() {
    }

    /** Returns the excluded keyword that matched, if any — kept so callers can log why a posting was excluded. */
    public static Optional<String> matchedKeyword(String title, List<String> excludedTitleKeywords) {
        Set<String> titleTokens = Tokenizer.tokenize(title);
        for (String keyword : excludedTitleKeywords) {
            Set<String> keywordTokens = Tokenizer.tokenize(keyword);
            if (!keywordTokens.isEmpty() && titleTokens.containsAll(keywordTokens)) {
                return Optional.of(keyword);
            }
        }
        return Optional.empty();
    }
}
