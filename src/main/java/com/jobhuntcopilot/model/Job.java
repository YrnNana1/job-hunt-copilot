package com.jobhuntcopilot.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single job posting, whichever source it came from.
 *
 * {@code id} is null until the job has been saved to the database — the
 * repository assigns it after insert. {@code status} and {@code score}
 * start out at their defaults and are updated in place as I review the
 * posting and the scoring engine (Phase 3) evaluates it.
 */
public class Job {

    private Long id;
    private final String source;
    private final String externalId;
    private final String title;
    private final String company;
    private final String location;
    private final boolean remote;
    private final String description;
    private final String url;
    private final Double salaryMin;
    private final Double salaryMax;
    private final String salaryCurrency;
    private final LocalDate postedDate;
    private final Instant fetchedAt;
    private JobStatus status;
    private Double score;

    public Job(
            String source,
            String externalId,
            String title,
            String company,
            String location,
            boolean remote,
            String description,
            String url,
            Double salaryMin,
            Double salaryMax,
            String salaryCurrency,
            LocalDate postedDate,
            Instant fetchedAt) {
        this.source = Objects.requireNonNull(source, "source");
        this.externalId = Objects.requireNonNull(externalId, "externalId");
        this.title = Objects.requireNonNull(title, "title");
        this.company = Objects.requireNonNull(company, "company");
        this.location = location;
        this.remote = remote;
        this.description = description;
        this.url = url;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.salaryCurrency = salaryCurrency;
        this.postedDate = Objects.requireNonNull(postedDate, "postedDate");
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
        this.status = JobStatus.NEW;
        this.score = null;
    }

    /** "source:externalId" — the primary dedupe key, unique per posting per source. See JobRepository. */
    public String dedupeKey() {
        return source + ":" + externalId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public boolean isRemote() {
        return remote;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public Double getSalaryMin() {
        return salaryMin;
    }

    public Double getSalaryMax() {
        return salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "Job{id=%s, source='%s', title='%s', company='%s', status=%s}"
                .formatted(id, source, title, company, status);
    }
}
