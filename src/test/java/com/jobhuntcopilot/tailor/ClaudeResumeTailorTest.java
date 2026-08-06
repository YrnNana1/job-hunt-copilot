package com.jobhuntcopilot.tailor;

import com.jobhuntcopilot.resume.ResumeBullet;
import com.jobhuntcopilot.resume.ResumeDocument;
import com.jobhuntcopilot.resume.ResumeEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the JSON parsing and validation logic directly — no live Claude API call. Constructing
 * ClaudeResumeTailor with a fake key is safe (the SDK client only talks to the network on
 * .create(), which these tests never call); parseResponse/buildResult are pure and package-visible
 * specifically so this validation logic can be tested without hitting the network.
 */
class ClaudeResumeTailorTest {

    private final ClaudeResumeTailor tailor = new ClaudeResumeTailor("test-api-key");

    private final ResumeDocument document = new ResumeDocument(
            "PREFIX",
            List.of(
                    new ResumeEntry("exp1", "\\resumeSubheading{Job One}{2024}{Acme}{}",
                            List.of(new ResumeBullet("exp1-b1", "Did thing A."),
                                    new ResumeBullet("exp1-b2", "Did thing B."))),
                    new ResumeEntry("exp2", "\\resumeSubheading{Job Two}{2023}{Beta}{}",
                            List.of(new ResumeBullet("exp2-b1", "Did thing C.")))),
            "BETWEEN",
            List.of(
                    new ResumeEntry("proj1", "\\resumeProjectHeading{Project One}{2024}",
                            List.of(new ResumeBullet("proj1-b1", "Built X."))),
                    new ResumeEntry("proj2", "\\resumeProjectHeading{Project Two}{2023}",
                            List.of(new ResumeBullet("proj2-b1", "Built Y."),
                                    new ResumeBullet("proj2-b2", "Built Z.")))),
            "SUFFIX");

    @Test
    void detectsRewordingAndDroppingAndReorderingKeptProjects() {
        String json = """
                Here you go:
                ```json
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A, aligned with the posting.", "dropped": false, "reason": "surfaces keyword"},
                      {"bulletId": "exp1-b2", "text": "Did thing B.", "dropped": true, "reason": "least relevant, trimmed"}
                    ]},
                    {"entryId": "exp2", "bullets": [
                      {"bulletId": "exp2-b1", "text": "Did thing C.", "dropped": false}
                    ]}
                  ],
                  "projects": [
                    {"entryId": "proj2", "dropped": false, "reason": "more relevant, moved first",
                     "bullets": [
                       {"bulletId": "proj2-b1", "text": "Built Y using 5 microservices.", "dropped": false, "reason": "added detail from posting"},
                       {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                     ]},
                    {"entryId": "proj1", "dropped": false,
                     "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]}
                  ]
                }
                ```
                """;

        ClaudeResumeTailor.ClaudeTailorResponse response = tailor.parseResponse(json);
        TailoringResult result = tailor.buildResult(document, response);

        // Experience always renders in original order regardless of response order.
        assertEquals(List.of("exp1", "exp2"), result.plan().experience().stream().map(EntryPlan::entryId).toList());
        assertEquals(List.of("exp1-b1"),
                result.plan().experience().get(0).bullets().stream().map(BulletPlan::bulletId).toList());
        assertEquals("Did thing A, aligned with the posting.", result.plan().experience().get(0).bullets().get(0).text());

        // Projects render in the response's order.
        assertEquals(List.of("proj2", "proj1"), result.plan().projects().stream().map(EntryPlan::entryId).toList());

        List<TailoringChange> changes = result.changes();
        assertTrue(changes.stream().anyMatch(c -> c.type() == TailoringChange.ChangeType.REWORDED
                && "exp1-b1".equals(c.bulletId())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == TailoringChange.ChangeType.DROPPED
                && "exp1-b2".equals(c.bulletId())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == TailoringChange.ChangeType.ENTRY_REORDERED));

        TailoringChange rewordedProjectBullet = changes.stream()
                .filter(c -> "proj2-b1".equals(c.bulletId()) && c.type() == TailoringChange.ChangeType.REWORDED)
                .findFirst().orElseThrow();
        assertEquals(List.of("5"), rewordedProjectBullet.suspiciousNewNumbers());
    }

    @Test
    void dropsAWholeProjectAndRecordsEntryDropped() {
        String json = """
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A.", "dropped": false},
                      {"bulletId": "exp1-b2", "text": "Did thing B.", "dropped": false}
                    ]},
                    {"entryId": "exp2", "bullets": [{"bulletId": "exp2-b1", "text": "Did thing C.", "dropped": false}]}
                  ],
                  "projects": [
                    {"entryId": "proj1", "dropped": true, "reason": "least relevant, dropped for space",
                     "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]},
                    {"entryId": "proj2", "dropped": false,
                     "bullets": [
                       {"bulletId": "proj2-b1", "text": "Built Y.", "dropped": false},
                       {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                     ]}
                  ]
                }
                """;

        TailoringResult result = tailor.buildResult(document, tailor.parseResponse(json));

        assertEquals(List.of("proj2"), result.plan().projects().stream().map(EntryPlan::entryId).toList());
        assertTrue(result.changes().stream().anyMatch(c -> c.type() == TailoringChange.ChangeType.ENTRY_DROPPED));
    }

    @Test
    void throwsWhenAnExperienceEntryIsMissingFromTheResponse() {
        String json = """
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A.", "dropped": false},
                      {"bulletId": "exp1-b2", "text": "Did thing B.", "dropped": false}
                    ]}
                  ],
                  "projects": [
                    {"entryId": "proj1", "dropped": false, "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]},
                    {"entryId": "proj2", "dropped": false, "bullets": [
                      {"bulletId": "proj2-b1", "text": "Built Y.", "dropped": false},
                      {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                    ]}
                  ]
                }
                """;

        ClaudeResumeTailor.ClaudeTailorResponse response = tailor.parseResponse(json);
        assertThrows(TailoringException.class, () -> tailor.buildResult(document, response));
    }

    @Test
    void throwsWhenAnUnknownBulletIdIsReferenced() {
        String json = """
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A.", "dropped": false},
                      {"bulletId": "exp1-b99", "text": "Fabricated bullet.", "dropped": false}
                    ]},
                    {"entryId": "exp2", "bullets": [{"bulletId": "exp2-b1", "text": "Did thing C.", "dropped": false}]}
                  ],
                  "projects": [
                    {"entryId": "proj1", "dropped": false, "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]},
                    {"entryId": "proj2", "dropped": false, "bullets": [
                      {"bulletId": "proj2-b1", "text": "Built Y.", "dropped": false},
                      {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                    ]}
                  ]
                }
                """;

        ClaudeResumeTailor.ClaudeTailorResponse response = tailor.parseResponse(json);
        TailoringException exception = assertThrows(TailoringException.class, () -> tailor.buildResult(document, response));
        assertTrue(exception.getMessage().contains("unknown bullet id"));
    }

    @Test
    void throwsWhenEveryBulletInAnEntryIsDropped() {
        String json = """
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A.", "dropped": true, "reason": "trim"},
                      {"bulletId": "exp1-b2", "text": "Did thing B.", "dropped": true, "reason": "trim"}
                    ]},
                    {"entryId": "exp2", "bullets": [{"bulletId": "exp2-b1", "text": "Did thing C.", "dropped": false}]}
                  ],
                  "projects": [
                    {"entryId": "proj1", "dropped": false, "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]},
                    {"entryId": "proj2", "dropped": false, "bullets": [
                      {"bulletId": "proj2-b1", "text": "Built Y.", "dropped": false},
                      {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                    ]}
                  ]
                }
                """;

        ClaudeResumeTailor.ClaudeTailorResponse response = tailor.parseResponse(json);
        assertThrows(TailoringException.class, () -> tailor.buildResult(document, response));
    }

    @Test
    void throwsWhenEveryProjectIsDropped() {
        String json = """
                {
                  "experience": [
                    {"entryId": "exp1", "bullets": [
                      {"bulletId": "exp1-b1", "text": "Did thing A.", "dropped": false},
                      {"bulletId": "exp1-b2", "text": "Did thing B.", "dropped": false}
                    ]},
                    {"entryId": "exp2", "bullets": [{"bulletId": "exp2-b1", "text": "Did thing C.", "dropped": false}]}
                  ],
                  "projects": [
                    {"entryId": "proj1", "dropped": true, "reason": "trim", "bullets": [{"bulletId": "proj1-b1", "text": "Built X.", "dropped": false}]},
                    {"entryId": "proj2", "dropped": true, "reason": "trim", "bullets": [
                      {"bulletId": "proj2-b1", "text": "Built Y.", "dropped": false},
                      {"bulletId": "proj2-b2", "text": "Built Z.", "dropped": false}
                    ]}
                  ]
                }
                """;

        ClaudeResumeTailor.ClaudeTailorResponse response = tailor.parseResponse(json);
        assertThrows(TailoringException.class, () -> tailor.buildResult(document, response));
    }

    @Test
    void throwsOnMalformedJson() {
        assertThrows(TailoringException.class, () -> tailor.parseResponse("not json at all"));
    }
}
