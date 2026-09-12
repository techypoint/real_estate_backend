package com.uprera.estates.dto;

/** Homepage counters. {@code listed} is what the catalogue actually shows; {@code total} is the raw RERA scrape size. */
public record StatsResponse(long total, long withDetail, long listed, int districts) {}
