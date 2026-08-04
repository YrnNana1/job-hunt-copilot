package com.jobhuntcopilot.score;

import java.util.Set;

/** matchedKeywords is kept alongside the score so the breakdown can show exactly which resume terms matched. */
public record KeywordMatchResult(double score, Set<String> matchedKeywords) {
}
