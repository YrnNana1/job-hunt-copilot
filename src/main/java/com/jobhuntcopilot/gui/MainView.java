package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.score.ScoredJob;
import javafx.application.HostServices;
import javafx.scene.layout.BorderPane;

import java.sql.SQLException;

/**
 * Owns navigation between the list and detail views within a single window
 * (swapping the center content) rather than opening a second Stage — one
 * Scene to manage, no separate window lifecycle to think about.
 */
public class MainView extends BorderPane {

    private final JobPipeline pipeline;
    private final HostServices hostServices;
    private final JobListView listView;

    public MainView(JobPipeline pipeline, HostServices hostServices) {
        this.pipeline = pipeline;
        this.hostServices = hostServices;
        this.listView = new JobListView(pipeline, this::showDetail);
        setCenter(listView);
    }

    /** Loads whatever's already in the database — fast, local, no network — so the window isn't empty on startup. */
    public void loadInitial() {
        listView.reload();
    }

    private void showDetail(ScoredJob scoredJob) {
        try {
            pipeline.markViewed(scoredJob.job());
        } catch (SQLException e) {
            // Non-fatal — the detail view still opens even if the status update fails.
        }
        setCenter(new JobDetailView(pipeline, scoredJob, hostServices, this::showList));
    }

    private void showList() {
        setCenter(listView);
        listView.reload();
    }
}
