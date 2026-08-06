package com.jobhuntcopilot.db;

import com.jobhuntcopilot.eligibility.EligibilityResult;
import com.jobhuntcopilot.model.Job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable, queryable log of every posting EligibilityFilter has hard-excluded — lets the
 * exclusions be spot-checked for over-filtering instead of just trusted. See schema.sql for why
 * this is idempotent (INSERT OR IGNORE on a UNIQUE(source, external_id) constraint).
 */
public class EligibilityExclusionRepository {

    public record ExclusionLogEntry(String source, String externalId, String title, String company,
            String reason, String detail, Instant excludedAt) {
    }

    private final Database database;

    public EligibilityExclusionRepository(Database database) {
        this.database = database;
    }

    public void log(Job job, EligibilityResult result) throws SQLException {
        if (result.eligible()) {
            throw new IllegalArgumentException("Only exclusions should be logged, not eligible results");
        }
        String sql = "INSERT OR IGNORE INTO eligibility_exclusions "
                + "(source, external_id, title, company, reason, detail, excluded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job.getSource());
            statement.setString(2, job.getExternalId());
            statement.setString(3, job.getTitle());
            statement.setString(4, job.getCompany());
            statement.setString(5, result.reason());
            statement.setString(6, result.detail());
            statement.setString(7, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public List<ExclusionLogEntry> findAll() throws SQLException {
        String sql = "SELECT * FROM eligibility_exclusions ORDER BY excluded_at DESC";
        try (Connection connection = database.connect();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<ExclusionLogEntry> entries = new ArrayList<>();
            while (resultSet.next()) {
                entries.add(new ExclusionLogEntry(
                        resultSet.getString("source"),
                        resultSet.getString("external_id"),
                        resultSet.getString("title"),
                        resultSet.getString("company"),
                        resultSet.getString("reason"),
                        resultSet.getString("detail"),
                        Instant.parse(resultSet.getString("excluded_at"))));
            }
            return entries;
        }
    }
}
