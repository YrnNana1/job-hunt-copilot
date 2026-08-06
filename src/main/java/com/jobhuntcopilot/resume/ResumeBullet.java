package com.jobhuntcopilot.resume;

/** One `\resumeItem{...}` bullet, keyed by a stable id (e.g. "exp1-b2") used to reference it in a TailoringPlan. */
public record ResumeBullet(String id, String text) {
}
