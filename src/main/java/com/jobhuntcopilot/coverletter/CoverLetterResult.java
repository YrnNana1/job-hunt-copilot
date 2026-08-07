package com.jobhuntcopilot.coverletter;

import java.util.List;

/** A cover letter tailoring plan plus the diff/summary of what Claude changed from the base letter. */
public record CoverLetterResult(CoverLetterPlan plan, List<CoverLetterChange> changes) {
}
