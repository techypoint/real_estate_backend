package com.uprera.estates.web;

import com.uprera.estates.dto.LeadRequest;
import com.uprera.estates.model.Lead;
import com.uprera.estates.repository.LeadRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Lead capture — the contact form today, chat-sourced leads later (CLAUDE.md:
 * "one pipeline and one attribution model"). Persists only; no notification or
 * CRM integration yet.
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadRepository repository;

    public LeadController(LeadRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody LeadRequest body) {
        Lead saved = repository.save(new Lead(
                null,
                body.name().trim(),
                body.email().trim(),
                body.phone() == null ? null : body.phone().trim(),
                body.message().trim(),
                "contact_page",
                Instant.now()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.id()));
    }
}
