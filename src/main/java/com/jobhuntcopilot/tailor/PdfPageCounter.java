package com.jobhuntcopilot.tailor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Counts pages in a PDF without a PDF library (no PDFBox dependency): every page object contains
 * exactly one "/Type/Page" entry (never "/Type/Pages"), whether it sits directly in the file
 * (older, uncompressed xref tables) or inside a FlateDecode-compressed object stream (modern
 * xref-stream PDFs — what Tectonic produces). Inflating every "stream...endstream" block with the
 * JDK's built-in zlib support and counting matches in both the raw bytes and the inflated content
 * finds it either way.
 */
public final class PdfPageCounter {

    private static final Pattern PAGE_TYPE = Pattern.compile("/Type\\s*/Page(?!s)");
    private static final byte[] STREAM_MARKER = "stream".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] END_STREAM_MARKER = "endstream".getBytes(StandardCharsets.US_ASCII);

    private PdfPageCounter() {
    }

    public static int countPages(Path pdfFile) throws IOException {
        byte[] data = Files.readAllBytes(pdfFile);
        int count = countMatches(new String(data, StandardCharsets.ISO_8859_1));

        int searchFrom = 0;
        while (true) {
            int streamStart = indexOf(data, STREAM_MARKER, searchFrom);
            if (streamStart < 0) {
                break;
            }
            int contentStart = skipStreamKeywordNewline(data, streamStart + STREAM_MARKER.length);
            int streamEnd = indexOf(data, END_STREAM_MARKER, contentStart);
            if (streamEnd < 0) {
                break;
            }
            byte[] chunk = Arrays.copyOfRange(data, contentStart, streamEnd);
            byte[] inflated = tryInflate(chunk);
            if (inflated != null) {
                count += countMatches(new String(inflated, StandardCharsets.ISO_8859_1));
            }
            searchFrom = streamEnd + END_STREAM_MARKER.length;
        }
        return count;
    }

    private static int skipStreamKeywordNewline(byte[] data, int index) {
        int i = index;
        if (i < data.length && data[i] == '\r') {
            i++;
        }
        if (i < data.length && data[i] == '\n') {
            i++;
        }
        return i;
    }

    private static byte[] tryInflate(byte[] chunk) {
        Inflater inflater = new Inflater();
        inflater.setInput(chunk);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, chunk.length * 3));
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static int countMatches(String text) {
        Matcher matcher = PAGE_TYPE.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
