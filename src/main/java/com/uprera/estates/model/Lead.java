package com.uprera.estates.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A contact-page / lead-capture submission.
 *
 * This is the one pipeline for every inbound lead on the site (contact form
 * today, chat-sourced leads later per CLAUDE.md) — conversion itself happens
 * off-platform through a tie-up partner, so this collection is purely the
 * record of "someone asked to be contacted."
 */
@Document(collection = "leads")
public record Lead(
        @Id String id,
        String name,
        String email,
        String phone,
        String message,
        String source,
        Instant createdAt
) {}
