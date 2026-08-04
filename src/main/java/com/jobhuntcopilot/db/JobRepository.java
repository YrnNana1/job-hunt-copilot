package com.jobhuntcopilot.db;

import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.model.JobStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes rows in the jobs table.
 *
 * Dedupe happens here, not in the schema, because it's two different checks:
 * a fast exact match on (source, externalId) via the dedupe_key column, and
 * a fallback title+company+postedDate match to catch the same posting
 * reappearing through a different source. See schema.sql for the column
 * this backs.
 */
public class JobRepository {

    public enum SaveOutcome {
        INSERTED,
        DUPLICATE_SKIPPED
    }

    private final Database database;

    public JobRepository(Database database) {
        this.database = database;
    }

    public SaveOutcome save(Job job) throws SQLException {
        try (Connection connection = database.connect()) {
            if (findBySourceAndExternalId(connection, job.getSource(), job.getExternalId()).isPresent()) {
                return SaveOutcome.DUPLICATE_SKIPPED;
            }
            if (findByTitleCompanyPostedDate(connection, job.getTitle(), job.getCompany(), job.getPostedDate())
                    .isPresent()) {
                return SaveOutcome.DUPLICATE_SKIPPED;
            }
            insert(connection, job);
            return SaveOutcome.INSERTED;
        }
    }

    public Optional<Job> findBySourceAndExternalId(String source, String externalId) throws SQLException {
        try (Connection connection = database.connect()) {
            return findBySourceAndExternalId(connection, source, externalId);
        }
    }

    public List<Job> findAll() throws SQLException {
        String sql = "SELECT * FROM jobs ORDER BY posted_date DESC, id DESC";
        try (Connection connection = database.connect();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            List<Job> jobs = new ArrayList<>();
            while (resultSet.next()) {
                jobs.add(mapRow(resultSet));
            }
            return jobs;
        }
    }

    public void updateStatus(long id, JobStatus status) throws SQLException {
        String sql = "UPDATE jobs SET status = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.dbValue());
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private Optional<Job> findBySourceAndExternalId(Connection connection, String source, String externalId)
            throws SQLException {
        String sql = "SELECT * FROM jobs WHERE source = ? AND external_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setString(2, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    private Optional<Job> findByTitleCompanyPostedDate(
            Connection connection, String title, String company, LocalDate postedDate) throws SQLException {
        String sql = "SELECT * FROM jobs WHERE LOWER(title) = LOWER(?) AND LOWER(company) = LOWER(?) "
                + "AND posted_date = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, company);
            statement.setString(3, postedDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    private void insert(Connection connection, Job job) throws SQLException {
        String sql = "INSERT INTO jobs (source, external_id, dedupe_key, title, company, location, remote, "
                + "description, url, salary_min, salary_max, salary_currency, posted_date, fetched_at, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, job.getSource());
            statement.setString(2, job.getExternalId());
            statement.setString(3, job.dedupeKey());
            statement.setString(4, job.getTitle());
            statement.setString(5, job.getCompany());
            statement.setString(6, job.getLocation());
            statement.setInt(7, job.isRemote() ? 1 : 0);
            statement.setString(8, job.getDescription());
            statement.setString(9, job.getUrl());
            setNullableDouble(statement, 10, job.getSalaryMin());
            setNullableDouble(statement, 11, job.getSalaryMax());
            statement.setString(12, job.getSalaryCurrency());
            statement.setString(13, job.getPostedDate().toString());
            statement.setString(14, job.getFetchedAt().toString());
            statement.setString(15, job.getStatus().dbValue());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    job.setId(generatedKeys.getLong(1));
                }
            }
        }
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private Job mapRow(ResultSet resultSet) throws SQLException {
        Job job = new Job(
                resultSet.getString("source"),
                resultSet.getString("external_id"),
                resultSet.getString("title"),
                resultSet.getString("company"),
                resultSet.getString("location"),
                resultSet.getInt("remote") == 1,
                resultSet.getString("description"),
                resultSet.getString("url"),
                getNullableDouble(resultSet, "salary_min"),
                getNullableDouble(resultSet, "salary_max"),
                resultSet.getString("salary_currency"),
                LocalDate.parse(resultSet.getString("posted_date")),
                Instant.parse(resultSet.getString("fetched_at")));
        job.setId(resultSet.getLong("id"));
        job.setStatus(JobStatus.fromDbValue(resultSet.getString("status")));
        Double score = getNullableDouble(resultSet, "score");
        if (score != null) {
            job.setScore(score);
        }
        return job;
    }

    private Double getNullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
