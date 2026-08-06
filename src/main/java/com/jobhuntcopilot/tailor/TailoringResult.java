package com.jobhuntcopilot.tailor;

import java.util.List;

public record TailoringResult(TailoringPlan plan, List<TailoringChange> changes) {
}
