package com.jobhuntcopilot.resume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexTextExtractorTest {

    @Test
    void stripsCommandNamesButKeepsArgumentText() {
        String latex = "\\begin{document}\\textbf{Languages}{: Python, Java}\\end{document}";

        String plainText = LatexTextExtractor.toPlainText(latex);

        assertTrue(plainText.contains("Languages"));
        assertTrue(plainText.contains("Python"));
        assertFalse(plainText.contains("\\textbf"));
    }

    @Test
    void ignoresPreambleOutsideTheDocumentEnvironment() {
        String latex = "\\usepackage{fontawesome5}\n\\begin{document}Visible text\\end{document}";

        String plainText = LatexTextExtractor.toPlainText(latex);

        assertTrue(plainText.contains("Visible text"));
        assertFalse(plainText.contains("fontawesome5"));
    }

    @Test
    void stripsCommentsButKeepsEscapedPercentSigns() {
        String latex = "\\begin{document}Kept text % this is a comment and should vanish\n"
                + "Ninety\\% match\\end{document}";

        String plainText = LatexTextExtractor.toPlainText(latex);

        assertTrue(plainText.contains("Kept text"));
        assertFalse(plainText.contains("comment"));
        assertTrue(plainText.contains("Ninety% match"));
    }

    @Test
    void unescapesSpecialCharacters() {
        String latex = "\\begin{document}C\\# and Data \\& Analytics\\end{document}";

        String plainText = LatexTextExtractor.toPlainText(latex);

        assertTrue(plainText.contains("C#"));
        assertTrue(plainText.contains("Data & Analytics"));
    }
}
