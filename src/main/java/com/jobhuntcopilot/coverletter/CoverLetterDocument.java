package com.jobhuntcopilot.coverletter;

import java.util.List;

/**
 * base_cover_letter.tex split into the parts a tailored cover letter is allowed to touch. The
 * header (name/contact block), salutation, and closing signature block are captured verbatim in
 * {@code prefix}/{@code suffix} and never change. {@code opening} and {@code closing} are the
 * unheaded paragraphs that must stay first/last but may be reworded; {@code bodyParagraphs} are
 * the headed paragraphs in between, which may be reworded, reordered, or dropped.
 */
public record CoverLetterDocument(
        String prefix,
        CoverLetterParagraph opening,
        List<CoverLetterParagraph> bodyParagraphs,
        CoverLetterParagraph closing,
        String suffix) {
}
