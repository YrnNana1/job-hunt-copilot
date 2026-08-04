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
