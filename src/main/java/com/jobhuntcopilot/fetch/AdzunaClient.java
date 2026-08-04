package com.jobhuntcopilot.fetch;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Talks to Adzuna's job search API (https://developer.adzuna.com/). */
public class AdzunaClient implements AdzunaSearchClient {

    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";
    private static final String COUNTRY = "us";

    private final String appId;
    private final String appKey;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public AdzunaClient(String appId, String appKey) {
        this.appId = appId;
        this.appKey = appKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public AdzunaSearchResponse search(String what, int maxDaysOld, int resultsPerPage) {
        String url = BASE_URL + "/" + COUNTRY + "/search/1"
                + "?app_id=" + encode(appId)
                + "&app_key=" + encode(appKey)
                + "&results_per_page=" + resultsPerPage
                + "&what=" + encode(what)
                + "&max_days_old=" + maxDaysOld
                + "&content-type=application/json";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AdzunaApiException("Adzuna search for \"" + what + "\" failed with HTTP "
                        + response.statusCode() + ": " + snippet(response.body()));
            }
            return gson.fromJson(response.body(), AdzunaSearchResponse.class);
        } catch (JsonSyntaxException e) {
            throw new AdzunaApiException("Could not parse Adzuna's response for \"" + what + "\"", e);
        } catch (IOException e) {
            throw new AdzunaApiException("Network error calling Adzuna for \"" + what + "\": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdzunaApiException("Interrupted while calling Adzuna for \"" + what + "\"", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Keeps error messages short and free of anything sensitive from the response body. */
    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
