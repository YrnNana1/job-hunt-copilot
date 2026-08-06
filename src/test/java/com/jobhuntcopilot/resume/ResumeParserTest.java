package com.jobhuntcopilot.resume;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real resources/base_resume.tex so a structural change to the template is caught here. */
class ResumeParserTest {

    @Test
    void parsesAllThreeExperienceEntriesInOrderWithTheirBullets() throws IOException {
        ResumeDocument document = parseRealResume();

        List<ResumeEntry> experience = document.experienceEntries();
        assertEquals(3, experience.size());
        assertEquals("exp1", experience.get(0).id());
        assertEquals("exp2", experience.get(1).id());
        assertEquals("exp3", experience.get(2).id());

        assertTrue(experience.get(0).headerLatex().contains("IT Instructional Designer Intern"));
        assertEquals(3, experience.get(0).bullets().size());
        assertEquals("exp1-b1", experience.get(0).bullets().get(0).id());
        assertTrue(experience.get(0).bullets().get(0).text().contains("ClickLearn"));

        assertTrue(experience.get(1).headerLatex().contains("Life Insurance Agent"));
        assertEquals(2, experience.get(1).bullets().size());

        assertTrue(experience.get(2).headerLatex().contains("Audio/Visual Engineer"));
        assertEquals(3, experience.get(2).bullets().size());
    }

    @Test
    void parsesAllThreeProjectEntriesInOrderWithTheirBullets() throws IOException {
        ResumeDocument document = parseRealResume();

        List<ResumeEntry> projects = document.projectEntries();
        assertEquals(3, projects.size());
        assertEquals("proj1", projects.get(0).id());
        assertEquals("proj2", projects.get(1).id());
        assertEquals("proj3", projects.get(2).id());

        assertTrue(projects.get(0).headerLatex().contains("Email Classification System"));
        assertEquals(2, projects.get(0).bullets().size());

        assertTrue(projects.get(1).headerLatex().contains("Virtual Sort System"));
        assertEquals(1, projects.get(1).bullets().size());

        assertTrue(projects.get(2).headerLatex().contains("VR Art Gallery"));
        assertEquals(1, projects.get(2).bullets().size());
    }

    @Test
    void keepsEducationCertificationsSkillsAndLeadershipOutsideTheEditableEntries() throws IOException {
        ResumeDocument document = parseRealResume();

        assertTrue(document.prefix().contains("Education"));
        assertTrue(document.prefix().contains("GPA: 3.13"));
        assertTrue(document.prefix().contains("Certifications"));
        assertTrue(document.prefix().contains("CompTIA Security+"));
        assertTrue(document.prefix().contains("Technical Skills"));
        assertTrue(document.suffix().contains("Leadership"));
        assertTrue(document.suffix().contains("Black Men Excellence"));

        // None of these ever appear inside a bullet or header of an editable entry.
        for (ResumeEntry entry : document.experienceEntries()) {
            assertFalse(entry.headerLatex().contains("GPA"));
        }
    }

    @Test
    void everyBulletIdInTheDocumentIsUnique() throws IOException {
        ResumeDocument document = parseRealResume();

        List<String> allBulletIds = java.util.stream.Stream.concat(
                        document.experienceEntries().stream(), document.projectEntries().stream())
                .flatMap(entry -> entry.bullets().stream())
                .map(ResumeBullet::id)
                .toList();

        assertEquals(allBulletIds.size(), java.util.Set.copyOf(allBulletIds).size());
    }

    private ResumeDocument parseRealResume() throws IOException {
        return ResumeParser.parse(Files.readString(realResumePath()));
    }

    private Path realResumePath() {
        return Path.of("resources", "base_resume.tex");
    }
}
