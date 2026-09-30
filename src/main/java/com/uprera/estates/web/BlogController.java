package com.uprera.estates.web;

import com.uprera.estates.dto.BlogRequest;
import com.uprera.estates.dto.PageResponse;
import com.uprera.estates.model.Blog;
import com.uprera.estates.model.BlogStatus;
import com.uprera.estates.repository.BlogRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Blog posts written by the content pipeline (agentic_ai_workflow's
 * "Content · Publisher" agent — see SOCIAL_CONTENT_PIPELINE.md there) after
 * human approval. This service only persists and serves; nothing here
 * generates or reviews content.
 */
@RestController
@RequestMapping("/api/blogs")
public class BlogController {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    private final BlogRepository repository;
    private final FrontendRevalidator revalidator;

    public BlogController(BlogRepository repository, FrontendRevalidator revalidator) {
        this.repository = repository;
        this.revalidator = revalidator;
    }

    /** Publish (or draft) a blog post. Called by the pipeline's Publisher step. */
    @PostMapping
    public ResponseEntity<Blog> create(@Valid @RequestBody BlogRequest body) {
        Blog blog = new Blog(
                null,
                body.slug().trim(),
                body.title().trim(),
                body.body(),
                body.seoDescription() == null ? null : body.seoDescription().trim(),
                body.tags(),
                body.heroImageUrl(),
                body.heroImageAttribution(),
                body.heroImageAttributionUrl(),
                body.status(),
                body.status() == BlogStatus.PUBLISHED
                        ? (body.publishedAt() != null ? body.publishedAt() : Instant.now())
                        : null,
                Instant.now()
        );
        Blog saved;
        try {
            saved = repository.save(blog);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(CONFLICT, "A blog with slug \"" + blog.slug() + "\" already exists");
        }
        if (saved.status() == BlogStatus.PUBLISHED) {
            revalidator.revalidateBlog(saved.slug());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Listing page for real_estate_frontend: published posts, newest first. */
    @GetMapping
    public PageResponse<Blog> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit
    ) {
        int pageNum = Math.max(1, page);
        int limitNum = Math.min(MAX_LIMIT, Math.max(1, limit));

        Page<Blog> result = repository.findByStatus(
                BlogStatus.PUBLISHED,
                PageRequest.of(pageNum - 1, limitNum, Sort.by(Sort.Direction.DESC, "publishedAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), pageNum, limitNum);
    }

    /** Detail page for real_estate_frontend. 404s for drafts too — they aren't public. */
    @GetMapping("/{slug}")
    public Blog detail(@PathVariable String slug) {
        Blog blog = repository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Blog not found"));
        if (blog.status() != BlogStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Blog not found");
        }
        return blog;
    }
}
