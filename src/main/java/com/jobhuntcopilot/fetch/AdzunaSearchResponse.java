package com.jobhuntcopilot.fetch;

import java.util.List;

public class AdzunaSearchResponse {

    private int count;
    private List<AdzunaResult> results;

    public int getCount() {
        return count;
    }

    public List<AdzunaResult> getResults() {
        return results == null ? List.of() : results;
    }
}
