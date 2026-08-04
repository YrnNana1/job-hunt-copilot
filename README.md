# Job Hunt Copilot

A local desktop app that searches for job postings matching a target list of roles, scores and ranks them by fit, lets me review each posting side-by-side with an AI-tailored version of my resume, and lets me trigger the actual application myself after reviewing it. **It never applies on its own** — every application is human-reviewed and human-submitted.

Built as a personal project to solve my own job search and to learn Java/software architecture along the way — so it's built in small, understandable phases rather than one big drop.

## Why this exists

Job hunting means the same repetitive loop over and over: search several job boards for the same handful of role titles, skim postings to see if they're worth a look, hand-tailor a resume for the ones that are, then fill out the same eligibility/demographic questions on a slightly different form each time. This app automates the tedious, mechanical parts of that loop (searching, scoring, tailoring, form-filling) while keeping a human in the loop for every judgment call — nothing gets submitted without me reading it first.

## Current status: Phase 0 — Repo setup

The project skeleton is in place and builds successfully. No feature logic yet — that starts in Phase 1.

## Tech stack

- **Language:** Java 17
- **GUI:** JavaFX (Phase 4+)
- **Local storage:** SQLite via JDBC (Phase 1+)
- **HTTP:** `java.net.http.HttpClient` (built into the JDK, no extra dependency)
- **Resume tailoring:** Claude API (Anthropic) (Phase 6+)
- **Resume format:** LaTeX (`.tex`) compiled to PDF via [Tectonic](https://tectonic-typesetting.github.io/) (Phase 6+)
- **Browser automation for applying:** Selenium WebDriver, Selenium Manager for driver binaries (Phase 8+)
- **Build tool:** Maven

## Job data sources

Adzuna API, USAJobs API, and the Greenhouse/Lever public job board APIs. Deliberately **not** scraping LinkedIn or Indeed — both prohibit automated scraping and bot-driven applications in their Terms of Service.

## Project layout

```
job-hunt-copilot/
├── config/                          # Role list, scoring weights, blocklist, profile answers (Phase 1+)
├── resources/
│   └── base_resume.tex              # My base LaTeX resume — source of truth for all tailored versions
├── src/
│   ├── main/java/com/jobhuntcopilot/
│   │   └── Main.java                # Entry point
│   └── test/java/com/jobhuntcopilot/
│       └── MainTest.java
├── .env.example                     # Template for API keys — copy to .env and fill in (gitignored)
├── .gitignore
└── pom.xml
```

## How to run it locally

Requires Java 17+ and Maven.

```bash
mvn clean test        # build + run tests
mvn compile exec:java  # run the app
```

At this phase there's no `.env` needed yet — it becomes required starting Phase 2, once the app makes real API calls.

## Roadmap

- [x] **Phase 0** — Repo setup: Maven skeleton, `.gitignore`, README, GitHub repo
- [ ] **Phase 1** — Config + data layer: roles/scoring config, SQLite schema, `Job` model
- [ ] **Phase 2** — Job fetching: Adzuna client, 14-day filter, dedupe, quota logging
- [ ] **Phase 3** — Scoring engine: keyword match, salary, recency, location fit
- [ ] **Phase 4** — GUI list view
- [ ] **Phase 5** — GUI detail view
- [ ] **Phase 6** — Resume tailoring (Claude API + LaTeX→PDF)
- [ ] **Phase 7** — Cover letter generation
- [ ] **Phase 8** — Semi-automated apply flow (Greenhouse/Lever first)
- [ ] **Phase 9** — Application history + CSV export
- [ ] **Phase 10** — Polish: error handling, more tests, screenshots, demo GIF

## What I learned — Phase 0

Getting a Maven project runnable from the command line is mostly about wiring plugins correctly rather than writing code: the `maven-compiler-plugin` pins the Java version, and the `exec-maven-plugin` is what lets `mvn exec:java` run `Main.main()` directly without first packaging a jar — useful for a fast local dev loop before there's a real build/release process to worry about. I also set up the `.gitignore` and `.env.example` split now, before any secrets exist, specifically so it's structurally impossible to accidentally commit an API key or my personal profile answers later — the ignore rule exists before there's anything to ignore.
