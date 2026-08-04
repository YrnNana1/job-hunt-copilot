package com.jobhuntcopilot.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the JDBC URL for a SQLite file and knows how to apply schema.sql to it.
 *
 * Each repository call opens and closes its own {@link Connection} rather than
 * sharing one across the app — at this project's scale (a local desktop app,
 * a few thousand rows at most) that's simpler to reason about than pooling,
 * and SQLite handles it fine.
 */
public class Database {

    private final String jdbcUrl;

    public Database(Path dbFile) {
        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory for database file: " + dbFile, e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile;
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        // SQLite enforces foreign keys and CHECK constraints only when this pragma is on.
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /** Creates the jobs table (and indexes) if they don't already exist. Safe to call every startup. */
    public void initSchema() {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                InputStream schemaStream = getClass().getResourceAsStream("/schema.sql")) {
            if (schemaStream == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            String schema = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            for (String sqlStatement : schema.split(";")) {
                if (!sqlStatement.isBlank()) {
                    statement.execute(sqlStatement);
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}
