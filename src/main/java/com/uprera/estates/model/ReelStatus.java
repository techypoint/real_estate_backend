package com.uprera.estates.model;

/**
 * Where a blog's Instagram reel stands. Absent (null) means no reel has been
 * started yet. The claim endpoint moves null to IN_PROGRESS atomically, so two
 * cron runs can't both make a reel for the same blog.
 */
public enum ReelStatus {
    IN_PROGRESS,
    POSTED,
    FAILED
}
