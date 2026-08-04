package com.jobhuntcopilot.score;

import com.jobhuntcopilot.model.Job;

/** A posting paired with its current score breakdown — what the list view and pipeline pass around together. */
public record ScoredJob(Job job, ScoreBreakdown breakdown) {
}
