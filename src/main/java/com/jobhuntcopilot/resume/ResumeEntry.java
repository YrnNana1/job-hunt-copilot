package com.jobhuntcopilot.resume;

import java.util.List;

/**
 * One `\resumeSubheading`/`\resumeProjectHeading` entry (a job or a project). {@code headerLatex}
 * is the exact original macro call (title/dates/employer or name/dates) — Claude never sees it as
 * editable, so it's copied through byte-for-byte on every tailored resume, regardless of how the
 * entry's bullets are reordered, reworded, or trimmed.
 */
public record ResumeEntry(String id, String headerLatex, List<ResumeBullet> bullets) {
}
