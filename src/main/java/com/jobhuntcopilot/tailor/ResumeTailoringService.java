package com.jobhuntcopilot.tailor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jobhuntcopilot.db.TailoredResumeRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.resume.LatexTextExtractor;
import com.jobhuntcopilot.resume.ResumeAssembler;
import com.jobhuntcopilot.resume.ResumeDocument;
import com.jobhuntcopilot.resume.ResumeParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates resume tailoring for a single posting: parse the base resume, ask Claude how to
 * tailor it, reassemble the LaTeX, compile to PDF via Tectonic, and — if the result overflows to a
 * second page — drop the lowest-priority project and recompile, up to a bounded number of tries,
 * rather than silently shrinking formatting or breaking the one-page layout. Caches by job id (see
 * TailoredResumeRepository) so reopening the same posting's detail view doesn't re-call Claude.
 */
public class ResumeTailoringService {

    private static final int MAX_TRIM_ATTEMPTS = 2;
    private static final String MODEL_NAME = "claude-opus-4-5";
    private static final Gson GSON = new Gson();

    private final Path baseResumePath;
    private final Path outputDir;
    private final ClaudeResumeTailor claudeResumeTailor;
    private final TectonicCompiler tectonicCompiler;
    private final TailoredResumeRepository repository;

    public ResumeTailoringService(
            Path baseResumePath, Path outputDir, ClaudeResumeTailor claudeResumeTailor,
            TectonicCompiler tectonicCompiler, TailoredResumeRepository repository) {
        this.baseResumePath = baseResumePath;
        this.outputDir = outputDir;
        this.claudeResumeTailor = claudeResumeTailor;
        this.tectonicCompiler = tectonicCompiler;
        this.repository = repository;
    }

    public TailoredResumeView tailor(Job job) throws SQLException {
        Optional<TailoredResumeRepository.TailoredResumeRecord> cached = repository.findByJobId(job.getId());
        if (cached.isPresent()) {
            List<TailoringChange> changes = deserializeChanges(cached.get().changesJson());
            return new TailoredResumeView(Path.of(cached.get().pdfPath()), changes, true);
        }

        String baseLatex = readBaseResume();
        ResumeDocument document = ResumeParser.parse(baseLatex);
        TailoringResult result = claudeResumeTailor.tailor(job, document);

        List<TailoringChange> changes = new ArrayList<>(result.changes());
        String baseName = job.getSource() + "-" + job.getExternalId();
        Path pdfPath = compileWithTrimming(document, result.plan(), changes, baseName);

        String finalLatex = ResumeAssembler.assemble(document, result.plan());
        repository.save(job.getId(), finalLatex, pdfPath.toString(), GSON.toJson(changes), MODEL_NAME);

        return new TailoredResumeView(pdfPath, changes, false);
    }

    private Path compileWithTrimming(
            ResumeDocument document, TailoringPlan plan, List<TailoringChange> changes, String baseName) {
        TailoringPlan currentPlan = plan;
        for (int attempt = 0; ; attempt++) {
            String tex = ResumeAssembler.assemble(document, currentPlan);
            Path pdfPath = tectonicCompiler.compile(tex, outputDir, baseName);
            int pageCount = countPagesOrThrow(pdfPath);
            if (pageCount <= 1) {
                return pdfPath;
            }
            if (attempt >= MAX_TRIM_ATTEMPTS || currentPlan.projects().size() <= 1) {
                throw new TailoringException("Tailored resume compiled to " + pageCount
                        + " pages and doesn't fit on one page even after trimming projects — try a shorter "
                        + "job description, or trim base_resume.tex manually.");
            }

            EntryPlan droppedProject = currentPlan.projects().get(currentPlan.projects().size() - 1);
            List<EntryPlan> trimmedProjects =
                    currentPlan.projects().subList(0, currentPlan.projects().size() - 1);
            currentPlan = new TailoringPlan(currentPlan.experience(), List.copyOf(trimmedProjects));

            changes.add(new TailoringChange("Technical Projects", labelForProjectEntry(document, droppedProject.entryId()),
                    null, TailoringChange.ChangeType.ENTRY_DROPPED, null, null,
                    "Dropped automatically — the tailored resume was " + pageCount + " pages and needed to fit one.",
                    List.of()));
        }
    }

    private String labelForProjectEntry(ResumeDocument document, String entryId) {
        return document.projectEntries().stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .map(entry -> LatexTextExtractor.toPlainText(entry.headerLatex()))
                .orElse(entryId);
    }

    private int countPagesOrThrow(Path pdfPath) {
        try {
            return PdfPageCounter.countPages(pdfPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to count pages in compiled PDF: " + pdfPath, e);
        }
    }

    private String readBaseResume() {
        try {
            return Files.readString(baseResumePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read base resume: " + baseResumePath, e);
        }
    }

    private List<TailoringChange> deserializeChanges(String json) {
        return GSON.fromJson(json, new TypeToken<List<TailoringChange>>() {
        }.getType());
    }
}
