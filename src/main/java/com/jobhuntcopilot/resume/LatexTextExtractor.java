package com.jobhuntcopilot.resume;

/**
 * Turns a .tex source file into rough plain text, good enough to tokenize for
 * keyword matching. This is not a real LaTeX parser — it's a handful of
 * regex passes that strip commands and markup while keeping the visible
 * text, which is all keyword matching needs.
 */
public class LatexTextExtractor {

    private LatexTextExtractor() {
    }

    public static String toPlainText(String latexSource) {
        String text = extractDocumentBody(latexSource);
        text = stripComments(text);
        text = unescapeSpecialCharacters(text);
        text = text.replace("\\\\", " "); // LaTeX's own line-break marker
        text = text.replaceAll("\\\\[a-zA-Z]+\\*?", ""); // command names, e.g. \textbf, \section, \item
        text = text.replaceAll("[{}\\[\\]]", " "); // leftover braces/brackets, keeping their contents
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Everything outside \begin{document}...\end{document} is preamble (macros, packages) — not real content. */
    private static String extractDocumentBody(String latex) {
        int start = latex.indexOf("\\begin{document}");
        int end = latex.indexOf("\\end{document}");
        if (start < 0 || end < 0 || end <= start) {
            return latex;
        }
        return latex.substring(start + "\\begin{document}".length(), end);
    }

    private static String stripComments(String latex) {
        StringBuilder result = new StringBuilder();
        for (String line : latex.split("\n", -1)) {
            int commentIndex = indexOfUnescapedPercent(line);
            result.append(commentIndex >= 0 ? line.substring(0, commentIndex) : line).append('\n');
        }
        return result.toString();
    }

    private static int indexOfUnescapedPercent(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '%' && (i == 0 || line.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeSpecialCharacters(String text) {
        return text.replace("\\#", "#")
                .replace("\\&", "&")
                .replace("\\$", "$")
                .replace("\\%", "%")
                .replace("\\_", "_");
    }

    /**
     * The reverse of the special-character unescaping above — used to turn plain text (e.g. a
     * bullet reworded by Claude, which only ever sees/produces plain text, never LaTeX escape
     * sequences) back into valid LaTeX before it's inserted into a .tex source.
     */
    public static String escapeSpecialCharacters(String text) {
        return text.replace("#", "\\#")
                .replace("&", "\\&")
                .replace("$", "\\$")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
