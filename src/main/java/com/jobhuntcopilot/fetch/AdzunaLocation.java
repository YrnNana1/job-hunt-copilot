package com.jobhuntcopilot.fetch;

import com.google.gson.annotations.SerializedName;

public class AdzunaLocation {

    @SerializedName("display_name")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }
}
