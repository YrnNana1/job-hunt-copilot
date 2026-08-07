package com.jobhuntcopilot.apply;

import com.google.gson.Gson;
import com.jobhuntcopilot.config.ProfileConfig;
import com.jobhuntcopilot.db.ApplyAttemptRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates one apply attempt: launch a visible browser, navigate to the posting, detect the
 * ATS, scan and resolve every field (pattern match first, Claude fallback), fill the browser, and
 * log the attempt. Deliberately never calls {@code driver.quit()} on any path — the browser is left
 * open under the user's control from the moment it's launched, whether the attempt succeeds, fails,
 * or hits an unsupported ATS, so there's always something real to look at, and so the only way an
 * application is ever submitted is the human clicking Submit themselves.
 *
 * The only browser-touching step ({@link #launchAndScan}) is a single package-private method so
 * tests can override it with canned data — same "fake subclass overrides the one network-touching
 * method" pattern ClaudeResumeTailor/ClaudeCoverLetterWriter use for the Claude API call, applied
 * here to the Selenium call instead. Everything else (field resolution, filling, attempt logging)
 * runs for real in tests, same as production.
 */
public class ApplyFlowService {

    private final ProfileConfig profile;
    private final FieldMatcher fieldMatcher;
    private final ClaudeFieldInterpreter claudeFieldInterpreter;
    private final ApplicationFormFiller filler;
    private final ApplyAttemptRepository applyAttemptRepository;
    private final JobRepository jobRepository;
    private final Gson gson = new Gson();

    public ApplyFlowService(
            ProfileConfig profile, FieldMatcher fieldMatcher, ClaudeFieldInterpreter claudeFieldInterpreter,
            ApplicationFormFiller filler, ApplyAttemptRepository applyAttemptRepository, JobRepository jobRepository) {
        this.profile = profile;
        this.fieldMatcher = fieldMatcher;
        this.claudeFieldInterpreter = claudeFieldInterpreter;
        this.filler = filler;
        this.applyAttemptRepository = applyAttemptRepository;
        this.jobRepository = jobRepository;
    }

    public ApplyAttemptView start(Job job, Path resumePdfPath, Path coverLetterPdfPath) throws SQLException {
        if (job.getUrl() == null || job.getUrl().isBlank()) {
            throw new ApplyException("This posting has no URL to apply to");
        }

        Instant startedAt = Instant.now();

        AtsScanResult scanResult;
        try {
            scanResult = launchAndScan(job.getUrl());
        } catch (RuntimeException e) {
            applyAttemptRepository.save(job.getId(), AtsType.UNKNOWN, job.getUrl(), "[]", "FAILED", startedAt);
            throw new ApplyException("Failed to load or scan the application page: " + e.getMessage(), e);
        }

        if (scanResult.atsType() == AtsType.UNKNOWN) {
            long attemptId = applyAttemptRepository.save(
                    job.getId(), AtsType.UNKNOWN, job.getUrl(), "[]", "UNSUPPORTED_ATS", startedAt);
            return new ApplyAttemptView(attemptId, AtsType.UNKNOWN, job.getUrl(), List.of());
        }

        List<FieldMatch> matches;
        try {
            matches = scanResult.fields().stream().map(field -> resolve(field, resumePdfPath, coverLetterPdfPath)).toList();
            filler.fill(scanResult.driver(), matches);
        } catch (RuntimeException e) {
            applyAttemptRepository.save(job.getId(), scanResult.atsType(), job.getUrl(), "[]", "FAILED", startedAt);
            throw new ApplyException("Failed to prepare application: " + e.getMessage(), e);
        }

        long attemptId = applyAttemptRepository.save(
                job.getId(), scanResult.atsType(), job.getUrl(), gson.toJson(matches), "PREPARED", startedAt);
        return new ApplyAttemptView(attemptId, scanResult.atsType(), job.getUrl(), matches);
    }

    public void recordOutcome(long attemptId, Job job, boolean submitted) throws SQLException {
        applyAttemptRepository.updateOutcome(attemptId, submitted ? "SUBMITTED" : "NOT_SUBMITTED", Instant.now());
        if (submitted) {
            jobRepository.updateStatus(job.getId(), JobStatus.APPLIED);
        }
    }

    /** Launches a real Chrome window, navigates to {@code url}, detects the ATS, and scans its fields. */
    AtsScanResult launchAndScan(String url) {
        WebDriver driver = new ChromeDriver();
        driver.get(url);

        AtsType atsType = AtsDetector.detect(driver.getCurrentUrl(), driver.getPageSource());
        if (atsType == AtsType.UNKNOWN) {
            return new AtsScanResult(driver, atsType, List.of());
        }

        AtsFormScanner scanner = atsType == AtsType.GREENHOUSE ? new GreenhouseFormScanner() : new LeverFormScanner();
        return new AtsScanResult(driver, atsType, scanner.scan(driver));
    }

    private FieldMatch resolve(FormField field, Path resumePdfPath, Path coverLetterPdfPath) {
        FieldMatch patternMatch = fieldMatcher.match(field, profile, resumePdfPath, coverLetterPdfPath);
        if (patternMatch.source() != MatchSource.UNMATCHED) {
            return patternMatch;
        }
        return claudeFieldInterpreter.interpret(field, profile, resumePdfPath, coverLetterPdfPath);
    }

    record AtsScanResult(WebDriver driver, AtsType atsType, List<FormField> fields) {
    }
}
