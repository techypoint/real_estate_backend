package com.uprera.estates.dto;

/**
 * Registration number + display name for one published project.
 *
 * Feeds the frontend's URL slug ({@code name-REGISTRATION_NO}) and its
 * generateStaticParams — see ProjectController#published.
 */
public record PublishedProjectRef(String registration_no, String name) {}
