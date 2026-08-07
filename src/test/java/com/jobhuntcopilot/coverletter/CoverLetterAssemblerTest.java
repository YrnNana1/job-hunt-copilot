package com.jobhuntcopilot.coverletter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverLetterAssemblerTest {

    private final CoverLetterDocument document = new CoverLetterDocument(
            "PREFIX\nDear Hiring Manager,\n\n",
            new CoverLetterParagraph("opening", null, "Opening paragraph text."),
            List.of(
                    new CoverLetterParagraph("body1", "Heading One", "Body paragraph one text."),
                    new CoverLetterParagraph("body2", "Heading Two", "Body paragraph two text.")),
            new CoverLetterParagraph("closing", null, "Closing paragraph text."),
            "\n\\vspace{0.5cm}\nSincerely, \\\\\nSUFFIX\n\\end{document}\n");

    @Test
    void keepsPrefixSuffixAndHeadingsByteForByte() {
        CoverLetterPlan unchanged = new CoverLetterPlan(
                "Opening paragraph text.",
                List.of(new CoverLetterParagraphPlan("body1", "Body paragraph one text."),
                        new CoverLetterParagraphPlan("body2", "Body paragraph two text.")),
                "Closing paragraph text.");

        String tex = CoverLetterAssembler.assemble(document, unchanged);

        assertTrue(tex.startsWith("PREFIX\nDear Hiring Manager,\n\n"));
        assertTrue(tex.endsWith("\\vspace{0.5cm}\nSincerely, \\\\\nSUFFIX\n\\end{document}\n"));
        assertTrue(tex.contains("\\textbf{Heading One} \\\\\nBody paragraph one text."));
        assertTrue(tex.contains("\\textbf{Heading Two} \\\\\nBody paragraph two text."));
        assertTrue(tex.contains("Opening paragraph text."));
        assertTrue(tex.contains("Closing paragraph text."));
    }

    @Test
    void rewordedAndReorderedBodyParagraphsRenderInThePlanOrderWithNewText() {
        CoverLetterPlan plan = new CoverLetterPlan(
                "Opening paragraph text.",
                List.of(new CoverLetterParagraphPlan("body2", "Body two, reworded for the posting."),
                        new CoverLetterParagraphPlan("body1", "Body paragraph one text.")),
                "Closing paragraph text.");

        String tex = CoverLetterAssembler.assemble(document, plan);

        int rewordedIndex = tex.indexOf("Body two, reworded for the posting.");
        int bodyOneIndex = tex.indexOf("Body paragraph one text.");
        assertTrue(rewordedIndex >= 0);
        assertTrue(rewordedIndex < bodyOneIndex, "reworded/reordered paragraph should render before the other");
        assertFalse(tex.contains("Body paragraph two text."), "original unreworded text should not remain");
        // Heading Two must still be rendered above the reworded body2 text, in the new position.
        assertTrue(tex.indexOf("\\textbf{Heading Two}") < rewordedIndex);
    }

    @Test
    void droppedBodyParagraphIsOmittedEntirelyButOpeningAndClosingAlwaysRemain() {
        CoverLetterPlan plan = new CoverLetterPlan(
                "Opening paragraph text.",
                List.of(new CoverLetterParagraphPlan("body1", "Body paragraph one text.")),
                "Closing paragraph text.");

        String tex = CoverLetterAssembler.assemble(document, plan);

        assertFalse(tex.contains("Heading Two"));
        assertFalse(tex.contains("Body paragraph two text."));
        assertTrue(tex.contains("Heading One"));
        assertTrue(tex.contains("Opening paragraph text."));
        assertTrue(tex.contains("Closing paragraph text."));
    }

    @Test
    void rewordedOpeningAndClosingRenderInsteadOfOriginal() {
        CoverLetterPlan plan = new CoverLetterPlan(
                "Reworded opening.",
                List.of(new CoverLetterParagraphPlan("body1", "Body paragraph one text."),
                        new CoverLetterParagraphPlan("body2", "Body paragraph two text.")),
                "Reworded closing.");

        String tex = CoverLetterAssembler.assemble(document, plan);

        assertTrue(tex.contains("Reworded opening."));
        assertTrue(tex.contains("Reworded closing."));
        assertFalse(tex.contains("Opening paragraph text."));
        assertFalse(tex.contains("Closing paragraph text."));
        // Opening still renders before the salutation-following body, closing still renders last.
        assertTrue(tex.indexOf("Reworded opening.") < tex.indexOf("Heading One"));
        assertTrue(tex.indexOf("Heading Two") < tex.indexOf("Reworded closing."));
    }
}
