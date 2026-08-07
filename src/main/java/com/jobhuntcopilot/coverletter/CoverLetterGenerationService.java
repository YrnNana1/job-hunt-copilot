package com.jobhuntcopilot.coverletter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jobhuntcopilot.db.CoverLetterRepository;
import com.jobhuntcopilot.model.Job;
import com.jobhuntcopilot.tailor.PdfPageCounter;
import com.jobhuntcopilot.tailor.TectonicCompiler;

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
 * Orchestrates cover letter generation for a single posting: parse the base cover letter, ask
 * Claude how to tailor it, reassemble the LaTeX, compile to PDF via Tectonic (reusing the same
 * TectonicCompiler/PdfPageCounter as resume tailoring), and — if the result overflows to a second
 * page — drop the lowest-priority body paragraph and recompile, up to a bounded number of tries.
 * Caches by job id (see CoverLetterRepository) so reopening the same posting's detail view doesn't
 * re-call Claude.
 */
public class CoverLetterGenerationService {

    private static final int MAX_TRIM_ATTEMPTS = 2;
    private static final String MODEL_NAME = "claude-opus-4-5";
    private static final Gson GSON = new Gson();

    private final Path baseCoverLetterPath;
    private final Path outputDir;
    private final ClaudeCoverLetterWriter claudeCoverLetterWriter;
    private final TectonicCompiler tectonicCompiler;
    private final CoverLetterRepository repository;

    public CoverLetterGenerationService(
            Path baseCoverLetterPath, Path outputDir, ClaudeCoverLetterWriter claudeCoverLetterWriter,
            TectonicCompiler tectonicCompiler, CoverLetterRepository repository) {
        this.baseCoverLetterPath = baseCoverLetterPath;
        this.outputDir = outputDir;
        this.claudeCoverLetterWriter = claudeCoverLetterWriter;
        this.tectonicCompiler = tectonicCompiler;
        this.repository = repository;
    }

    public CoverLetterView generate(Job job) throws SQLException {
        Optional<CoverLetterRepository.CoverLetterRecord> cached = repository.findByJobId(job.getId());
        if (cached.isPresent()) {
            List<CoverLetterChange> changes = deserializeChanges(cached.get().changesJson());
            return new CoverLetterView(Path.of(cached.get().pdfPath()), changes, true);
        }

        String baseLatex = readBaseCoverLetter();
        CoverLetterDocument document = CoverLetterParser.parse(baseLatex);
        CoverLetterResult result = claudeCoverLetterWriter.write(job, document);

        List<CoverLetterChange> changes = new ArrayList<>(result.changes());
        String baseName = job.getSource() + "-" + job.getExternalId();
        Path pdfPath = compileWithTrimming(document, result.plan(), changes, baseName);

        String finalLatex = CoverLetterAssembler.assemble(document, result.plan());
        repository.save(job.getId(), finalLatex, pdfPath.toString(), GSON.toJson(changes), MODEL_NAME);

        return new CoverLetterView(pdfPath, changes, false);
    }

    private Path compileWithTrimming(
            CoverLetterDocument document, CoverLetterPlan plan, List<CoverLetterChange> changes, String baseName) {
        CoverLetterPlan currentPlan = plan;
        for (int attempt = 0; ; attempt++) {
            String tex = CoverLetterAssembler.assemble(document, currentPlan);
            Path pdfPath = tectonicCompiler.compile(tex, outputDir, baseName);
            int pageCount = countPagesOrThrow(pdfPath);
            if (pageCount <= 1) {
                return pdfPath;
            }
            if (attempt >= MAX_TRIM_ATTEMPTS || currentPlan.bodyParagraphs().size() <= 1) {
                throw new CoverLetterException("Tailored cover letter compiled to " + pageCount
                        + " pages and doesn't fit on one page even after trimming body paragraphs — try a "
                        + "shorter job description, or trim base_cover_letter.tex manually.");
            }

            CoverLetterParagraphPlan droppedParagraph =
                    currentPlan.bodyParagraphs().get(currentPlan.bodyParagraphs().size() - 1);
            List<CoverLetterParagraphPlan> trimmedBody =
                    currentPlan.bodyParagraphs().subList(0, currentPlan.bodyParagraphs().size() - 1);
            currentPlan = new CoverLetterPlan(currentPlan.openingText(), List.copyOf(trimmedBody), currentPlan.closingText());

            changes.add(new CoverLetterChange(droppedParagraph.paragraphId(),
                    labelForBodyParagraph(document, droppedParagraph.paragraphId()),
                    CoverLetterChange.ChangeType.DROPPED, null, null,
                    "Dropped automatically — the tailored cover letter was " + pageCount
                            + " pages and needed to fit one.",
                    List.of()));
        }
    }

    private String labelForBodyParagraph(CoverLetterDocument document, String paragraphId) {
        return document.bodyParagraphs().stream()
                .filter(paragraph -> paragraph.id().equals(paragraphId))
                .findFirst()
                .map(CoverLetterParagraph::heading)
                .orElse(paragraphId);
    }

    private int countPagesOrThrow(Path pdfPath) {
        try {
            return PdfPageCounter.countPages(pdfPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to count pages in compiled PDF: " + pdfPath, e);
        }
    }

    private String readBaseCoverLetter() {
        try {
            return Files.readString(baseCoverLetterPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read base cover letter: " + baseCoverLetterPath, e);
        }
    }

    private List<CoverLetterChange> deserializeChanges(String json) {
        return GSON.fromJson(json, new TypeToken<List<CoverLetterChange>>() {
        }.getType());
    }
}
