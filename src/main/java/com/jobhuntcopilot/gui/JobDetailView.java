package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.score.ScoreFactor;
import com.jobhuntcopilot.score.ScoredJob;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.SQLException;

/**
 * Full detail for a single posting: metadata, the complete per-factor score
 * breakdown (the list view only shows the total), the full description, and
 * actions (open the original posting, dismiss). Opening this view marks a
 * NEW posting VIEWED — see MainView, which calls JobPipeline.markViewed
 * before constructing this.
 *
 * No Apply button yet — that needs a tailored resume (Phase 6) and cover
 * letter (Phase 7) to actually attach, and a half-wired button that opens
 * nothing isn't worth having yet.
 */
public class JobDetailView extends BorderPane {

    private final JobPipeline pipeline;
    private final ScoredJob scoredJob;
    private final HostServices hostServices;
    private final Runnable onBack;
    private final Label statusMessageLabel = new Label();

    public JobDetailView(JobPipeline pipeline, ScoredJob scoredJob, HostServices hostServices, Runnable onBack) {
        this.pipeline = pipeline;
        this.scoredJob = scoredJob;
        this.hostServices = hostServices;
        this.onBack = onBack;
        setPadding(new Insets(15));
        setTop(buildHeader());
        setCenter(buildBody());
    }

    private Node buildHeader() {
        Job job = scoredJob.job();

        Button backButton = new Button("← Back to list");
        backButton.setOnAction(event -> onBack.run());

        Label titleLabel = new Label(job.getTitle());
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label statusBadge = new Label(job.getStatus().name());
        statusBadge.setStyle("-fx-background-color: " + StatusBadges.colorFor(job.getStatus())
                + "; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 3;");

        Label subtitleLabel = new Label(job.getCompany() + "  •  Score: " + scoredJob.breakdown().totalScore() + "/100");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");

        HBox titleRow = new HBox(10, titleLabel, statusBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(4, titleRow, subtitleLabel);

        HBox header = new HBox(15, backButton, titleBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 15, 0));
        return header;
    }

    private Node buildBody() {
        VBox body = new VBox(18,
                buildMetadataSection(),
                new Separator(),
                buildScoreBreakdownSection(),
                new Separator(),
                buildDescriptionSection(),
                buildActionsRow(),
                statusMessageLabel);

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private Node buildMetadataSection() {
        Job job = scoredJob.job();
        String location = job.isRemote() ? "Remote" : (job.getLocation() == null ? "Not listed" : job.getLocation());

        HBox row = new HBox(30,
                metadataItem("Location", location),
                metadataItem("Salary", JobFormatting.formatSalary(job)),
                metadataItem("Posted", JobFormatting.formatPosted(job.getPostedDate())),
                metadataItem("Source", job.getSource()));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node metadataItem(String label, String value) {
        Label labelNode = new Label(label.toUpperCase());
        labelNode.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 13px;");
        return new VBox(2, labelNode, valueNode);
    }

    private Node buildScoreBreakdownSection() {
        Label heading = new Label("Score Breakdown");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        VBox factors = new VBox(10);
        for (ScoreFactor factor : scoredJob.breakdown().factors()) {
            factors.getChildren().add(buildFactorRow(factor));
        }

        return new VBox(8, heading, factors);
    }

    private Node buildFactorRow(ScoreFactor factor) {
        Label nameAndPoints = new Label(String.format(
                "%s — %.1f pts (weight %.0f%%, raw %.0f%%)",
                factor.name(), factor.points(), factor.weight() * 100, factor.rawScore() * 100));
        nameAndPoints.setStyle("-fx-font-weight: bold;");

        Label explanation = new Label(factor.explanation());
        explanation.setWrapText(true);
        explanation.setStyle("-fx-text-fill: #444;");

        return new VBox(2, nameAndPoints, explanation);
    }

    private Node buildDescriptionSection() {
        Label heading = new Label("Full Description");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        String description = scoredJob.job().getDescription();
        Label descriptionLabel = new Label(description == null || description.isBlank()
                ? "No description provided." : description);
        descriptionLabel.setWrapText(true);

        return new VBox(8, heading, descriptionLabel);
    }

    private Node buildActionsRow() {
        Job job = scoredJob.job();

        Button openButton = new Button("Open posting ↗");
        openButton.setDisable(job.getUrl() == null || job.getUrl().isBlank());
        openButton.setOnAction(event -> hostServices.showDocument(job.getUrl()));

        Button dismissButton = new Button("Dismiss");
        dismissButton.setOnAction(event -> onDismiss());

        HBox row = new HBox(10, openButton, dismissButton);
        row.setPadding(new Insets(10, 0, 0, 0));
        return row;
    }

    private void onDismiss() {
        try {
            pipeline.dismiss(scoredJob.job());
            onBack.run();
        } catch (SQLException e) {
            statusMessageLabel.setText("Failed to dismiss: " + e.getMessage());
        }
    }
}
