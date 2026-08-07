package com.jobhuntcopilot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unlike ConfigLoaderTest (which loads the real, committed config/roles.json and blocklist.json
 * directly), config/profile.json is gitignored real personal data and won't exist in a fresh
 * checkout or CI — so this writes its own fixture to a @TempDir and never touches the default path.
 */
class ProfileConfigTest {

    @Test
    void loadsPersonalInfoWorkAuthorizationAndEeoAnswers(@TempDir Path tempDir) throws IOException {
        Path path = writeFixture(tempDir, """
                {
                  "personal": {
                    "fullName": "Jane Example",
                    "firstName": "Jane",
                    "lastName": "Example",
                    "email": "jane@example.com",
                    "phone": "555-5555",
                    "linkedInUrl": "https://linkedin.com/in/jane",
                    "websiteUrl": "https://jane.dev",
                    "location": "Remote, USA"
                  },
                  "workAuthorization": {
                    "authorizedToWorkInUs": true,
                    "requiresSponsorshipNow": false,
                    "requiresSponsorshipFuture": false
                  },
                  "eeo": {
                    "disabilityStatus": "NOT_DISABLED",
                    "veteranStatus": "NOT_VETERAN",
                    "raceEthnicity": "BLACK_OR_AFRICAN_AMERICAN"
                  }
                }
                """);

        ProfileConfig config = ConfigLoader.loadProfileConfig(path);

        assertEquals("Jane Example", config.personal().fullName());
        assertEquals("jane@example.com", config.personal().email());
        assertTrue(config.workAuthorization().authorizedToWorkInUs());
        assertEquals(DisabilityStatus.NOT_DISABLED, config.eeo().disabilityStatus());
        assertEquals(RaceEthnicity.BLACK_OR_AFRICAN_AMERICAN, config.eeo().raceEthnicity());
    }

    @Test
    void omittedGenderIdentityDeserializesToNullRatherThanBeingGuessed(@TempDir Path tempDir) throws IOException {
        Path path = writeFixture(tempDir, """
                {
                  "personal": {
                    "fullName": "Jane Example", "firstName": "Jane", "lastName": "Example",
                    "email": "jane@example.com", "phone": "555-5555",
                    "linkedInUrl": "https://linkedin.com/in/jane", "websiteUrl": "https://jane.dev",
                    "location": "Remote, USA"
                  },
                  "workAuthorization": {"authorizedToWorkInUs": true, "requiresSponsorshipNow": false, "requiresSponsorshipFuture": false},
                  "eeo": {"disabilityStatus": "DECLINE_TO_ANSWER", "veteranStatus": "DECLINE_TO_ANSWER", "raceEthnicity": "DECLINE_TO_ANSWER"}
                }
                """);

        ProfileConfig config = ConfigLoader.loadProfileConfig(path);

        assertNull(config.eeo().genderIdentity());
    }

    private Path writeFixture(Path tempDir, String json) throws IOException {
        Path path = tempDir.resolve("profile.json");
        Files.writeString(path, json);
        return path;
    }
}
