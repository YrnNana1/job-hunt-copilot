package com.jobhuntcopilot.config;

/**
 * Standard EEO gender self-identification categories. Defined for completeness even though unused
 * today — {@code EeoAnswers.genderIdentity} is null when config/profile.json omits it, and any form
 * field that needs it goes through the normal blank-and-flag path rather than being guessed.
 */
public enum GenderIdentity {
    MALE,
    FEMALE,
    NON_BINARY,
    DECLINE_TO_ANSWER
}
