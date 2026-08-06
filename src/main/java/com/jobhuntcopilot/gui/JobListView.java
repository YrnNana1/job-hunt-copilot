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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * The list view: a ranked, scrollable table of scored postings with a status
 * badge, a per-row View Details button (double-clicking a row does the same
 * thing) that hands off to the detail view, and a per-row Dismiss button.
 * Refresh fetches new postings from Adzuna on a background thread — it
 * makes real HTTP calls, and blocking the JavaFX Application Thread would
 * freeze the whole window until it finished.
 */
public class JobListView extends BorderPane {

    private final JobPipeline pipeline;
    private final Consumer<ScoredJob> onViewDetails;
    private final TableView<ScoredJob> table = new TableView<>();
    private final Label statusLabel = new Label();
    private final Button refreshButton = new Button("Refresh");

    public JobListView(JobPipeline pipeline, Consumer<ScoredJob> onViewDetails) {
        this.pipeline = pipeline;
        this.onViewDetails = onViewDetails;
        setPadding(new Insets(10));
        setTop(buildToolbar());
        setCenter(buildTable());
    }

    /** Re-reads whatever's in the database — fast, local, no network — used both on startup and after returning from the detail view. */
    public void reload() {
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
                postedColumn(), locationColumn(), statusColumn(), viewColumn(), dismissColumn()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No postings yet — click Refresh to fetch from Adzuna."));
        table.setRowFactory(tv -> {
            TableRow<ScoredJob> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    onViewDetails.accept(row.getItem());
                }
            });
            return row;
        });
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
        column.setCellValueFactory(data -> new SimpleStringProperty(JobFormatting.formatSalary(data.getValue().job())));
        return column;
    }

    private TableColumn<ScoredJob, String> postedColumn() {
        TableColumn<ScoredJob, String> column = new TableColumn<>("Posted");
        column.setCellValueFactory(data -> new SimpleStringProperty(
                JobFormatting.formatPosted(data.getValue().job().getPostedDate())));
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
                    setStyle("-fx-background-color: " + StatusBadges.colorFor(status)
                            + "; -fx-text-fill: white; -fx-alignment: CENTER;");
                }
            }
        });
        return column;
    }

    private TableColumn<ScoredJob, Void> viewColumn() {
        TableColumn<ScoredJob, Void> column = new TableColumn<>("");
        column.setMinWidth(110);
        column.setMaxWidth(110);
        column.setCellFactory(col -> new TableCell<>() {
            private final Button viewButton = new Button("View Details");

            {
                viewButton.setOnAction(event -> onViewDetails.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewButton);
            }
        });
        return column;
    }

    private TableColumn<ScoredJob, Void> dismissColumn() {
        TableColumn<ScoredJob, Void> column = new TableColumn<>("");
        column.setMinWidth(90);
        column.setMaxWidth(90);
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

}
