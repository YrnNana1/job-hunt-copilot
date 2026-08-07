package com.jobhuntcopilot.apply;

import java.util.regex.Pattern;

/**
 * Identifies which ATS a loaded application page is running on. Takes plain strings (not a
 * WebDriver) specifically so this is unit-testable against fixture URLs/HTML without a browser.
 * Checks the URL first (fast, reliable for postings hosted directly on the ATS's own domain), then
 * falls back to DOM markers for companies that embed the ATS's widget on their own career-site
 * domain instead.
 */
public final class AtsDetector {

    private static final Pattern GREENHOUSE_DOM_MARKER =
            Pattern.compile("id=\"(grnhse_app|application_form)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVER_DOM_MARKER =
            Pattern.compile("class=\"(application-form|lever-)", Pattern.CASE_INSENSITIVE);

    private AtsDetector() {
    }

    public static AtsType detect(String currentUrl, String pageSource) {
        String url = currentUrl == null ? "" : currentUrl.toLowerCase();
        if (url.contains("greenhouse.io")) {
            return AtsType.GREENHOUSE;
        }
        if (url.contains("lever.co")) {
            return AtsType.LEVER;
        }

        String html = pageSource == null ? "" : pageSource;
        if (GREENHOUSE_DOM_MARKER.matcher(html).find()) {
            return AtsType.GREENHOUSE;
        }
        if (LEVER_DOM_MARKER.matcher(html).find()) {
            return AtsType.LEVER;
        }
        return AtsType.UNKNOWN;
    }
}
