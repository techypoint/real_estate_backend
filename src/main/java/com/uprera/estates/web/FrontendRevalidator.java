package com.uprera.estates.web;

import com.uprera.estates.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Notifies real_estate_frontend's on-demand ISR hook ({@code POST
 * /api/revalidate}) after a blog is created, so the new post is visible on
 * the public site within seconds instead of waiting on that page's own
 * revalidate window (up to an hour — see real_estate_frontend's lib/api.ts).
 *
 * Confirmed missing entirely until 2026-09-27: {@code app.frontend-url} /
 * {@code app.revalidate-secret} were unset in every profile and nothing in
 * this codebase called the endpoint, so a published blog only became
 * visible whenever that page's cache happened to expire on its own. See
 * SOCIAL_CONTENT_PIPELINE.md §5a in agentic_ai_workflow for the incident
 * this was found from.
 *
 * Best-effort: a slow or unreachable frontend must never fail the publish
 * that already succeeded in Mongo. Logs and moves on.
 *
 * Use a literal {@code 127.0.0.1} for local dev, not {@code localhost} —
 * confirmed 2026-09-27: the JDK HttpClient resolving "localhost" against
 * this Next.js dev server intermittently failed with "HTTP/1.1 header
 * parser received no bytes" even though curl against the identical URL
 * succeeded every time; switching the configured URL to the literal loopback
 * address resolved it.
 */
@Component
public class FrontendRevalidator {

    private static final Logger log = LoggerFactory.getLogger(FrontendRevalidator.class);

    private final String frontendUrl;
    private final String secret;
    // HTTP_1_1 pinned, not the default HTTP/2-with-upgrade negotiation:
    // against this Next.js server the client otherwise intermittently threw
    // "HTTP/1.1 header parser received no bytes" even when the request had
    // already been processed successfully server-side (revalidation still
    // happened; only the client's read of the response failed) — confirmed
    // 2026-09-27, see the class comment.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public FrontendRevalidator(AppProperties props) {
        this.frontendUrl = props.frontendUrl();
        this.secret = props.revalidateSecret();
    }

    public void revalidateBlog(String slug) {
        if (!StringUtils.hasText(frontendUrl) || !StringUtils.hasText(secret)) {
            log.debug("Skipping frontend revalidation for blog \"{}\" — app.frontend-url / app.revalidate-secret not configured", slug);
            return;
        }

        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        URI uri = URI.create(base + "/api/revalidate?type=blog&slug=" + slug);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("x-revalidate-secret", secret)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Frontend revalidation for blog \"{}\" returned {}: {}", slug, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Frontend revalidation for blog \"{}\" failed — post is saved, the site will just pick it up on its own cache window instead", slug, e);
        }
    }
}
