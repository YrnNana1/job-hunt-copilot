package com.jobhuntcopilot;

import com.jobhuntcopilot.apply.ApplicationFormFiller;
import com.jobhuntcopilot.apply.ApplyFlowService;
import com.jobhuntcopilot.apply.ClaudeFieldInterpreter;
import com.jobhuntcopilot.apply.FieldMatcher;
import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.ConfigLoader;
import com.jobhuntcopilot.config.EnvLoader;
import com.jobhuntcopilot.config.ProfileConfig;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.coverletter.ClaudeCoverLetterWriter;
import com.jobhuntcopilot.coverletter.CoverLetterGenerationService;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.ApplyAttemptRepository;
import com.jobhuntcopilot.db.CoverLetterRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.db.TailoredResumeRepository;
import com.jobhuntcopilot.fetch.AdzunaClient;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.gui.MainView;
import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.resume.ResumeKeywordExtractor;
import com.jobhuntcopilot.score.ScoringEngine;
import com.jobhuntcopilot.tailor.ClaudeResumeTailor;
import com.jobhuntcopilot.tailor.ResumeTailoringService;
import com.jobhuntcopilot.tailor.TectonicCompiler;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Set;

/**
 * Entry point for Job Hunt Copilot.
 *
 * Phase 0 was the Maven skeleton, Phase 1 added config + the database,
 * Phase 2 fetched real postings from Adzuna, Phase 3 added scoring, Phase 4
 * replaced the console printout with a JavaFX window, Phase 5 added the
 * detail view, Phase 6 added Claude-powered resume tailoring compiled to PDF
 * via Tectonic, Phase 7 added the same for cover letters, and Phase 8 (this
 * one) adds the semi-automated Selenium apply flow for Greenhouse/Lever.
 */
public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        JobPipeline pipeline = buildPipeline();

        MainView mainView = new MainView(pipeline, getHostServices());
        mainView.loadInitial();

        primaryStage.setTitle("Job Hunt Copilot");
        primaryStage.setScene(new Scene(mainView, 1150, 700));
        primaryStage.show();
    }

    private JobPipeline buildPipeline() throws Exception {
        RolesConfig roles = ConfigLoader.loadRolesConfig();
        BlocklistConfig blocklist = ConfigLoader.loadBlocklistConfig();

        Database database = new Database(Path.of("data", "jobhunt.db"));
        database.initSchema();

        JobRepository jobRepository = new JobRepository(database);
        ApiCallRepository apiCallRepository = new ApiCallRepository(database);
        EligibilityExclusionRepository eligibilityExclusionRepository = new EligibilityExclusionRepository(database);
        TailoredResumeRepository tailoredResumeRepository = new TailoredResumeRepository(database);
        CoverLetterRepository coverLetterRepository = new CoverLetterRepository(database);
        ApplyAttemptRepository applyAttemptRepository = new ApplyAttemptRepository(database);

        AdzunaClient adzunaClient = new AdzunaClient(
                EnvLoader.require("ADZUNA_APP_ID"), EnvLoader.require("ADZUNA_APP_KEY"));
        JobFetchService fetchService = new JobFetchService(
                adzunaClient, jobRepository, apiCallRepository, eligibilityExclusionRepository, blocklist,
                roles.recency(), roles.eligibility());

        Path baseResumePath = Path.of("resources", "base_resume.tex");
        Set<String> resumeKeywords = ResumeKeywordExtractor.extractKeywords(baseResumePath);
        ScoringEngine scoringEngine = new ScoringEngine(resumeKeywords, roles);

        String anthropicApiKey = EnvLoader.require("ANTHROPIC_API_KEY");
        ClaudeResumeTailor claudeResumeTailor = new ClaudeResumeTailor(anthropicApiKey);
        ResumeTailoringService resumeTailoringService = new ResumeTailoringService(
                baseResumePath, Path.of("data", "tailored-resumes"), claudeResumeTailor, new TectonicCompiler(),
                tailoredResumeRepository);

        ClaudeCoverLetterWriter claudeCoverLetterWriter = new ClaudeCoverLetterWriter(anthropicApiKey);
        CoverLetterGenerationService coverLetterGenerationService = new CoverLetterGenerationService(
                Path.of("resources", "base_cover_letter.tex"), Path.of("data", "cover-letters"),
                claudeCoverLetterWriter, new TectonicCompiler(), coverLetterRepository);

        ProfileConfig profile = ConfigLoader.loadProfileConfig();
        ApplyFlowService applyFlowService = new ApplyFlowService(
                profile, new FieldMatcher(), new ClaudeFieldInterpreter(anthropicApiKey), new ApplicationFormFiller(),
                applyAttemptRepository, jobRepository);

        return new JobPipeline(roles, blocklist, jobRepository, fetchService, apiCallRepository,
                eligibilityExclusionRepository, scoringEngine, resumeTailoringService, coverLetterGenerationService,
                applyFlowService);
    }
}
