package com.jobhuntcopilot.apply;

import com.jobhuntcopilot.config.DisabilityStatus;
import com.jobhuntcopilot.config.EeoAnswers;
import com.jobhuntcopilot.config.PersonalInfo;
import com.jobhuntcopilot.config.ProfileConfig;
import com.jobhuntcopilot.config.RaceEthnicity;
import com.jobhuntcopilot.config.VeteranStatus;
import com.jobhuntcopilot.config.WorkAuthorization;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldMatcherTest {

    private final FieldMatcher matcher = new FieldMatcher();

    private final ProfileConfig profile = new ProfileConfig(
            new PersonalInfo("Jane Example", "Jane", "Example", "jane@example.com", "555-5555",
                    "https://linkedin.com/in/jane", "https://jane.dev", "Remote, USA"),
            new WorkAuthorization(true, false, false),
            new EeoAnswers(DisabilityStatus.NOT_DISABLED, VeteranStatus.NOT_VETERAN,
                    RaceEthnicity.BLACK_OR_AFRICAN_AMERICAN, null));

    @Test
    void matchesATextFieldByLabel() {
        FormField field = new FormField("id:email", "Email", FieldType.EMAIL, true, List.of());
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.PATTERN, match.source());
        assertEquals("jane@example.com", match.resolvedValue());
        assertFalseFlagged(match);
    }

    @Test
    void resolvesAnEeoSelectOptionViaSynonymMatching() {
        FormField field = new FormField("id:race", "Race/Ethnicity", FieldType.SELECT, false,
                List.of("Hispanic or Latino", "Black or African American (Not Hispanic or Latino)",
                        "White", "I don't wish to answer"));
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.PATTERN, match.source());
        assertEquals("Black or African American (Not Hispanic or Latino)", match.resolvedOptionText());
        assertFalseFlagged(match);
    }

    @Test
    void resolvesAWorkAuthorizationRadioGroup() {
        FormField field = new FormField("name:work_auth", "Are you legally authorized to work in the US?",
                FieldType.RADIO_GROUP, true, List.of("Yes", "No"));
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.PATTERN, match.source());
        assertEquals("Yes", match.resolvedOptionText());
    }

    @Test
    void detectsResumeAndCoverLetterFileUploads() {
        Path resume = Path.of("resume.pdf");
        Path coverLetter = Path.of("cover-letter.pdf");

        FieldMatch resumeMatch = matcher.match(
                new FormField("id:resume", "Resume/CV", FieldType.FILE, true, List.of()), profile, resume, coverLetter);
        FieldMatch coverLetterMatch = matcher.match(
                new FormField("id:cl", "Cover Letter", FieldType.FILE, false, List.of()), profile, resume, coverLetter);

        assertTrue(resumeMatch.resolvedValue().endsWith("resume.pdf"));
        assertTrue(coverLetterMatch.resolvedValue().endsWith("cover-letter.pdf"));
    }

    @Test
    void leavesAnUnrecognizedLabelBlankAndFlagged() {
        FormField field = new FormField("id:mystery", "What's your favorite algorithm?", FieldType.TEXTAREA, false, List.of());
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.UNMATCHED, match.source());
        assertTrue(match.flagged());
        assertEquals(null, match.resolvedValue());
    }

    @Test
    void leavesAGenderFieldBlankAndFlaggedWhenNotConfigured() {
        FormField field = new FormField("name:gender", "Gender", FieldType.RADIO_GROUP, false,
                List.of("Male", "Female", "I don't wish to answer"));
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.UNMATCHED, match.source());
        assertTrue(match.flagged());
        assertTrue(match.flagReason().contains("not configured"));
    }

    @Test
    void leavesAnEeoFieldBlankWhenNoOptionOnTheFormMatches() {
        FormField field = new FormField("id:race", "Race", FieldType.SELECT, false, List.of("Prefer not to say"));
        FieldMatch match = matcher.match(field, profile, null, null);

        assertEquals(MatchSource.UNMATCHED, match.source());
        assertTrue(match.flagged());
    }

    private void assertFalseFlagged(FieldMatch match) {
        assertEquals(false, match.flagged());
    }
}
