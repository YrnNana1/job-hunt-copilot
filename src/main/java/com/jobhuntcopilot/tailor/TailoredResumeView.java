package com.jobhuntcopilot.tailor;

import java.nio.file.Path;
import java.util.List;

/** What the GUI needs to show: where the compiled PDF lives, the diff/summary, and whether this came from cache. */
public record TailoredResumeView(Path pdfPath, List<TailoringChange> changes, boolean cached) {
}
