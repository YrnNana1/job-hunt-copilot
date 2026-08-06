package com.jobhuntcopilot;

import com.jobhuntcopilot.config.BlocklistConfig;
import com.jobhuntcopilot.config.ConfigLoader;
import com.jobhuntcopilot.config.EnvLoader;
import com.jobhuntcopilot.config.RolesConfig;
import com.jobhuntcopilot.db.ApiCallRepository;
import com.jobhuntcopilot.db.Database;
import com.jobhuntcopilot.db.EligibilityExclusionRepository;
import com.jobhuntcopilot.db.JobRepository;
import com.jobhuntcopilot.fetch.AdzunaClient;
import com.jobhuntcopilot.fetch.JobFetchService;
import com.jobhuntcopilot.gui.MainView;
import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.resume.ResumeKeywordExtractor;
import com.jobhuntcopilot.score.ScoringEngine;
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
 * replaced the console printout with a JavaFX window, and Phase 5 (this
 * one) adds the detail view — MainView now owns navigation between the two.
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

        AdzunaClient adzunaClient = new AdzunaClient(
                EnvLoader.require("ADZUNA_APP_ID"), EnvLoader.require("ADZUNA_APP_KEY"));
        JobFetchService fetchService = new JobFetchService(
                adzunaClient, jobRepository, apiCallRepository, eligibilityExclusionRepository, blocklist,
                roles.recency(), roles.eligibility());

        Set<String> resumeKeywords = ResumeKeywordExtractor.extractKeywords(Path.of("resources", "base_resume.tex"));
        ScoringEngine scoringEngine = new ScoringEngine(resumeKeywords, roles);

        return new JobPipeline(roles, blocklist, jobRepository, fetchService, apiCallRepository,
                eligibilityExclusionRepository, scoringEngine);
    }
}
