package com.uprera.estates.dto;

import org.bson.types.ObjectId;

/**
 * The listing-page projection.
 *
 * Deliberately typed, unlike the detail endpoint: this shape is stable, small,
 * and drives a card component, so being explicit is worth it. The detail
 * endpoint passes documents through untyped because that shape evolves with
 * every schema version — see ProjectController.
 */
public record ProjectSummary(
        ObjectId _id,
        String registration_no,
        String project_name,
        String promoter_name,
        String district,
        String project_type,
        String project_category,
        Boolean has_detail,
        String declared_completion_date,
        String registration_date
) {}
