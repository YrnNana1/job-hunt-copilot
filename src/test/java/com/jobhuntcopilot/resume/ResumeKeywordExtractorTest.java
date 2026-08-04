package com.jobhuntcopilot.resume;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real resources/base_resume.tex to make sure extraction works on the actual file, not just fixtures. */
class ResumeKeywordExtractorTest {

    @Test
    void extractsExpectedSkillsFromTheRealResume() throws IOException {
        Set<String> keywords = ResumeKeywordExtractor.extractKeywords(Path.of("resources", "base_resume.tex"));

        assertTrue(keywords.containsAll(Set.of(
                "python", "java", "sql", "javascript", "azure", "devops", "git", "agile",
                "troubleshooting", "documentation", "client", "communication")));
    }

    @Test
    void doesNotIncludeLatexCommandNamesAsKeywords() throws IOException {
        Set<String> keywords = ResumeKeywordExtractor.extractKeywords(Path.of("resources", "base_resume.tex"));

        assertFalse(keywords.contains("textbf"));
        assertFalse(keywords.contains("resumeitem"));
        assertFalse(keywords.contains("section"));
    }
}
