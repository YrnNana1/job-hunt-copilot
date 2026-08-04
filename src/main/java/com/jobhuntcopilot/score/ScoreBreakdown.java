package com.jobhuntcopilot.score;

import java.util.List;

public record ScoreBreakdown(int totalScore, List<ScoreFactor> factors) {

    public static ScoreBreakdown of(List<ScoreFactor> factors) {
        double totalPoints = factors.stream().mapToDouble(ScoreFactor::points).sum();
        return new ScoreBreakdown((int) Math.round(totalPoints), factors);
    }
}
