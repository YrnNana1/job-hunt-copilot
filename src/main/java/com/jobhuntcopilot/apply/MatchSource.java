package com.jobhuntcopilot.apply;

/** How a field's value was resolved — surfaced on the review screen so nothing is trusted blindly. */
public enum MatchSource {
    PATTERN,
    CLAUDE,
    UNMATCHED
}
