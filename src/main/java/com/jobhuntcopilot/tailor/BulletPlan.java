package com.jobhuntcopilot.tailor;

/** A bullet to render in the tailored resume — either the original text or a reworded version, keyed to a real bullet id. */
public record BulletPlan(String bulletId, String text) {
}
