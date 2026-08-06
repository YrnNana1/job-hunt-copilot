package com.jobhuntcopilot.tailor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Compiles a LaTeX source string to PDF via the tectonic CLI (installed in Phase 0, used here for the first time). */
public class TectonicCompiler {

    private static final long TIMEOUT_SECONDS = 120;

    public Path compile(String latexSource, Path workDir, String baseName) {
        try {
            Files.createDirectories(workDir);
            Path texFile = workDir.resolve(baseName + ".tex");
            Files.writeString(texFile, latexSource, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("tectonic", texFile.getFileName().toString())
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TectonicCompilationException("tectonic timed out after " + TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new TectonicCompilationException("tectonic failed:\n" + lastLines(output, 40));
            }

            Path pdfFile = workDir.resolve(baseName + ".pdf");
            if (!Files.exists(pdfFile)) {
                throw new TectonicCompilationException(
                        "tectonic reported success but produced no PDF:\n" + lastLines(output, 40));
            }
            return pdfFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to run tectonic", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TectonicCompilationException("Interrupted while running tectonic", e);
        }
    }

    private String lastLines(String output, int maxLines) {
        List<String> lines = output.lines().toList();
        int from = Math.max(0, lines.size() - maxLines);
        return String.join("\n", lines.subList(from, lines.size()));
    }
}
