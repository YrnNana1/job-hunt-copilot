package com.jobhuntcopilot.config;

/** One role title to search for, plus a human-readable note on why it's on the list. */
public record SearchTerm(String term, String reason) {
}
