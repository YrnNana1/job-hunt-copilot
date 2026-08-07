package com.jobhuntcopilot.config;

/** Contact/identity fields most application forms ask for. */
public record PersonalInfo(
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phone,
        String linkedInUrl,
        String websiteUrl,
        String location) {
}
