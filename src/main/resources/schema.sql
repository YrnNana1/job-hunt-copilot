-- Postings pulled from any source (Adzuna, USAJobs, Greenhouse, Lever).
--
-- Dedupe strategy (see JobRepository):
--   1. dedupe_key ("source:externalId") catches re-fetching the same posting
--      from the same source.
--   2. A separate lookup on (title, company, posted_date) at the app layer
--      catches the same job appearing through two different sources, which
--      will have different dedupe_keys.
CREATE TABLE IF NOT EXISTS jobs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source          TEXT NOT NULL,
    external_id     TEXT NOT NULL,
    dedupe_key      TEXT NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    company         TEXT NOT NULL,
    location        TEXT,
    remote          INTEGER NOT NULL DEFAULT 0,
    description     TEXT,
    url             TEXT,
    salary_min      REAL,
    salary_max      REAL,
    salary_currency TEXT,
    posted_date     TEXT NOT NULL,
    fetched_at      TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'new'
                        CHECK (status IN ('new', 'viewed', 'applied', 'dismissed')),
    score           REAL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_jobs_title_company_posted
    ON jobs (title, company, posted_date);

CREATE INDEX IF NOT EXISTS idx_jobs_status
    ON jobs (status);

-- One row per outbound API call (Adzuna, USAJobs, ...), so quota usage can be
-- inspected later and so JobFetchService can skip re-fetching a search term
-- that was just queried (see JobFetchService's cooldown check).
CREATE TABLE IF NOT EXISTS api_calls (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    endpoint   TEXT NOT NULL,
    params     TEXT NOT NULL,
    called_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_calls_params_called_at
    ON api_calls (params, called_at);

-- One row per posting hard-excluded by EligibilityFilter (seniority title, years of
-- experience, active clearance requirement) — durable and queryable, same style the apply
-- flow's per-attempt log (Phase 8) is meant to follow, specifically so filtering decisions
-- can be spot-checked for over-filtering rather than trusted blindly.
--
-- UNIQUE(source, external_id) makes logging idempotent: JobPipeline re-evaluates eligibility
-- on every list load for whatever's already stored, and "INSERT OR IGNORE" (see
-- EligibilityExclusionRepository) means that doesn't spam a new row every single load.
CREATE TABLE IF NOT EXISTS eligibility_exclusions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    source      TEXT NOT NULL,
    external_id TEXT NOT NULL,
    title       TEXT NOT NULL,
    company     TEXT NOT NULL,
    reason      TEXT NOT NULL CHECK (reason IN ('SENIORITY', 'EXPERIENCE', 'CLEARANCE')),
    detail      TEXT NOT NULL,
    excluded_at TEXT NOT NULL,
    UNIQUE (source, external_id)
);

-- One row per posting a tailored resume has been generated for (Phase 6) — the cache that
-- keeps re-opening the same posting's detail view from burning another Claude API call.
-- The compiled PDF lives on disk (data/tailored-resumes/) — this row keeps the path plus the
-- tailored LaTeX and the change summary (as JSON) that JobDetailView renders as a diff.
CREATE TABLE IF NOT EXISTS tailored_resumes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id       INTEGER NOT NULL REFERENCES jobs(id),
    latex        TEXT NOT NULL,
    pdf_path     TEXT NOT NULL,
    changes_json TEXT NOT NULL,
    model        TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    UNIQUE (job_id)
);

-- One row per posting a cover letter has been generated for (Phase 7) - same caching purpose
-- as tailored_resumes above. The compiled PDF lives on disk (data/cover-letters/) - this row
-- keeps the path plus the tailored LaTeX and the change summary (as JSON).
CREATE TABLE IF NOT EXISTS cover_letters (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id       INTEGER NOT NULL REFERENCES jobs(id),
    latex        TEXT NOT NULL,
    pdf_path     TEXT NOT NULL,
    changes_json TEXT NOT NULL,
    model        TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    UNIQUE (job_id)
);
