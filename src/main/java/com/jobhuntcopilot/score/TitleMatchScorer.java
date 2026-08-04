package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.SearchTerm;
import com.jobhuntcopilot.text.Tokenizer;

import java.util.List;
import java.util.Set;

/** How closely a posting's title matches one of the configured search terms — the best match across all of them wins. */
public class TitleMatchScorer {

    private TitleMatchScorer() {
    }

    public static double score(String jobTitle, List<SearchTerm> searchTerms) {
        Set<String> titleTokens = Tokenizer.tokenize(jobTitle);
        if (titleTokens.isEmpty()) {
            return 0.0;
        }

        double best = 0.0;
        for (SearchTerm searchTerm : searchTerms) {
            Set<String> termTokens = Tokenizer.tokenize(searchTerm.term());
            if (termTokens.isEmpty()) {
                continue;
            }
            long overlap = termTokens.stream().filter(titleTokens::contains).count();
            best = Math.max(best, overlap / (double) termTokens.size());
        }
        return best;
    }
}
