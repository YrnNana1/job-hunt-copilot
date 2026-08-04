package com.jobhuntcopilot.fetch;

import com.google.gson.annotations.SerializedName;

/** One posting as returned by Adzuna's /search endpoint. Field names match Adzuna's JSON via @SerializedName. */
public class AdzunaResult {

    private String id;
    private String title;
    private AdzunaCompany company;
    private AdzunaLocation location;
    private String description;

    @SerializedName("redirect_url")
    private String redirectUrl;

    @SerializedName("salary_min")
    private Double salaryMin;

    @SerializedName("salary_max")
    private Double salaryMax;

    /** ISO-8601 timestamp, e.g. "2026-07-28T09:15:00Z". */
    private String created;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public AdzunaCompany getCompany() {
        return company;
    }

    public AdzunaLocation getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public Double getSalaryMin() {
        return salaryMin;
    }

    public Double getSalaryMax() {
        return salaryMax;
    }

    public String getCreated() {
        return created;
    }
}
