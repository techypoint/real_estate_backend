package com.uprera.estates.dto;

import java.util.List;

/** Matches the pagination envelope the frontend already consumes. */
public record PageResponse<T>(List<T> items, long total, int page, int limit, int pages) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int limit) {
        int pages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
        return new PageResponse<>(items, total, page, limit, pages);
    }
}
