package com.uprera.estates.dto;

import com.uprera.estates.model.ReelStatus;
import jakarta.validation.constraints.NotNull;

/** Inbound shape for {@code PATCH /api/blogs/{slug}/reel} — the reel pipeline's final result. */
public record ReelStatusRequest(@NotNull ReelStatus status) {}
