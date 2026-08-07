package com.jobhuntcopilot.apply;

import com.jobhuntcopilot.config.DisabilityStatus;
import com.jobhuntcopilot.config.GenderIdentity;
import com.jobhuntcopilot.config.ProfileConfig;
import com.jobhuntcopilot.config.RaceEthnicity;
import com.jobhuntcopilot.config.VeteranStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Keyword/pattern matching for application form fields — the first pass of the field-recognition
 * strategy (see ClaudeFieldInterpreter for the fallback pass). Pure and Selenium-free, so it's
 * testable with plain fixtures. Every branch either resolves a field confidently (MatchSource.PATTERN)
 * or returns FieldMatch.unmatched(...) with a specific reason — there is no branch that guesses.
 */
public class FieldMatcher {

    private static final Pattern FIRST_NAME = Pattern.compile("first\\s*name", CASE_INSENSITIVE);
    private static final Pattern LAST_NAME = Pattern.compile("last\\s*name|surname", CASE_INSENSITIVE);
    private static final Pattern FULL_NAME = Pattern.compile("\\bname\\b", CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("e-?mail", CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("phone|mobile", CASE_INSENSITIVE);
    private static final Pattern LINKEDIN = Pattern.compile("linkedin", CASE_INSENSITIVE);
    private static final Pattern WEBSITE = Pattern.compile("website|portfolio|personal site", CASE_INSENSITIVE);
    private static final Pattern LOCATION = Pattern.compile("location|current city|\\bcity\\b", CASE_INSENSITIVE);
    private static final Pattern RESUME_UPLOAD = Pattern.compile("resum[eé]|\\bcv\\b", CASE_INSENSITIVE);
    private static final Pattern COVER_LETTER_UPLOAD = Pattern.compile("cover letter", CASE_INSENSITIVE);
    private static final Pattern WORK_AUTHORIZED = Pattern.compile("authorized.*work|legally.*work|work.*authoriz", CASE_INSENSITIVE);
    private static final Pattern SPONSORSHIP = Pattern.compile("sponsorship|sponsor.*visa|visa.*sponsor", CASE_INSENSITIVE);
    private static final Pattern SPONSORSHIP_FUTURE = Pattern.compile("future", CASE_INSENSITIVE);
    private static final Pattern DISABILITY = Pattern.compile("disab", CASE_INSENSITIVE);
    private static final Pattern VETERAN = Pattern.compile("veteran", CASE_INSENSITIVE);
    private static final Pattern RACE_ETHNICITY = Pattern.compile("race|ethnicity", CASE_INSENSITIVE);
    private static final Pattern GENDER = Pattern.compile("gender|\\bsex\\b", CASE_INSENSITIVE);

    private static final Pattern DECLINE_PATTERN =
            Pattern.compile("decline|prefer not|don'?t wish|do not wish|not disclose", CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_PATTERN = Pattern.compile("\\bnot\\b|\\bno\\b|don'?t\\b|non-", CASE_INSENSITIVE);

    private static final Map<RaceEthnicity, Pattern> RACE_PATTERNS = Map.of(
            RaceEthnicity.AMERICAN_INDIAN_OR_ALASKA_NATIVE, Pattern.compile("american indian|alaska native", CASE_INSENSITIVE),
            RaceEthnicity.ASIAN, Pattern.compile("\\basian\\b", CASE_INSENSITIVE),
            RaceEthnicity.BLACK_OR_AFRICAN_AMERICAN, Pattern.compile("black|african american", CASE_INSENSITIVE),
            RaceEthnicity.HISPANIC_OR_LATINO, Pattern.compile("hispanic|latino|latina|latinx", CASE_INSENSITIVE),
            RaceEthnicity.NATIVE_HAWAIIAN_OR_PACIFIC_ISLANDER, Pattern.compile("native hawaiian|pacific islander", CASE_INSENSITIVE),
            RaceEthnicity.WHITE, Pattern.compile("\\bwhite\\b", CASE_INSENSITIVE),
            RaceEthnicity.TWO_OR_MORE_RACES, Pattern.compile("two or more|multiracial|multiple races", CASE_INSENSITIVE));

    private static final Map<GenderIdentity, Pattern> GENDER_PATTERNS = Map.of(
            GenderIdentity.MALE, Pattern.compile("\\bmale\\b", CASE_INSENSITIVE),
            GenderIdentity.FEMALE, Pattern.compile("\\bfemale\\b", CASE_INSENSITIVE),
            GenderIdentity.NON_BINARY, Pattern.compile("non-?binary|genderqueer|gender non-conforming", CASE_INSENSITIVE));

    public FieldMatch match(FormField field, ProfileConfig profile, Path resumePdfPath, Path coverLetterPdfPath) {
        String label = field.labelText();

        if (field.fieldType() == FieldType.FILE && RESUME_UPLOAD.matcher(label).find()) {
            return fileMatch(field, resumePdfPath, "resume upload");
        }
        if (field.fieldType() == FieldType.FILE && COVER_LETTER_UPLOAD.matcher(label).find()) {
            return fileMatch(field, coverLetterPdfPath, "cover letter upload");
        }

        if (FIRST_NAME.matcher(label).find()) {
            return textMatch(field, profile.personal().firstName(), "first name");
        }
        if (LAST_NAME.matcher(label).find()) {
            return textMatch(field, profile.personal().lastName(), "last name");
        }
        if (EMAIL.matcher(label).find()) {
            return textMatch(field, profile.personal().email(), "email");
        }
        if (PHONE.matcher(label).find()) {
            return textMatch(field, profile.personal().phone(), "phone");
        }
        if (LINKEDIN.matcher(label).find()) {
            return textMatch(field, profile.personal().linkedInUrl(), "LinkedIn URL");
        }
        if (WEBSITE.matcher(label).find()) {
            return textMatch(field, profile.personal().websiteUrl(), "website URL");
        }
        if (LOCATION.matcher(label).find()) {
            return textMatch(field, profile.personal().location(), "location");
        }
        if (FULL_NAME.matcher(label).find()) {
            return textMatch(field, profile.personal().fullName(), "full name");
        }

        if (SPONSORSHIP.matcher(label).find()) {
            boolean desiredTrue = SPONSORSHIP_FUTURE.matcher(label).find()
                    ? profile.workAuthorization().requiresSponsorshipFuture()
                    : (profile.workAuthorization().requiresSponsorshipNow() || profile.workAuthorization().requiresSponsorshipFuture());
            return booleanMatch(field, desiredTrue, "sponsorship requirement");
        }
        if (WORK_AUTHORIZED.matcher(label).find()) {
            return booleanMatch(field, profile.workAuthorization().authorizedToWorkInUs(), "work authorization");
        }

        if (DISABILITY.matcher(label).find()) {
            return eeoBooleanMatch(field, profile.eeo().disabilityStatus(),
                    DisabilityStatus.DISABLED, DisabilityStatus.DECLINE_TO_ANSWER, "disability status");
        }
        if (VETERAN.matcher(label).find()) {
            return eeoBooleanMatch(field, profile.eeo().veteranStatus(),
                    VeteranStatus.VETERAN, VeteranStatus.DECLINE_TO_ANSWER, "veteran status");
        }
        if (RACE_ETHNICITY.matcher(label).find()) {
            return categoryMatch(field, profile.eeo().raceEthnicity(), RaceEthnicity.DECLINE_TO_ANSWER,
                    RACE_PATTERNS, "race/ethnicity");
        }
        if (GENDER.matcher(label).find()) {
            return categoryMatch(field, profile.eeo().genderIdentity(), GenderIdentity.DECLINE_TO_ANSWER,
                    GENDER_PATTERNS, "gender identity");
        }

        return FieldMatch.unmatched(field, "No recognized pattern for this field's label");
    }

    private FieldMatch textMatch(FormField field, String value, String description) {
        if (value == null || value.isBlank()) {
            return FieldMatch.unmatched(field, capitalize(description) + " not configured in profile.json");
        }
        return new FieldMatch(field, MatchSource.PATTERN, value, null, false, null);
    }

    private FieldMatch fileMatch(FormField field, Path filePath, String description) {
        if (filePath == null) {
            return FieldMatch.unmatched(field, capitalize(description) + " not available for this posting");
        }
        return new FieldMatch(field, MatchSource.PATTERN, filePath.toAbsolutePath().toString(), null, false, null);
    }

    private <E extends Enum<E>> FieldMatch eeoBooleanMatch(
            FormField field, E value, E trueValue, E declineValue, String description) {
        if (value == null) {
            return FieldMatch.unmatched(field, capitalize(description) + " not configured in profile.json");
        }
        if (value == declineValue) {
            return declineMatch(field, description);
        }
        return booleanMatch(field, value == trueValue, description);
    }

    private FieldMatch booleanMatch(FormField field, boolean desiredTrue, String description) {
        return switch (field.fieldType()) {
            case CHECKBOX -> new FieldMatch(field, MatchSource.PATTERN, String.valueOf(desiredTrue), null, false, null);
            case SELECT, RADIO_GROUP -> {
                Optional<String> option = findOptionForBoolean(field.options(), desiredTrue);
                yield option.isEmpty()
                        ? FieldMatch.unmatched(field, "No option on this form matched the configured " + description)
                        : new FieldMatch(field, MatchSource.PATTERN, String.valueOf(desiredTrue), option.get(), false, null);
            }
            case TEXT, TEXTAREA -> new FieldMatch(field, MatchSource.PATTERN, desiredTrue ? "Yes" : "No", null, false, null);
            default -> FieldMatch.unmatched(field, "Unsupported field type for " + description);
        };
    }

    private FieldMatch declineMatch(FormField field, String description) {
        if (field.fieldType() == FieldType.SELECT || field.fieldType() == FieldType.RADIO_GROUP) {
            Optional<String> option = findDeclineOption(field.options());
            return option.isEmpty()
                    ? FieldMatch.unmatched(field, "No \"decline to answer\" option found on this form for " + description)
                    : new FieldMatch(field, MatchSource.PATTERN, "Decline to answer", option.get(), false, null);
        }
        return new FieldMatch(field, MatchSource.PATTERN, "I don't wish to answer", null, false, null);
    }

    private <E extends Enum<E>> FieldMatch categoryMatch(
            FormField field, E value, E declineValue, Map<E, Pattern> synonymPatterns, String description) {
        if (value == null) {
            return FieldMatch.unmatched(field, capitalize(description) + " not configured in profile.json");
        }
        if (field.fieldType() != FieldType.SELECT && field.fieldType() != FieldType.RADIO_GROUP) {
            return new FieldMatch(field, MatchSource.PATTERN, displayName(value), null, false, null);
        }
        Pattern pattern = value == declineValue ? DECLINE_PATTERN : synonymPatterns.get(value);
        if (pattern == null) {
            return FieldMatch.unmatched(field, "No synonym pattern configured for " + displayName(value));
        }
        for (String option : field.options()) {
            if (pattern.matcher(option).find()) {
                return new FieldMatch(field, MatchSource.PATTERN, displayName(value), option, false, null);
            }
        }
        return FieldMatch.unmatched(field, "No option on this form matched the configured " + description);
    }

    private Optional<String> findOptionForBoolean(List<String> options, boolean desiredTrue) {
        for (String option : options) {
            if (DECLINE_PATTERN.matcher(option).find()) {
                continue;
            }
            boolean isNegative = NEGATIVE_PATTERN.matcher(option).find();
            if (desiredTrue != isNegative) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    private Optional<String> findDeclineOption(List<String> options) {
        return options.stream().filter(option -> DECLINE_PATTERN.matcher(option).find()).findFirst();
    }

    private String displayName(Enum<?> value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.name().split("_")) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word.equalsIgnoreCase("OR") ? "or" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
