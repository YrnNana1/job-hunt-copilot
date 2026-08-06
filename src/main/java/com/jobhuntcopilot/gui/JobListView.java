package com.jobhuntcopilot.gui;

import com.jobhuntcopilot.fetch.FetchSummary;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;
import com.jobhuntcopilot.pipeline.JobPipeline;
import com.jobhuntcopilot.score.ScoredJob;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The Phase 4 list view: a ranked, scrollable table of scored postings with a
 * status badge and a per-row Dismiss button, plus a Refresh button that
 * fetches new postings from Adzuna. Fetching runs on a background thread —
 * it makes real HTTP calls, and blocking the JavaFX Application Thread would
 * freeze the whole window until it finished.
 */
public class JobListView extends BorderPane {

    private final JobPipeline pipeline;
    private final TableView<ScoredJob> table = new TableView<>();
    private final Label statusLabel = new Label();
    private final Button refreshButton = new Button("Refresh");

    public JobListView(JobPipeline pipeline) {
        this.pipeline = pipeline;
        setPadding(new Insets(10));
        setTop(buildToolbar());
        setCenter(buildTable());
    }

    /** Loads whatever's already in the database — fast, local, no network — so the window isn't empty on startup. */
    public void loadInitial() {
        try {
            List<ScoredJob> jobs = pipeline.loadScoredJobs();
            table.setItems(FXCollections.observableArrayList(jobs));
            statusLabel.setText(jobs.size() + " posting(s) shown.");
        } catch (SQLException e) {
            statusLabel.setText("Failed to load postings: " + e.getMessage());
        }
    }

    private HBox buildToolbar() {
        refreshButton.setOnAction(event -> onRefresh());
        HBox toolbar = new HBox(10, refreshButton, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));
        return toolbar;
    }

    private TableView<ScoredJob> buildTable() {
        table.getColumns().addAll(List.of(
                scoreColumn(), titleColumn(), companyColumn(), salaryColumn(),
                postedColumn(), locationColumn(), statusColumn(), dismissColumn()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No postings yet — click Refresh to fetch from Adzuna."));
        return table;
    }

    private void onRefresh() {
        refreshButton.setDisable(true);
        statusLabel.setText("Fetching from Adzuna...");

        Task<List<ScoredJob>> task = new Task<>() {
            @Override
            protected List<ScoredJob> call() throws SQLException {
                List<FetchSummary> summaries = pipeline.fetchNewPostings();
                int inserted = summaries.stream().mapToInt(FetchSummary::inserted).sum();
                int ineligible = summaries.stream().mapToInt(FetchSummary::ineligible).sum();
                int callsToday = pipeline.apiCallsInLast24Hours();
                updateMessage(inserted + " new posting(s) fetched, " + ineligible + " ineligible (filtered) ("
                        + callsToday + " Adzuna calls in the last 24h).");
                return pipeline.loadScoredJobs();
            }
        };

        task.setOnSucceeded(event -> {
            table.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getMessage() + " " + task.getValue().size() + " posting(s) shown.");
            refreshButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            statusLabel.setText("Refresh failed: " + task.getException().getMessage());
            refreshButton.setDisable(false);
        });

        Thread fetchThread = new Thread(task, "job-fetch");
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private void onDismiss(ScoredJob scoredJob) {
        try {
            pipeline.dismiss(scoredJob.job());
            table.getItems().remove(scoredJob);
            statusLabel.setText(table.getItems().size() + " posting(s) shown.");
        } catch (SQLException e) {
            statusLabel.setText("Failed to dismiss: " + e.getMessage());
        }
    }

    private TableColumn<ScoredJob, Number> scoreColumn() {
        TableColumn<ScoredJob, Number> column = new TableColumn<>("Score");
        column.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().breakdown().totalScore()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        return column;
    }

    private TableColumn<ScoredJob, String> titleColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Title");
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().job().getTitle()));
        return column;
    }

    private TableColumn<ScoredJob, String> companyColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Company");
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().job().getCompany()));
        return column;
    }

    private TableColumn<ScoredJob, String> salaryColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Salary");
        column.setCellValueFactory(data -> new SimpleStringProperty(formatSalary(data.getValue().job())));
        return column;
    }

    private TableColumn<ScoredJob, String> postedColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Posted");
        column.setCellValueFactory(data -> new SimpleStringProperty(formatPosted(data.getValue().job().getPostedDate())));
        return column;
    }

    private TableColumn<ScoredJob, String> locationColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Location");
        column.setCellValueFactory(data -> {
            Job job = data.getValue().job();
            String location = job.isRemote() ? "Remote" : job.getLocation();
            return new SimpleStringProperty(location == null ? "" : location);
        });
        return column;
    }

    private TableColumn<ScoredJob, JobStatus> statusColumn() {
        TableColumn<ScoredJob, JobStatus> column = new TableColumn<>("Status");
        column.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().job().getStatus()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(JobStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.name());
                    setStyle("-fx-background-color: " + badgeColor(status)
                            + "; -fx-text-fill: white; -fx-alignment: CENTER;");
                }
            }
        });
        return column;
    }

    private TableColumn<ScoredJob, Void> dismissColumn() {
        TableColumn<ScoredJob, Void> column = new TableColumn<>("");
        column.setCellFactory(col -> new TableCell<>() {
            private final Button dismissButton = new Button("Dismiss");

            {
                dismissButton.setOnAction(event -> onDismiss(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : dismissButton);
            }
        });
        return column;
    }

    private static String formatSalary(Job job) {
        Double min = job.getSalaryMin();
        Double max = job.getSalaryMax();
        if (min == null && max == null) {
            return "—";
        }
        if (min == null || max == null || min.equals(max)) {
            return "$" + formatThousands(min != null ? min : max);
        }
        return "$" + formatThousands(min) + " - $" + formatThousands(max);
    }

    private static String formatThousands(double value) {
        return Math.round(value / 1000) + "k";
    }

    private static String formatPosted(LocalDate postedDate) {
        long daysOld = ChronoUnit.DAYS.between(postedDate, LocalDate.now());
        if (daysOld <= 0) {
            return "Today";
        }
        return daysOld == 1 ? "1 day ago" : daysOld + " days ago";
    }

    private static String badgeColor(JobStatus status) {
        return switch (status) {
            case NEW -> "#3b82f6";
            case VIEWED -> "#6b7280";
            case APPLIED -> "#16a34a";
            case DISMISSED -> "#dc2626";
        };
    }
}
