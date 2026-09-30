package com.uprera.estates.dto;

import com.uprera.estates.model.BlogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Inbound shape for {@code POST /api/blogs} — written by the content pipeline's Publisher step. */
public record BlogRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase, hyphen-separated") String slug,
        @NotBlank String body,
        @Size(max = 300) String seoDescription,
        List<String> tags,
        String heroImageUrl,
        // Both set together only for an Unsplash-sourced image — see Blog.java.
        String heroImageAttribution,
        String heroImageAttributionUrl,
        @NotNull BlogStatus status,
        Instant publishedAt
) {}
