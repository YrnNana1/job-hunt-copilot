package com.jobhuntcopilot.apply;

import java.util.List;

/** What the GUI needs to render the pre-submit review screen after an apply attempt is prepared. */
public record ApplyAttemptView(long attemptId, AtsType atsType, String url, List<FieldMatch> matches) {
}
