package com.jobhuntcopilot.resume;

import com.jobhuntcopilot.tailor.BulletPlan;
import com.jobhuntcopilot.tailor.EntryPlan;
import com.jobhuntcopilot.tailor.TailoringPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeAssemblerTest {

    private final ResumeDocument document = new ResumeDocument(
            "PREFIX\n",
            List.of(
                    new ResumeEntry("exp1", "\\resumeSubheading{Job One}{2024}{Acme}{}",
                            List.of(new ResumeBullet("exp1-b1", "Did thing A."),
                                    new ResumeBullet("exp1-b2", "Did thing B."))),
                    new ResumeEntry("exp2", "\\resumeSubheading{Job Two}{2023}{Beta}{}",
                            List.of(new ResumeBullet("exp2-b1", "Did thing C.")))),
            "BETWEEN\n",
            List.of(
                    new ResumeEntry("proj1", "\\resumeProjectHeading{Project One}{2024}",
                            List.of(new ResumeBullet("proj1-b1", "Built X."))),
                    new ResumeEntry("proj2", "\\resumeProjectHeading{Project Two}{2023}",
                            List.of(new ResumeBullet("proj2-b1", "Built Y."),
                                    new ResumeBullet("proj2-b2", "Built Z.")))),
            "SUFFIX\n");

    @Test
    void keepsHeadersAndPrefixSuffixByteForByte() {
        TailoringPlan unchanged = new TailoringPlan(
                List.of(new EntryPlan("exp1", List.of(new BulletPlan("exp1-b1", "Did thing A."),
                                new BulletPlan("exp1-b2", "Did thing B."))),
                        new EntryPlan("exp2", List.of(new BulletPlan("exp2-b1", "Did thing C.")))),
                List.of(new EntryPlan("proj1", List.of(new BulletPlan("proj1-b1", "Built X."))),
                        new EntryPlan("proj2", List.of(new BulletPlan("proj2-b1", "Built Y."),
                                new BulletPlan("proj2-b2", "Built Z.")))));

        String tex = ResumeAssembler.assemble(document, unchanged);

        assertTrue(tex.startsWith("PREFIX\n"));
        assertTrue(tex.endsWith("SUFFIX\n"));
        assertTrue(tex.contains("BETWEEN\n"));
        assertTrue(tex.contains("\\resumeSubheading{Job One}{2024}{Acme}{}"));
        assertTrue(tex.contains("\\resumeSubheading{Job Two}{2023}{Beta}{}"));
        assertTrue(tex.contains("\\resumeProjectHeading{Project One}{2024}"));
        assertTrue(tex.contains("\\resumeProjectHeading{Project Two}{2023}"));
        assertTrue(tex.contains("\\resumeItem{Did thing A.}"));
        assertTrue(tex.contains("\\resumeItem{Did thing B.}"));
        assertTrue(tex.contains("\\resumeItem{Built X.}"));
        assertTrue(tex.contains("\\resumeItem{Built Y.}"));
    }

    @Test
    void rewordedAndReorderedBulletsRenderInThePlanOrderWithNewText() {
        TailoringPlan plan = new TailoringPlan(
                List.of(new EntryPlan("exp1", List.of(
                                new BulletPlan("exp1-b2", "Did thing B, reworded for the posting."),
                                new BulletPlan("exp1-b1", "Did thing A."))),
                        new EntryPlan("exp2", List.of(new BulletPlan("exp2-b1", "Did thing C.")))),
                List.of(new EntryPlan("proj1", List.of(new BulletPlan("proj1-b1", "Built X."))),
                        new EntryPlan("proj2", List.of(new BulletPlan("proj2-b1", "Built Y."),
                                new BulletPlan("proj2-b2", "Built Z.")))));

        String tex = ResumeAssembler.assemble(document, plan);

        int rewordedIndex = tex.indexOf("Did thing B, reworded for the posting.");
        int originalAIndex = tex.indexOf("\\resumeItem{Did thing A.}");
        assertTrue(rewordedIndex >= 0);
        assertTrue(rewordedIndex < originalAIndex, "reworded bullet should render before the reordered original");
        assertFalse(tex.contains("\\resumeItem{Did thing B.}"), "original unreworded text should not remain");
    }

    @Test
    void droppedBulletIsOmittedEntirely() {
        TailoringPlan plan = new TailoringPlan(
                List.of(new EntryPlan("exp1", List.of(new BulletPlan("exp1-b1", "Did thing A."))),
                        new EntryPlan("exp2", List.of(new BulletPlan("exp2-b1", "Did thing C.")))),
                List.of(new EntryPlan("proj1", List.of(new BulletPlan("proj1-b1", "Built X."))),
                        new EntryPlan("proj2", List.of(new BulletPlan("proj2-b1", "Built Y."),
                                new BulletPlan("proj2-b2", "Built Z.")))));

        String tex = ResumeAssembler.assemble(document, plan);

        assertFalse(tex.contains("Did thing B"));
        assertTrue(tex.contains("\\resumeItem{Did thing A.}"));
    }

    @Test
    void droppedProjectEntryIsOmittedAndVspaceOnlyAppearsBetweenRemainingKeptProjects() {
        TailoringPlan plan = new TailoringPlan(
                List.of(new EntryPlan("exp1", List.of(new BulletPlan("exp1-b1", "Did thing A."))),
                        new EntryPlan("exp2", List.of(new BulletPlan("exp2-b1", "Did thing C.")))),
                List.of(new EntryPlan("proj2", List.of(new BulletPlan("proj2-b1", "Built Y.")))));

        String tex = ResumeAssembler.assemble(document, plan);

        assertFalse(tex.contains("Project One"));
        assertFalse(tex.contains("Built X."));
        assertTrue(tex.contains("Project Two"));
        // Only one project remains, so there's no "next" project to add trailing spacing before.
        assertFalse(tex.contains("\\vspace{-18pt}"));
    }

    @Test
    void vspaceSeparatesEveryKeptProjectExceptTheLast() {
        TailoringPlan plan = new TailoringPlan(
                List.of(new EntryPlan("exp1", List.of(new BulletPlan("exp1-b1", "Did thing A."))),
                        new EntryPlan("exp2", List.of(new BulletPlan("exp2-b1", "Did thing C.")))),
                List.of(new EntryPlan("proj1", List.of(new BulletPlan("proj1-b1", "Built X."))),
                        new EntryPlan("proj2", List.of(new BulletPlan("proj2-b1", "Built Y.")))));

        String tex = ResumeAssembler.assemble(document, plan);

        assertEquals(1, tex.split("\\\\vspace\\{-18pt\\}", -1).length - 1);
        assertTrue(tex.indexOf("Built X.") < tex.indexOf("\\vspace{-18pt}"));
        assertTrue(tex.indexOf("\\vspace{-18pt}") < tex.indexOf("Built Y."));
    }
}
