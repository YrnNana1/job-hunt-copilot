package com.jobhuntcopilot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Caches the tailored cover letter generated for a posting — one row per job id — so reopening the
 * same posting's detail view doesn't re-call the Claude API. See CoverLetterGenerationService,
 * which checks this before generating anything new.
 */
public class CoverLetterRepository {

    public record CoverLetterRecord(
            long id, long jobId, String latex, String pdfPath, String changesJson, String model, Instant generatedAt) {
    }

    private final Database database;

    public CoverLetterRepository(Database database) {
        this.database = database;
    }

    public Optional<CoverLetterRecord> findByJobId(long jobId) throws SQLException {
        String sql = "SELECT * FROM cover_letters WHERE job_id = ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    public void save(long jobId, String latex, String pdfPath, String changesJson, String model) throws SQLException {
        String sql = "INSERT INTO cover_letters (job_id, latex, pdf_path, changes_json, model, generated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(job_id) DO UPDATE SET latex = excluded.latex, pdf_path = excluded.pdf_path, "
                + "changes_json = excluded.changes_json, model = excluded.model, generated_at = excluded.generated_at";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, jobId);
            statement.setString(2, latex);
            statement.setString(3, pdfPath);
            statement.setString(4, changesJson);
            statement.setString(5, model);
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private CoverLetterRecord mapRow(ResultSet resultSet) throws SQLException {
        return new CoverLetterRecord(
                resultSet.getLong("id"),
                resultSet.getLong("job_id"),
                resultSet.getString("latex"),
                resultSet.getString("pdf_path"),
                resultSet.getString("changes_json"),
                resultSet.getString("model"),
                Instant.parse(resultSet.getString("generated_at")));
    }
}
