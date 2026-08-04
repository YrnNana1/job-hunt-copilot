# Job Hunt Copilot

A local desktop app that searches for job postings matching a target list of roles, scores and ranks them by fit, lets me review each posting side-by-side with an AI-tailored version of my resume, and lets me trigger the actual application myself after reviewing it. **It never applies on its own** — every application is human-reviewed and human-submitted.

Built as a personal project to solve my own job search and to learn Java/software architecture along the way — so it's built in small, understandable phases rather than one big drop.

## Why this exists

Job hunting means the same repetitive loop over and over: search several job boards for the same handful of role titles, skim postings to see if they're worth a look, hand-tailor a resume for the ones that are, then fill out the same eligibility/demographic questions on a slightly different form each time. This app automates the tedious, mechanical parts of that loop (searching, scoring, tailoring, form-filling) while keeping a human in the loop for every judgment call — nothing gets submitted without me reading it first.

## Current status: Phase 3 — Scoring engine

Every stored posting now gets a 0-100 fit score with a full, human-readable breakdown: skill/keyword match against my actual resume, salary fit against my target range, recency, and location fit — each shown with its point contribution and the reasoning behind it, not just a final number. No GUI yet; it's a console app for now.

## Tech stack

- **Language:** Java 17
- **GUI:** JavaFX (Phase 4+)
- **Local storage:** SQLite via JDBC (`org.xerial:sqlite-jdbc`)
- **Config parsing:** Gson (reads `config/roles.json` and `config/blocklist.json`)
- **HTTP:** `java.net.http.HttpClient` (built into the JDK, no extra dependency)
- **Resume tailoring:** Claude API (Anthropic) (Phase 6+)
- **Resume format:** LaTeX (`.tex`) compiled to PDF via [Tectonic](https://tectonic-typesetting.github.io/) (Phase 6+)
- **Browser automation for applying:** Selenium WebDriver, Selenium Manager for driver binaries (Phase 8+)
- **Build tool:** Maven

## Job data sources

**Adzuna API** is live (Phase 2). USAJobs and the Greenhouse/Lever public job board APIs are planned for later phases. Deliberately **not** scraping LinkedIn or Indeed — both prohibit automated scraping and bot-driven applications in their Terms of Service.

Adzuna's free tier is ~1,000 calls/month (~33/day), so the fetcher is careful with it: one page per search term (~6 calls per full run), and a 6-hour cooldown that skips re-fetching a term if it was already queried recently. Every call is logged to the `api_calls` table so quota usage is visible over time.

## Scoring

Every posting gets a 0-100 score, computed as four weighted factors (weights live in `config/roles.json` and are configurable):

| Factor | Weight | What it measures |
|---|---|---|
| Skill/keyword match | 35% | Resume keyword overlap (real words extracted from `base_resume.tex`) + job title vs. configured search terms |
| Salary fit | 30% | Posting's salary vs. my minimum acceptable ($80k) and target range ($85k-90k+) |
| Recency | 20% | Linear decay from 1.0 (posted today) to 0.0 (at the 14-day cutoff) |
| Location fit | 15% | Remote, or a preferred metro (VA/MD/NC/TX/DC/Atlanta/Seattle/FL) scores highest; anywhere else still scores reasonably, since I'm open to relocating |

The keyword matcher doesn't use a hand-maintained skills list — it strips `base_resume.tex` down to plain text (stripping LaTeX commands, keeping visible content) and tokenizes the whole thing, so the resume file stays the single source of truth. Nothing is a hard filter here: a posting below my salary floor or outside my preferred metros still gets scored and shown, just lower — dismissing is a manual decision (Phase 4+), not something the scorer does for me.

Every score comes with a breakdown, not just the number — see `ScoreFactor`/`ScoreBreakdown` in `src/main/java/com/jobhuntcopilot/score/`, printed in full for the top 5 postings each run.

## Project layout

```
job-hunt-copilot/
├── config/
│   ├── roles.json                   # Search terms, location/remote pref, recency rule, scoring weights
│   └── blocklist.json               # Companies to filter out before scoring
├── resources/
│   └── base_resume.tex              # My base LaTeX resume — source of truth for all tailored versions
├── src/
│   ├── main/
│   │   ├── java/com/jobhuntcopilot/
│   │   │   ├── Main.java            # Entry point
│   │   │   ├── config/              # Config records, ConfigLoader (Gson), EnvLoader (.env parsing)
│   │   │   ├── model/               # Job, JobStatus
│   │   │   ├── db/                  # Database (schema init), JobRepository, ApiCallRepository
│   │   │   ├── fetch/               # AdzunaClient, JobFetchService, FetchSummary
│   │   │   ├── text/                # Tokenizer — shared keyword tokenization
│   │   │   ├── resume/              # LatexTextExtractor, ResumeKeywordExtractor
│   │   │   └── score/                # KeywordMatcher, *Scorer classes, ScoringEngine, ScoreBreakdown
│   │   └── resources/
│   │       └── schema.sql           # SQLite DDL: jobs table, api_calls table
│   └── test/java/com/jobhuntcopilot/   # Mirrors main/ — one test class per component above
├── .env.example                     # Template for API keys — copy to .env and fill in (gitignored)
├── .gitignore
└── pom.xml
```

`data/jobhunt.db` (the actual SQLite file) is created on first run and is gitignored — it's local runtime state, not source.

## How to run it locally

Requires Java 17+ and Maven.

1. Copy `.env.example` to `.env` and fill in `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` (free from [developer.adzuna.com](https://developer.adzuna.com/)).
2. Run it:

```bash
mvn clean test         # build + run tests (no network calls, no quota used)
mvn compile exec:java  # run the app — fetches real postings from Adzuna
```

Running the app fetches postings for every search term in `config/roles.json`, filters out anything blocklisted or older than 14 days, dedupes against what's already stored, and prints a per-term summary plus quota usage. Re-running within 6 hours of the last fetch for a given term skips it instead of re-querying.

### Editing the config

`config/roles.json` and `config/blocklist.json` are meant to be hand-edited as my search evolves — no code changes needed to add a role, tweak a scoring weight, add a preferred metro, or block a company.

## Roadmap

- [x] **Phase 0** — Repo setup: Maven skeleton, `.gitignore`, README, GitHub repo
- [x] **Phase 1** — Config + data layer: roles/scoring config, SQLite schema, `Job` model
- [x] **Phase 2** — Job fetching: Adzuna client, 14-day filter, dedupe, quota logging
- [x] **Phase 3** — Scoring engine: keyword match, salary, recency, location fit
- [ ] **Phase 4** — GUI list view
- [ ] **Phase 5** — GUI detail view
- [ ] **Phase 6** — Resume tailoring (Claude API + LaTeX→PDF)
- [ ] **Phase 7** — Cover letter generation
- [ ] **Phase 8** — Semi-automated apply flow (Greenhouse/Lever first)
- [ ] **Phase 9** — Application history + CSV export
- [ ] **Phase 10** — Polish: error handling, more tests, screenshots, demo GIF

## What I learned — Phase 3

The keyword matching only became trustworthy after I actually looked at its output. My first version tokenized the whole resume and job postings and counted overlapping words — technically correct, but the very first live run's top match showed matched keywords like "including," "detailed," "during," and "experience." Those are real words, but they show up in almost every resume and every job posting regardless of actual fit, so they were diluting the score instead of explaining it. The fix wasn't a smarter algorithm, it was a better stopword list — I added a second tier of "resume/job-posting boilerplate" words (experience, skills, technical, required, years, and a few others) on top of ordinary English stopwords like "the" and "and." After that, the same top posting's matches became "ai, machine, learning, engineer, infrastructure, lead" — actually meaningful. This was a good reminder that "transparent scoring" isn't just about exposing a formula; the breakdown has to survive being read, or it doesn't actually build trust.

The other thing worth noting: I caught a real bug through reasoning before it shipped, not through a failing test. My first pass at location matching used `location.contains("VA")` — a plain substring check. "Las Vegas, NV" contains the substring "va" (inside "Vegas"), which would have wrongly scored a Nevada posting as matching my Virginia preference. Switching to token-based matching (tokenize the location string, tokenize the metro name, require an exact token match) fixed it, and `LocationScorerTest.abbreviationDoesNotFalsePositiveInsideAnUnrelatedWord` locks in the fix so it can't silently regress.

## What I learned — Phase 2

The Phase 1 dedupe logic proved itself sooner than expected — not across sources, but *within a single run*. Adzuna's search does fuzzy matching, so a "Solutions Engineer" query and a "Technical Consultant" query sometimes return the very same listing (a hybrid role matches both). On the first live run, the "Solutions Engineer" term fetched 20 results but only 4 were actually new — 16 were already in the database from an earlier term in the same run. The title+company+posted-date fallback caught every one of them without me writing any run-specific logic for it, which was a nice confirmation that dedupe belongs at the repository layer, not scattered through the fetcher.

The other real decision was testability: `JobFetchService` depends on an `AdzunaSearchClient` interface, not the concrete `AdzunaClient`, so `JobFetchServiceTest` runs against a fake that returns canned responses instead of hitting the live API. That matters for a free-tier API — without it, every `mvn test` run would burn real Adzuna quota and eventually just start failing in CI with no internet access. The fake's responses are built with the same Gson classes the real client uses (`AdzunaSearchResponse.class`), so the fixtures look like real API payloads instead of hand-rolled test doubles that could drift from what Adzuna actually returns.

## What I learned — Phase 1

Dedupe turned out to need two different checks, not one. My first instinct was "just make a column unique" — but a posting can repeat two different ways: the *same* source re-sending the *same* posting (easy — `source` + `externalId` is a natural unique key), and the *same job* showing up through *two different sources* with unrelated IDs (e.g. Adzuna's crawler finds a listing, and later I also pull it directly from the company's Greenhouse board). No single column can catch both, so `JobRepository.save()` does two lookups before inserting: an exact match on `dedupe_key` (`source:externalId`), then a fallback match on `title` + `company` + `postedDate`. Getting this right at the schema/repository layer now means Phase 2's fetcher can just call `save()` per posting and not think about dedupe logic itself.

I also chose records for the config types (`RolesConfig`, `ScoringWeights`, etc.) but a plain mutable class for `Job` — the config is read once and never changes, so immutability is free; a `Job` gets its `id` assigned by the database after insert and its `status`/`score` updated in place as I review postings, so it's a genuinely mutable entity and a record would've fought that.

## What I learned — Phase 0

Getting a Maven project runnable from the command line is mostly about wiring plugins correctly rather than writing code: the `maven-compiler-plugin` pins the Java version, and the `exec-maven-plugin` is what lets `mvn exec:java` run `Main.main()` directly without first packaging a jar — useful for a fast local dev loop before there's a real build/release process to worry about. I also set up the `.gitignore` and `.env.example` split now, before any secrets exist, specifically so it's structurally impossible to accidentally commit an API key or my personal profile answers later — the ignore rule exists before there's anything to ignore.
