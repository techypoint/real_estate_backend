package com.uprera.estates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound shape for {@code POST /api/social-posts}, called once per
 * platform by the content pipeline's Publisher step — never batched, same
 * discipline as {@link BlogRequest} (see SOCIAL_CONTENT_PIPELINE.md §5a for
 * why that matters: this endpoint posts to exactly one Buffer channel per
 * call, there is no multi-platform shape to send here).
 *
 * {@code idempotencyKey} is the Publisher's deterministic
 * "&lt;slug&gt;:&lt;platform&gt;" — see SocialPostLog for how it's used to
 * make a retried call safe instead of creating a duplicate post.
 */
public record SocialPostRequest(
        @NotBlank @Pattern(regexp = "^(instagram|facebook|linkedin)$", message = "must be instagram, facebook, or linkedin") String platform,
        @NotBlank @Size(max = 3000) String text,
        String imageUrl,
        @NotBlank @Size(max = 200) String idempotencyKey
) {}
