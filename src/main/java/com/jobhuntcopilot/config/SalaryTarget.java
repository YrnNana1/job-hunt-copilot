package com.jobhuntcopilot.config;

/** Target salary range for scoring. min/max are null until a real target is configured. */
public record SalaryTarget(Double min, Double max, String currency) {
}
