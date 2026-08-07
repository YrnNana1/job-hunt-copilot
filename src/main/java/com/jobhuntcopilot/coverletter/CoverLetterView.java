package com.jobhuntcopilot.coverletter;

import java.nio.file.Path;
import java.util.List;

/** What the GUI needs to show: where the compiled PDF lives, the diff/summary, and whether this came from cache. */
public record CoverLetterView(Path pdfPath, List<CoverLetterChange> changes, boolean cached) {
}
