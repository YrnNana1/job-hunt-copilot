package com.jobhuntcopilot.resume;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses base_resume.tex into a ResumeDocument: the Experience and Technical Projects sections
 * split into entries/bullets (the only content a tailored resume is allowed to touch), with
 * everything else — preamble, header, Education, Certifications, Technical Skills, Leadership —
 * captured verbatim.
 *
 * Not a general LaTeX parser (see LatexTextExtractor) — it knows exactly the macros this resume's
 * template uses (\resumeSubheading, \resumeProjectHeading, \resumeItem) and fails fast if they
 * aren't found in the expected shape, rather than silently misparsing a resume that's since
 * diverged from this structure.
 */
public class ResumeParser {

    private static final String EXPERIENCE_SECTION = "\\section{Experience}";
    private static final String PROJECTS_SECTION = "\\section{Technical Projects}";
    private static final String NEXT_SECTION_MARKER = "\\section{";
    private static final String SUBHEADING = "\\resumeSubheading";
    private static final String PROJECT_HEADING = "\\resumeProjectHeading";
    private static final String ITEM_LIST_START = "\\resumeItemListStart";
    private static final String ITEM_LIST_END = "\\resumeItemListEnd";
    private static final String ITEM_OPEN = "\\resumeItem{";

    private ResumeParser() {
    }

    public static ResumeDocument parse(String latex) {
        int experienceSectionStart = indexOfOrThrow(latex, EXPERIENCE_SECTION, 0, "\\section{Experience}");
        int projectsSectionStart = indexOfOrThrow(latex, PROJECTS_SECTION, experienceSectionStart,
                "\\section{Technical Projects}");

        int firstExperienceEntry = indexOfOrThrow(latex, SUBHEADING, experienceSectionStart,
                "a \\resumeSubheading entry in the Experience section");
        String prefix = latex.substring(0, firstExperienceEntry);

        ParsedEntries experience = parseEntries(latex, firstExperienceEntry, projectsSectionStart,
                SUBHEADING, 4, "exp");

        int firstProjectEntry = indexOfOrThrow(latex, PROJECT_HEADING, projectsSectionStart,
                "a \\resumeProjectHeading entry in the Technical Projects section");
        String betweenExperienceAndProjects = latex.substring(experience.endOffset, firstProjectEntry);

        int projectsSectionBoundary = findNextSectionOrEnd(latex, projectsSectionStart + PROJECTS_SECTION.length());
        ParsedEntries projects = parseEntries(latex, firstProjectEntry, projectsSectionBoundary,
                PROJECT_HEADING, 2, "proj");

        String suffix = latex.substring(projects.endOffset);

        return new ResumeDocument(prefix, experience.entries, betweenExperienceAndProjects, projects.entries, suffix);
    }

    private record ParsedEntries(List<ResumeEntry> entries, int endOffset) {
    }

    private static ParsedEntries parseEntries(
            String latex, int firstEntryStart, int sectionBoundary, String macroName, int headerGroupCount,
            String idPrefix) {
        List<ResumeEntry> entries = new ArrayList<>();
        int cursor = firstEntryStart;
        int entryNumber = 1;
        while (cursor < sectionBoundary) {
            if (!latex.startsWith(macroName, cursor)) {
                throw new IllegalStateException(
                        "Expected " + macroName + " at index " + cursor + " while parsing base_resume.tex");
            }
            int headerEnd = cursor + macroName.length();
            for (int i = 0; i < headerGroupCount; i++) {
                int openBrace = indexOfNextNonWhitespace(latex, headerEnd, '{');
                headerEnd = findMatchingBrace(latex, openBrace) + 1;
            }
            String headerLatex = latex.substring(cursor, headerEnd);

            int listStart = indexOfOrThrow(latex, ITEM_LIST_START, headerEnd,
                    "\\resumeItemListStart after " + macroName + " header at index " + cursor);
            int listEnd = indexOfOrThrow(latex, ITEM_LIST_END, listStart,
                    "\\resumeItemListEnd after \\resumeItemListStart at index " + listStart);

            List<ResumeBullet> bullets = parseBullets(latex, listStart, listEnd, idPrefix + entryNumber);
            int entryEnd = listEnd + ITEM_LIST_END.length();

            entries.add(new ResumeEntry(idPrefix + entryNumber, headerLatex, bullets));
            entryNumber++;

            int nextEntry = latex.indexOf(macroName, entryEnd);
            if (nextEntry < 0 || nextEntry >= sectionBoundary) {
                return new ParsedEntries(entries, entryEnd);
            }
            cursor = nextEntry;
        }
        throw new IllegalStateException("Ran past the section boundary while parsing " + macroName + " entries");
    }

    private static List<ResumeBullet> parseBullets(String latex, int from, int to, String entryId) {
        List<ResumeBullet> bullets = new ArrayList<>();
        int cursor = from;
        int bulletNumber = 1;
        while (true) {
            int itemStart = latex.indexOf(ITEM_OPEN, cursor);
            if (itemStart < 0 || itemStart >= to) {
                break;
            }
            int openBrace = itemStart + ITEM_OPEN.length() - 1;
            int closeBrace = findMatchingBrace(latex, openBrace);
            String text = latex.substring(openBrace + 1, closeBrace);
            bullets.add(new ResumeBullet(entryId + "-b" + bulletNumber, text));
            bulletNumber++;
            cursor = closeBrace + 1;
        }
        if (bullets.isEmpty()) {
            throw new IllegalStateException("No \\resumeItem bullets found for entry " + entryId);
        }
        return bullets;
    }

    private static int findNextSectionOrEnd(String latex, int from) {
        int next = latex.indexOf(NEXT_SECTION_MARKER, from);
        return next < 0 ? latex.length() : next;
    }

    private static int indexOfNextNonWhitespace(String latex, int from, char expected) {
        int i = from;
        while (i < latex.length() && Character.isWhitespace(latex.charAt(i))) {
            i++;
        }
        if (i >= latex.length() || latex.charAt(i) != expected) {
            throw new IllegalStateException(
                    "Expected '" + expected + "' at or after index " + from + " while parsing base_resume.tex");
        }
        return i;
    }

    private static int findMatchingBrace(String latex, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < latex.length(); i++) {
            char c = latex.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unmatched '{' starting at index " + openBraceIndex);
    }

    private static int indexOfOrThrow(String latex, String needle, int from, String description) {
        int index = latex.indexOf(needle, from);
        if (index < 0) {
            throw new IllegalStateException(
                    "base_resume.tex is missing expected structure: " + description);
        }
        return index;
    }
}
