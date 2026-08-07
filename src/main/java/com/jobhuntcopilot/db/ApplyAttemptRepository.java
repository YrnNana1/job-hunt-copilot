package com.jobhuntcopilot.db;

import com.jobhuntcopilot.apply.AtsType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Logs every Apply button click — one row per attempt, not per job (a user may retry after
 * NOT_SUBMITTED) — so an attempt can be debugged without re-running Selenium blind. See
 * ApplyFlowService, which writes a PREPARED/UNSUPPORTED_ATS/FAILED row up front and later moves it
 * to SUBMITTED/NOT_SUBMITTED once the user confirms what actually happened in the browser.
 */
public class ApplyAttemptRepository {

    public record ApplyAttemptRecord(
            long id, long jobId, AtsType atsType, String url, String fieldsJson, String outcome,
            Instant startedAt, Instant finishedAt) {
    }

    private final Database database;

    public ApplyAttemptRepository(Database database) {
        this.database = database;
    }

    public long save(long jobId, AtsType atsType, String url, String fieldsJson, String outcome, Instant startedAt)
            throws SQLException {
        String sql = "INSERT INTO apply_attempts (job_id, ats_type, url, fields_json, outcome, started_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, jobId);
            statement.setString(2, atsType.name());
            statement.setString(3, url);
            statement.setString(4, fieldsJson);
            statement.setString(5, outcome);
            statement.setString(6, startedAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void updateOutcome(long attemptId, String outcome, Instant finishedAt) throws SQLException {
        String sql = "UPDATE apply_attempts SET outcome = ?, finished_at = ? WHERE id = ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, outcome);
            statement.setString(2, finishedAt.toString());
            statement.setLong(3, attemptId);
            statement.executeUpdate();
        }
    }

    public List<ApplyAttemptRecord> findByJobId(long jobId) throws SQLException {
        String sql = "SELECT * FROM apply_attempts WHERE job_id = ? ORDER BY started_at DESC";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ApplyAttemptRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(mapRow(resultSet));
                }
                return records;
            }
        }
    }

    private ApplyAttemptRecord mapRow(ResultSet resultSet) throws SQLException {
        String finishedAt = resultSet.getString("finished_at");
        return new ApplyAttemptRecord(
                resultSet.getLong("id"),
                resultSet.getLong("job_id"),
                AtsType.valueOf(resultSet.getString("ats_type")),
                resultSet.getString("url"),
                resultSet.getString("fields_json"),
                resultSet.getString("outcome"),
                Instant.parse(resultSet.getString("started_at")),
                finishedAt == null ? null : Instant.parse(finishedAt));
    }

    public Optional<ApplyAttemptRecord> findLatestByJobId(long jobId) throws SQLException {
        List<ApplyAttemptRecord> records = findByJobId(jobId);
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(0));
    }
}
