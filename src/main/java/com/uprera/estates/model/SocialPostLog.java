package com.uprera.estates.model;

import com.uprera.estates.web.BufferClient;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Records a social post that actually went out via Buffer, keyed by the
 * Publisher's {@code idempotencyKey} (see SocialPostController). The
 * Publisher's prompt (agentic_ai_workflow's PUBLISHER_TOOLS /
 * PUBLISHER_PROMPT — see ORCHESTRATION_IMPROVEMENTS.md item 4 there)
 * computes this deterministically as {@code "<slug>:<platform>"}, so a
 * retried postSocialUpdate call — e.g. after a network failure where the
 * caller can't tell whether the first request actually reached Buffer —
 * can be recognized as "already posted" instead of creating a real
 * duplicate post on Instagram/Facebook/LinkedIn.
 *
 * Only successful posts are recorded here: an unconfigured/failed attempt
 * (Buffer not connected, no channel for the platform, a Buffer-side error)
 * has no side effect to protect against repeating, so it's left freely
 * retryable — recording it would wrongly freeze that outcome even after
 * Buffer gets configured or the transient error clears.
 *
 * {@code idempotencyKey} has a unique index created manually in Mongo, same
 * convention as {@link Blog#slug()} — see that class's comment.
 */
@Document(collection = "socialPostLogs")
public record SocialPostLog(
        @Id String id,
        String idempotencyKey,
        String platform,
        String postId,
        Instant createdAt
) {
    public BufferClient.PostResult toResult() {
        return new BufferClient.PostResult(true, postId, null);
    }
}
