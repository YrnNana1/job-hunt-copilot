package com.jobhuntcopilot.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCallRepositoryTest {

    private ApiCallRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        database.initSchema();
        repository = new ApiCallRepository(database);
    }

    @Test
    void lastCallTimeIsEmptyBeforeAnyCallIsLogged() throws SQLException {
        assertEquals(Optional.empty(), repository.lastCallTime("Solutions Engineer"));
    }

    @Test
    void lastCallTimeReflectsTheMostRecentLog() throws SQLException {
        Instant before = Instant.now();

        repository.log("adzuna_search", "Solutions Engineer");

        Optional<Instant> lastCall = repository.lastCallTime("Solutions Engineer");
        assertTrue(lastCall.isPresent());
        assertTrue(!lastCall.get().isBefore(before));
    }

    @Test
    void countCallsSinceOnlyCountsCallsAtOrAfterTheCutoff() throws SQLException {
        repository.log("adzuna_search", "Solutions Engineer");
        repository.log("adzuna_search", "AI Engineer");

        int countFromAnHourAgo = repository.countCallsSince(Instant.now().minus(Duration.ofHours(1)));
        int countFromAnHourFromNow = repository.countCallsSince(Instant.now().plus(Duration.ofHours(1)));

        assertEquals(2, countFromAnHourAgo);
        assertEquals(0, countFromAnHourFromNow);
    }
}
