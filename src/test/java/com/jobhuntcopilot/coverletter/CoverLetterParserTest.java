package com.jobhuntcopilot.coverletter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real resources/base_cover_letter.tex so a structural change to the template is caught here. */
class CoverLetterParserTest {

    @Test
    void parsesOpeningThreeBodyParagraphsAndClosingInOrder() throws IOException {
        CoverLetterDocument document = parseRealCoverLetter();

        assertEquals("opening", document.opening().id());
        assertTrue(document.opening().text().contains("Virginia Tech"));

        List<CoverLetterParagraph> body = document.bodyParagraphs();
        assertEquals(3, body.size());
        assertEquals("body1", body.get(0).id());
        assertEquals("Technical Impact and Systems Development", body.get(0).heading());
        assertTrue(body.get(0).text().contains("Virtual Sort System"));

        assertEquals("body2", body.get(1).id());
        assertEquals("Professional Experience and Security Expertise", body.get(1).heading());
        assertTrue(body.get(1).text().contains("Keller North America"));

        assertEquals("body3", body.get(2).id());
        assertEquals("Collaboration and Emerging Technologies", body.get(2).heading());
        assertTrue(body.get(2).text().contains("VR Art Gallery"));

        assertEquals("closing", document.closing().id());
        assertTrue(document.closing().text().contains("welcome the opportunity"));
    }

    @Test
    void keepsHeaderSalutationAndSignatureBlockOutsideTheEditableParagraphs() throws IOException {
        CoverLetterDocument document = parseRealCoverLetter();

        assertTrue(document.prefix().contains("Nana Agyemang Prempeh"));
        assertTrue(document.prefix().contains("Dear Hiring Manager,"));
        assertTrue(document.suffix().contains("Sincerely"));

        for (CoverLetterParagraph paragraph : document.bodyParagraphs()) {
            assertFalse(paragraph.text().contains("Dear Hiring Manager"));
        }
    }

    @Test
    void everyParagraphIdIsUnique() throws IOException {
        CoverLetterDocument document = parseRealCoverLetter();

        List<String> ids = new java.util.ArrayList<>();
        ids.add(document.opening().id());
        document.bodyParagraphs().forEach(p -> ids.add(p.id()));
        ids.add(document.closing().id());

        assertEquals(ids.size(), Set.copyOf(ids).size());
    }

    private CoverLetterDocument parseRealCoverLetter() throws IOException {
        return CoverLetterParser.parse(Files.readString(realCoverLetterPath()));
    }

    private Path realCoverLetterPath() {
        return Path.of("resources", "base_cover_letter.tex");
    }
}
