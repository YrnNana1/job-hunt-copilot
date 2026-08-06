package com.jobhuntcopilot.tailor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises PdfPageCounter against hand-built byte fixtures rather than real Tectonic output, so
 * these tests stay fast and don't depend on tectonic being installed. The counting logic (raw-byte
 * "/Type/Page" scan plus inflating "stream...endstream" blocks) was independently validated
 * against a real Tectonic-compiled PDF during development.
 */
class PdfPageCounterTest {

    @Test
    void countsPageTypeOccurrencesDirectlyInUncompressedBytes(@TempDir Path tempDir) throws IOException {
        String fake = "junk /Type/Page junk /Type/Page junk /Type/Pages junk";
        Path pdf = writeBytes(tempDir, fake.getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(2, PdfPageCounter.countPages(pdf));
    }

    @Test
    void doesNotCountTypePagesAsAPage(@TempDir Path tempDir) throws IOException {
        Path pdf = writeBytes(tempDir, "/Type/Pages /Kids [3 0 R]".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(0, PdfPageCounter.countPages(pdf));
    }

    @Test
    void countsPageTypeOccurrencesInsideAFlateDecodeCompressedStream(@TempDir Path tempDir) throws IOException {
        byte[] compressed = deflate("<< /Type /ObjStm >> /Type/Page /Type/Page /Type/Page");
        ByteArrayOutputStream fake = new ByteArrayOutputStream();
        fake.write("prefix junk\n".getBytes(StandardCharsets.ISO_8859_1));
        fake.write("stream\n".getBytes(StandardCharsets.ISO_8859_1));
        fake.write(compressed);
        fake.write("\nendstream\nsuffix junk".getBytes(StandardCharsets.ISO_8859_1));
        Path pdf = writeBytes(tempDir, fake.toByteArray());

        assertEquals(3, PdfPageCounter.countPages(pdf));
    }

    @Test
    void combinesRawAndCompressedMatches(@TempDir Path tempDir) throws IOException {
        byte[] compressed = deflate("/Type/Page");
        ByteArrayOutputStream fake = new ByteArrayOutputStream();
        fake.write("/Type/Page\n".getBytes(StandardCharsets.ISO_8859_1));
        fake.write("stream\n".getBytes(StandardCharsets.ISO_8859_1));
        fake.write(compressed);
        fake.write("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
        Path pdf = writeBytes(tempDir, fake.toByteArray());

        assertEquals(2, PdfPageCounter.countPages(pdf));
    }

    private byte[] deflate(String text) {
        Deflater deflater = new Deflater();
        deflater.setInput(text.getBytes(StandardCharsets.ISO_8859_1));
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private Path writeBytes(Path tempDir, byte[] bytes) throws IOException {
        Path file = tempDir.resolve("fake.pdf");
        Files.write(file, bytes);
        return file;
    }
}
