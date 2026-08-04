package com.jobhuntcopilot.resume;

import com.jobhuntcopilot.text.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Reads base_resume.tex and turns it into the keyword set that KeywordMatcher compares job postings against. */
public class ResumeKeywordExtractor {

    private ResumeKeywordExtractor() {
    }

    public static Set<String> extractKeywords(Path resumeFile) throws IOException {
        String latex = Files.readString(resumeFile);
        String plainText = LatexTextExtractor.toPlainText(latex);
        return Tokenizer.tokenize(plainText);
    }
}
