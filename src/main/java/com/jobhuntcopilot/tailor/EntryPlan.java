package com.jobhuntcopilot.tailor;

import java.util.List;

/** A job/project entry's final bullet list — order is the desired display order among kept bullets. */
public record EntryPlan(String entryId, List<BulletPlan> bullets) {
}
