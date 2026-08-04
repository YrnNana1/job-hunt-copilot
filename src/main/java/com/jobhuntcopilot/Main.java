package com.jobhuntcopilot;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.ConfigLoader;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.db.Database;

import java.nio.file.Path;

/**
 * Entry point for Job Hunt Copilot.
 *
 * For now this just proves each phase's pieces are wired together correctly:
 * Phase 0 was the Maven skeleton, Phase 1 loads config and initializes the
 * database. Later phases (job fetching, scoring, the JavaFX GUI) will wire
 * in behind this and eventually replace it as the way the app is launched.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Job Hunt Copilot — Phase 1: config + data layer\n");

        RolesConfig roles = ConfigLoader.loadRolesConfig();
        BlocklistConfig blocklist = ConfigLoader.loadBlocklistConfig();

        System.out.println("Loaded " + roles.searchTerms().size() + " search terms:");
        roles.searchTerms().forEach(t -> System.out.println("  - " + t.term() + " (" + t.reason() + ")"));

        System.out.println("\nLocation preference: remoteOk=" + roles.location().remoteOk()
                + ", acceptableMetros=" + roles.location().acceptableMetros());
        System.out.println("Recency rule: nothing older than " + roles.recency().maxDaysOld() + " days");
        System.out.println("Scoring weights: " + roles.scoring().weights());
        System.out.println("Company blocklist (" + blocklist.blockedCompanies().size() + "): "
                + blocklist.blockedCompanies());

        Database database = new Database(Path.of("data", "jobhunt.db"));
        database.initSchema();
        System.out.println("\nSQLite database ready at data/jobhunt.db");
    }
}
