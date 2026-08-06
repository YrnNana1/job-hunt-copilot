package com.jobhuntcopilot.tailor;

import java.util.List;

/**
 * What to render for a tailored resume. {@code experience} is always all of the original
 * Experience entries, in their original order — only bullets change. {@code projects} lists only
 * the KEPT project entries, in the desired display order (dropped projects are simply absent).
 */
public record TailoringPlan(List<EntryPlan> experience, List<EntryPlan> projects) {
}
