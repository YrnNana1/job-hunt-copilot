package com.jobhuntcopilot.score;

/**
 * One line of the score breakdown: a factor's raw fit (0-1), its configured
 * weight, how many of the 100 total points it contributed, and a
 * human-readable reason — this is what makes the score explainable instead
 * of a single opaque number.
 */
public record ScoreFactor(String name, double rawScore, double weight, double points, String explanation) {
}
