package com.uprera.estates.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A marketing blog post, written by the content pipeline in agentic_ai_workflow
 * (see SOCIAL_CONTENT_PIPELINE.md there) and approved by a human before it
 * reaches this collection. Unlike {@code projects}, this schema is ours and
 * small, so it's mapped onto a typed record rather than read as a raw
 * Document — see ProjectRepository's class comment for why that's not always
 * the right call.
 *
 * {@code slug} has a unique index created manually in Mongo (this app runs
 * with {@code spring.data.mongodb.auto-index-creation=false} — indexes are
 * owned outside the app, same convention as the importer-owned indexes on
 * {@code projects}), not via a {@code @Indexed} annotation here.
 */
@Document(collection = "blogs")
public record Blog(
        @Id String id,
        String slug,
        String title,
        String body,
        String seoDescription,
        List<String> tags,
        // Absolute URL — either an existing listing's own R2-hosted photo, or
        // (when heroImageAttribution is set) a hotlinked Unsplash photo. Never
        // re-hosted: Unsplash's API is meant to be linked to directly, not
        // copied — see UnsplashClient.
        String heroImageUrl,
        // Set only for an Unsplash-sourced heroImageUrl. Required by
        // Unsplash's API terms to be shown, not optional caption styling —
        // the frontend must render this visibly next to the image whenever
        // it's non-null.
        String heroImageAttribution,
        String heroImageAttributionUrl,
        BlogStatus status,
        Instant publishedAt,
        // Instagram reel progress for this blog — null until the reel pipeline claims it.
        // Only set by /api/blogs/{slug}/reel/claim and /api/blogs/{slug}/reel, never by POST.
        ReelStatus reelStatus,
        Instant createdAt
) {}
