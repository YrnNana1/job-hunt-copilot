package com.jobhuntcopilot.config;

import java.util.List;

/** Deserialized form of config/blocklist.json. Postings from these companies are filtered out before scoring. */
public record BlocklistConfig(List<String> blockedCompanies) {
}
