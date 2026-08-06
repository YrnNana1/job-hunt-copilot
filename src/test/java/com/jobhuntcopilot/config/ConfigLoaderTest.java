package com.jobhuntcopilot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the real config/roles.json and config/blocklist.json to make sure they parse and match expectations. */
class ConfigLoaderTest {

    @Test
    void loadsRolesConfigWithExpectedSearchTerms() {
        RolesConfig config = ConfigLoader.loadRolesConfig();

        assertEquals(6, config.searchTerms().size());
        assertTrue(config.searchTerms().stream().anyMatch(t -> t.term().equals("Solutions Engineer")));
        assertTrue(config.location().remoteOk());
        assertEquals(14, config.recency().maxDaysOld());
    }

    @Test
    void loadsScoringWeightsThatSumToOne() {
        RolesConfig config = ConfigLoader.loadRolesConfig();
        ScoringWeights weights = config.scoring().weights();

        double total = weights.keywordMatch() + weights.salary() + weights.recency() + weights.locationFit();
        assertEquals(1.0, total, 0.0001);
    }

    @Test
    void loadsSalaryTargetAndPreferredMetros() {
        RolesConfig config = ConfigLoader.loadRolesConfig();

        assertEquals(80000, config.scoring().salaryTarget().minimumAcceptable());
        assertEquals(85000, config.scoring().salaryTarget().targetMin());
        assertTrue(config.location().acceptableMetros().contains("Texas"));
    }

    @Test
    void loadsEligibilityConfig() {
        RolesConfig config = ConfigLoader.loadRolesConfig();
        EligibilityConfig eligibility = config.eligibility();

        assertEquals(2, eligibility.maxYearsExperience());
        assertTrue(eligibility.excludedTitleKeywords().contains("Senior"));
        assertTrue(eligibility.excludedTitleKeywords().contains("Lead"));
    }

    @Test
    void loadsBlocklistConfig() {
        BlocklistConfig config = ConfigLoader.loadBlocklistConfig();

        assertNotNull(config.blockedCompanies());
    }
}
