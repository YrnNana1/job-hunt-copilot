package com.jobhuntcopilot.config;

/** Deserialized form of config/profile.json — real personal data used to fill application forms (Phase 8). Gitignored, never committed. */
public record ProfileConfig(PersonalInfo personal, WorkAuthorization workAuthorization, EeoAnswers eeo) {
}
