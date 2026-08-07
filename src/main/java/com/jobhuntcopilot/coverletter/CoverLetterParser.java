package com.jobhuntcopilot.coverletter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses base_cover_letter.tex into a CoverLetterDocument: the salutation-to-signature body split
 * into an opening paragraph, headed body paragraphs, and a closing paragraph — the only content a
 * tailored cover letter is allowed to touch. Everything else (header, salutation, signature block)
 * is captured verbatim.
 *
 * Not a general LaTeX parser (see LatexTextExtractor) — it knows exactly this template's shape
 * (a "Dear ...," salutation, blank-line-separated paragraphs, headed body paragraphs starting with
 * \textbf{...}, and a "\vspace{0.5cm}\nSincerely," signature block) and fails fast if that
 * structure isn't found, rather than silently misparsing a letter that's since diverged from it.
 */
public final class CoverLetterParser {

    private static final Pattern SALUTATION = Pattern.compile("Dear [^\\n]*,\\n\\n");
    private static final String CLOSING_MARKER = "\n\\vspace{0.5cm}\nSincerely,";
    private static final String HEADING_OPEN = "\\textbf{";

    private CoverLetterParser() {
    }

    public static CoverLetterDocument parse(String latex) {
        Matcher salutationMatcher = SALUTATION.matcher(latex);
        if (!salutationMatcher.find()) {
            throw new IllegalStateException(
                    "base_cover_letter.tex is missing expected structure: a \"Dear ...,\" salutation line");
        }
        String prefix = latex.substring(0, salutationMatcher.end());

        int closingStart = latex.indexOf(CLOSING_MARKER, salutationMatcher.end());
        if (closingStart < 0) {
            throw new IllegalStateException(
                    "base_cover_letter.tex is missing expected structure: the closing \"Sincerely,\" block");
        }
        String suffix = latex.substring(closingStart);

        String body = latex.substring(salutationMatcher.end(), closingStart).strip();
        List<String> blocks = splitParagraphs(body);
        if (blocks.size() < 3) {
            throw new IllegalStateException(
                    "base_cover_letter.tex body must have an opening paragraph, at least one body "
                            + "paragraph, and a closing paragraph");
        }

        CoverLetterParagraph opening = new CoverLetterParagraph("opening", null, blocks.get(0).strip());
        CoverLetterParagraph closing =
                new CoverLetterParagraph("closing", null, blocks.get(blocks.size() - 1).strip());

        List<CoverLetterParagraph> bodyParagraphs = new ArrayList<>();
        int bodyNumber = 1;
        for (int i = 1; i < blocks.size() - 1; i++) {
            bodyParagraphs.add(parseBodyParagraph(blocks.get(i), "body" + bodyNumber));
            bodyNumber++;
        }

        return new CoverLetterDocument(prefix, opening, bodyParagraphs, closing, suffix);
    }

    private static List<String> splitParagraphs(String body) {
        List<String> blocks = new ArrayList<>();
        for (String block : body.split("\\n\\s*\\n")) {
            if (!block.isBlank()) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static CoverLetterParagraph parseBodyParagraph(String block, String id) {
        String trimmed = block.strip();
        if (!trimmed.startsWith(HEADING_OPEN)) {
            throw new IllegalStateException(
                    "Expected a \\textbf{...} heading at the start of body paragraph " + id);
        }
        int closeBrace = findMatchingBrace(trimmed, HEADING_OPEN.length() - 1);
        String heading = trimmed.substring(HEADING_OPEN.length(), closeBrace);
        String rest = trimmed.substring(closeBrace + 1).strip();
        if (rest.startsWith("\\\\")) {
            rest = rest.substring(2).strip();
        }
        return new CoverLetterParagraph(id, heading, rest);
    }

    private static int findMatchingBrace(String text, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unmatched '{' in a body paragraph heading");
    }
}
