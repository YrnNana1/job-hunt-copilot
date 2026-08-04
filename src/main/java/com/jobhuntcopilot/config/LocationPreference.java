package com.jobhuntcopilot.config;

import java.util.List;

/** Which metro areas are acceptable, and whether a fully remote posting counts as a fit. */
public record LocationPreference(List<String> acceptableMetros, boolean remoteOk) {
}
