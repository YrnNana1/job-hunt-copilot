package com.jobhuntcopilot.apply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtsDetectorTest {

    @Test
    void detectsGreenhouseByUrl() {
        assertEquals(AtsType.GREENHOUSE,
                AtsDetector.detect("https://boards.greenhouse.io/acme/jobs/12345", "<html></html>"));
    }

    @Test
    void detectsLeverByUrl() {
        assertEquals(AtsType.LEVER, AtsDetector.detect("https://jobs.lever.co/acme/abc-123", "<html></html>"));
    }

    @Test
    void detectsGreenhouseByDomMarkerWhenEmbeddedOnACompanyDomain() {
        String html = "<html><body><div id=\"grnhse_app\">...</div></body></html>";
        assertEquals(AtsType.GREENHOUSE, AtsDetector.detect("https://careers.acme.com/apply", html));
    }

    @Test
    void detectsLeverByDomMarkerWhenEmbeddedOnACompanyDomain() {
        String html = "<html><body><div class=\"application-form\">...</div></body></html>";
        assertEquals(AtsType.LEVER, AtsDetector.detect("https://careers.acme.com/apply", html));
    }

    @Test
    void returnsUnknownForAnUnrelatedSite() {
        assertEquals(AtsType.UNKNOWN,
                AtsDetector.detect("https://careers.acme.com/apply", "<html><body>Apply here</body></html>"));
    }

    @Test
    void returnsUnknownForNullInputs() {
        assertEquals(AtsType.UNKNOWN, AtsDetector.detect(null, null));
    }
}
