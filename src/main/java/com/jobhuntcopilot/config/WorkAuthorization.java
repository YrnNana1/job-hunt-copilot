package com.jobhuntcopilot.config;

/** Standard work-authorization/sponsorship questions almost every application form asks. */
public record WorkAuthorization(
        boolean authorizedToWorkInUs, boolean requiresSponsorshipNow, boolean requiresSponsorshipFuture) {
}
