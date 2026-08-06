package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.score.ScoreFactor;
import com.jobhuntcopilot.score.ScoredJob;
import com.jobhuntcopilot.tailor.TailoredResumeView;
import com.jobhuntcopilot.tailor.TailoringChange;
import javafx.application.HostServices;
import javafx.concurrent.Task;
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

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * Full detail for a single posting: metadata, the complete per-factor score
 * breakdown (the list view only shows the total), the full description,
 * Claude-powered resume tailoring (Phase 6), and actions (open the original
 * posting, dismiss). Opening this view marks a NEW posting VIEWED — see
 * MainView, which calls JobPipeline.markViewed before constructing this.
 *
 * No Apply button yet — that needs a cover letter (Phase 7) too, and a
 * half-wired button that opens nothing isn't worth having yet.
 */
public class JobDetailView extends BorderPane {

    private final JobPipeline pipeline;
    private final ScoredJob scoredJob;
    private final HostServices hostServices;
    private final Runnable onBack;
    private final Label statusMessageLabel = new Label();
    private final Label tailoredResumeStatusLabel = new Label();
    private final Button generateTailoredResumeButton = new Button("Tailor Resume for This Posting");
    private final Button openTailoredResumeButton = new Button("Open Tailored Resume PDF");
    private final VBox tailoredResumeChangesBox = new VBox(6);
    private Path tailoredResumePdfPath;

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
                new Separator(),
                buildTailoredResumeSection(),
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

    private Node buildTailoredResumeSection() {
        Label heading = new Label("Tailored Resume");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label helpText = new Label("Uses Claude to reorder and reword real resume bullets to surface keywords "
                + "from this posting — it never adds anything that isn't already in the base resume. Review "
                + "the changes below before using the PDF.");
        helpText.setWrapText(true);
        helpText.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        generateTailoredResumeButton.setOnAction(event -> onGenerateTailoredResume());
        openTailoredResumeButton.setDisable(true);
        openTailoredResumeButton.setOnAction(event -> {
            if (tailoredResumePdfPath != null) {
                hostServices.showDocument(tailoredResumePdfPath.toUri().toString());
            }
        });
        HBox actions = new HBox(10, generateTailoredResumeButton, openTailoredResumeButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, heading, helpText, actions, tailoredResumeStatusLabel, tailoredResumeChangesBox);
    }

    private void onGenerateTailoredResume() {
        generateTailoredResumeButton.setDisable(true);
        tailoredResumeStatusLabel.setText(
                "Asking Claude to tailor the resume and compiling to PDF — this can take a bit...");
        tailoredResumeChangesBox.getChildren().clear();

        Task<TailoredResumeView> task = new Task<>() {
            @Override
            protected TailoredResumeView call() throws SQLException {
                return pipeline.tailorResume(scoredJob.job());
            }
        };
        task.setOnSucceeded(event -> onTailoredResumeReady(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            tailoredResumeStatusLabel.setText(
                    "Failed to tailor resume: " + (error == null ? "unknown error" : error.getMessage()));
            generateTailoredResumeButton.setDisable(false);
        });

        Thread tailorThread = new Thread(task, "resume-tailor");
        tailorThread.setDaemon(true);
        tailorThread.start();
    }

    private void onTailoredResumeReady(TailoredResumeView view) {
        tailoredResumePdfPath = view.pdfPath();
        openTailoredResumeButton.setDisable(false);
        generateTailoredResumeButton.setDisable(false);
        tailoredResumeStatusLabel.setText(view.cached()
                ? "Loaded a previously tailored resume for this posting — no new Claude call was made."
                : "Tailored resume generated. Review the changes below, then open the PDF.");
        renderTailoringChanges(view.changes());
    }

    private void renderTailoringChanges(List<TailoringChange> changes) {
        tailoredResumeChangesBox.getChildren().clear();
        if (changes.isEmpty()) {
            tailoredResumeChangesBox.getChildren().add(new Label("No changes — the base resume already matched well."));
            return;
        }
        for (TailoringChange change : changes) {
            tailoredResumeChangesBox.getChildren().add(buildChangeRow(change));
        }
    }

    private Node buildChangeRow(TailoringChange change) {
        Label header = new Label(changeSummary(change));
        header.setWrapText(true);
        header.setStyle("-fx-font-weight: bold;");

        VBox rows = new VBox(2, header);
        if (change.reason() != null && !change.reason().isBlank()) {
            Label reason = new Label("Why: " + change.reason());
            reason.setWrapText(true);
            reason.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");
            rows.getChildren().add(reason);
        }
        if (change.type() == TailoringChange.ChangeType.REWORDED) {
            rows.getChildren().addAll(wrappedLabel("Before: " + change.originalText()),
                    wrappedLabel("After: " + change.newText()));
        } else if (change.type() == TailoringChange.ChangeType.DROPPED) {
            rows.getChildren().add(wrappedLabel("Dropped: " + change.originalText()));
        }
        if (!change.suspiciousNewNumbers().isEmpty()) {
            Label warning = new Label("New number(s) not in the original bullet — verify before using: "
                    + String.join(", ", change.suspiciousNewNumbers()));
            warning.setWrapText(true);
            warning.setStyle("-fx-text-fill: #b00020; -fx-font-size: 12px; -fx-font-weight: bold;");
            rows.getChildren().add(warning);
        }
        rows.setStyle("-fx-padding: 6; -fx-background-color: #f5f5f5; -fx-background-radius: 4;");
        return rows;
    }

    private Label wrappedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 12px;");
        return label;
    }

    private String changeSummary(TailoringChange change) {
        return switch (change.type()) {
            case REWORDED -> change.section() + " — " + change.entryLabel() + ": reworded a bullet";
            case REORDERED -> change.section() + " — " + change.entryLabel() + ": reordered a bullet";
            case DROPPED -> change.section() + " — " + change.entryLabel() + ": dropped a bullet";
            case ENTRY_DROPPED -> change.section() + " — dropped \"" + change.entryLabel() + "\" entirely";
            case ENTRY_REORDERED -> change.section() + " — moved \"" + change.entryLabel() + "\"";
        };
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
