package com.jobhuntcopilot.eligibility;

/** eligible=false means a hard exclude — not eligible for the role, not just a low score. */
public record EligibilityResult(boolean eligible, String reason, String detail) {

    public enum Reason {
        SENIORITY,
        EXPERIENCE,
        CLEARANCE
    }

    public static EligibilityResult allowed() {
        return new EligibilityResult(true, null, null);
    }

    public static EligibilityResult excluded(Reason reason, String detail) {
        return new EligibilityResult(false, reason.name(), detail);
    }
}
