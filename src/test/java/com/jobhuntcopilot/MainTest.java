package com.jobhuntcopilot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Placeholder test to prove the test toolchain (JUnit 5 + Surefire) is wired
 * up correctly. Real tests start in Phase 3 with the scoring engine.
 */
class MainTest {

    @Test
    void mainRunsWithoutThrowing() {
        assertDoesNotThrow(() -> Main.main(new String[] {}));
    }
}
