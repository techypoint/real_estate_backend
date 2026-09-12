package com.uprera.estates.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inbound shape for {@code POST /api/leads} — the contact form today. */
public record LeadRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 4000) String message
) {}
