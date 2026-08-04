package com.jobhuntcopilot.score;

import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.config.ScoringWeights;
import com.jobhuntcopilot.model.Job;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Combines the individual scorers into the 0-100 fit score, using the
 * weights from config/roles.json. Each posting's ScoreBreakdown lists every
 * factor's raw score, weight, point contribution, and a plain-language
 * reason, so the total is never just an opaque number.
 */
public class ScoringEngine {

    /**
     * Skill match = resume keyword overlap + title-vs-search-term match, combined into one
     * weighted factor rather than two (see config/roles.json's 4-factor split). Keyword
     * overlap gets more weight here since it's the richer signal — it considers the whole
     * job description, not just the title.
     */
    private static final double KEYWORD_OVERLAP_SHARE = 0.7;
    private static final double TITLE_MATCH_SHARE = 0.3;

    private final KeywordMatcher keywordMatcher;
    private final RolesConfig rolesConfig;

    public ScoringEngine(Set<String> resumeKeywords, RolesConfig rolesConfig) {
        this.keywordMatcher = new KeywordMatcher(resumeKeywords);
        this.rolesConfig = rolesConfig;
    }

    public ScoreBreakdown score(Job job) {
        ScoringWeights weights = rolesConfig.scoring().weights();

        KeywordMatchResult keywordResult = keywordMatcher.match(job);
        double titleScore = TitleMatchScorer.score(job.getTitle(), rolesConfig.searchTerms());
        double skillScore = KEYWORD_OVERLAP_SHARE * keywordResult.score() + TITLE_MATCH_SHARE * titleScore;

        SalaryScoreResult salaryResult = SalaryScorer.score(job, rolesConfig.scoring().salaryTarget());
        LocationScoreResult locationResult = LocationScorer.score(job, rolesConfig.location());
        double recencyScore = RecencyScorer.score(job.getPostedDate(), rolesConfig.recency().maxDaysOld());

        List<ScoreFactor> factors = List.of(
                factor("Skill/keyword match", skillScore, weights.keywordMatch(),
                        skillMatchExplanation(keywordResult, titleScore)),
                factor("Salary fit", salaryResult.score(), weights.salary(), salaryResult.note()),
                factor("Recency", recencyScore, weights.recency(), recencyExplanation(job.getPostedDate())),
                factor("Location fit", locationResult.score(), weights.locationFit(), locationResult.note()));

        return ScoreBreakdown.of(factors);
    }

    private ScoreFactor factor(String name, double rawScore, double weight, String explanation) {
        double points = rawScore * weight * 100;
        return new ScoreFactor(name, rawScore, weight, points, explanation);
    }

    private String skillMatchExplanation(KeywordMatchResult keywordResult, double titleScore) {
        String keywordPart = keywordResult.matchedKeywords().isEmpty()
                ? "no resume keywords matched"
                : keywordResult.matchedKeywords().size() + " resume keyword(s) matched: "
                        + String.join(", ", keywordResult.matchedKeywords());
        return String.format("%s; title matches search terms %.0f%%", keywordPart, titleScore * 100);
    }

    private String recencyExplanation(LocalDate postedDate) {
        long daysOld = ChronoUnit.DAYS.between(postedDate, LocalDate.now());
        return daysOld <= 0 ? "Posted today" : "Posted " + daysOld + " day(s) ago";
    }
}
