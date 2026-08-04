package com.jobhuntcopilot.config;

/**
 * Salary scoring is a floor (below this, score very low) plus a target range
 * (at or above targetMin scores highest — targetMax is descriptive, not a
 * ceiling, since more money is never scored worse).
 */
public record SalaryTarget(double minimumAcceptable, double targetMin, double targetMax, String currency) {
}
