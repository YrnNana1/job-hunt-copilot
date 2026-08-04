package com.jobhuntcopilot.config;

/** Postings older than this are never fetched or displayed. */
public record RecencyRule(int maxDaysOld) {
}
