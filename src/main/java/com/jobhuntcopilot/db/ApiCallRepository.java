package com.jobhuntcopilot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Logs every outbound API call and answers "when did we last call this?" so
 * JobFetchService can skip re-fetching a search term too soon (see its
 * cooldown check) and I can see quota usage over time.
 */
public class ApiCallRepository {

    private final Database database;

    public ApiCallRepository(Database database) {
        this.database = database;
    }

    public void log(String endpoint, String params) throws SQLException {
        String sql = "INSERT INTO api_calls (endpoint, params, called_at) VALUES (?, ?, ?)";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, endpoint);
            statement.setString(2, params);
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public Optional<Instant> lastCallTime(String params) throws SQLException {
        String sql = "SELECT called_at FROM api_calls WHERE params = ? ORDER BY called_at DESC LIMIT 1";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(Instant.parse(resultSet.getString(1))) : Optional.empty();
            }
        }
    }

    public int countCallsSince(Instant since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM api_calls WHERE called_at >= ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, since.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
