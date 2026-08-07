package com.jobhuntcopilot.config;

/**
 * Voluntary EEO self-identification answers. Any field left out of config/profile.json (e.g.
 * {@code genderIdentity} if not configured) deserializes to null here — that's treated as
 * "unconfigured," not guessed, by the field matcher.
 */
public record EeoAnswers(
        DisabilityStatus disabilityStatus, VeteranStatus veteranStatus, RaceEthnicity raceEthnicity,
        GenderIdentity genderIdentity) {
}
