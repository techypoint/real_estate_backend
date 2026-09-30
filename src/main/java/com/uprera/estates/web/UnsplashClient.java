package com.uprera.estates.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uprera.estates.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * One free stock photo per topic-digest post, via Unsplash's free API
 * (https://unsplash.com/documentation) — chosen over AI image generation
 * for cost and simplicity (Pipeline A's listing-announcement posts use the
 * real listing's own photos instead; this client is Pipeline B only). See
 * SOCIAL_CONTENT_PIPELINE.md §8.
 *
 * Unsplash's API guidelines (free, including commercial use) require two
 * things this class handles, not just the courtesy of crediting someone:
 * (1) visible attribution — {@link Result#attribution()} must be included
 * wherever the photo is used, not silently dropped; (2) a "download" ping
 * to Unsplash when a photo is actually used, not just browsed — see
 * {@link #pingDownload}.
 *
 * Demo-mode rate limit is 50 req/hour — confirmed 2026-09-28 this is NOT
 * headroom to ignore: a real run (5 keyword searches across 2 topics,
 * sharing the limit with whatever manual testing happened the same hour)
 * hit it mid-run. Before this fix, a rate-limited response and a genuine
 * "no photo matches this topic" were indistinguishable to the caller — both
 * silently returned no url, so the Content Writer reported perfectly
 * good queries ("airport terminal", 1,225 real matches) as if Unsplash had
 * nothing, which isn't true and isn't actionable for a reviewer. See
 * SOCIAL_CONTENT_PIPELINE.md §9b.
 */
@Component
public class UnsplashClient {

    private static final Logger log = LoggerFactory.getLogger(UnsplashClient.class);

    /** error is null on success; otherwise a short machine-readable reason ("rate_limited", "no_match", "not_configured", "unsplash_error", "call_failed") the caller can act on differently. */
    public record Result(String url, String attribution, String attributionUrl, String error) {}

    private final String accessKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public UnsplashClient(AppProperties props, ObjectMapper mapper) {
        this.accessKey = props.unsplashAccessKey();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(accessKey);
    }

    /** Top match for {@code query}. Always returns a Result — check {@code error} rather than expecting null. */
    public Result search(String query) {
        if (!StringUtils.hasText(accessKey)) {
            log.debug("Skipping Unsplash search for \"{}\" — app.unsplash-access-key not configured", query);
            return new Result(null, null, null, "not_configured");
        }

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.unsplash.com/search/photos?query=" + encoded + "&per_page=1");

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Authorization", "Client-ID " + accessKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            String remaining = response.headers().firstValue("X-Ratelimit-Remaining").orElse(null);
            if (response.statusCode() == 403 || "0".equals(remaining)) {
                log.warn("Unsplash rate limit hit on search for \"{}\" (X-Ratelimit-Remaining={}): {}", query, remaining, response.body());
                return new Result(null, null, null, "rate_limited");
            }
            if (response.statusCode() >= 300) {
                log.warn("Unsplash search for \"{}\" returned {}: {}", query, response.statusCode(), response.body());
                return new Result(null, null, null, "unsplash_error");
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode photo = root.path("results").path(0);
            if (photo.isMissingNode()) return new Result(null, null, null, "no_match");

            String url = photo.path("urls").path("regular").asText(null);
            String name = photo.path("user").path("name").asText("Unknown");
            String profileUrl = photo.path("user").path("links").path("html").asText(null);
            if (url == null) return new Result(null, null, null, "no_match");

            pingDownload(photo.path("links").path("download_location").asText(null));

            String attribution = "Photo by " + name + " on Unsplash";
            return new Result(url, attribution, profileUrl, null);
        } catch (Exception e) {
            log.warn("Unsplash search for \"{}\" failed", query, e);
            return new Result(null, null, null, "call_failed");
        }
    }

    /** Best-effort — required by Unsplash's guidelines when a photo is used, but never blocks the caller. */
    private void pingDownload(String downloadLocationUrl) {
        if (!StringUtils.hasText(downloadLocationUrl)) return;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadLocationUrl))
                    .header("Authorization", "Client-ID " + accessKey)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Unsplash download-tracking ping failed (non-fatal)", e);
        }
    }
}
