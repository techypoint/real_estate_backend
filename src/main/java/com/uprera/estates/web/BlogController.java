package com.uprera.estates.web;

import com.uprera.estates.dto.BlogRequest;
import com.uprera.estates.dto.PageResponse;
import com.uprera.estates.dto.ReelStatusRequest;
import com.uprera.estates.model.Blog;
import com.uprera.estates.model.BlogStatus;
import com.uprera.estates.model.ReelStatus;
import com.uprera.estates.repository.BlogRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
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
    private final MongoTemplate mongo;

    public BlogController(BlogRepository repository, FrontendRevalidator revalidator, MongoTemplate mongo) {
        this.repository = repository;
        this.revalidator = revalidator;
        this.mongo = mongo;
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
                null,
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

    /**
     * Published blogs with no reel started yet, newest first. Read by the reel
     * cron (video_code / agentic_ai_workflow). Declared before /{slug} so the
     * literal path wins.
     */
    @GetMapping("/needs-reel")
    public PageResponse<Blog> needsReel(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int limitNum = Math.min(MAX_LIMIT, Math.max(1, limit));
        Page<Blog> result = repository.findByStatusAndReelStatusIsNull(
                BlogStatus.PUBLISHED,
                PageRequest.of(0, limitNum, Sort.by(Sort.Direction.DESC, "publishedAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), 1, limitNum);
    }

    /**
     * Atomically marks a published blog's reel as IN_PROGRESS. 409 if the blog
     * is missing, not published, or already has a reel state — so only one run
     * can claim it.
     */
    @PostMapping("/{slug}/reel/claim")
    public Blog claimReel(@PathVariable String slug) {
        Query query = Query.query(Criteria.where("slug").is(slug)
                .and("status").is(BlogStatus.PUBLISHED)
                .and("reelStatus").is(null));
        Blog claimed = mongo.findAndModify(
                query,
                new Update().set("reelStatus", ReelStatus.IN_PROGRESS),
                FindAndModifyOptions.options().returnNew(true),
                Blog.class
        );
        if (claimed == null) {
            throw new ResponseStatusException(CONFLICT, "Blog \"" + slug + "\" is not published or already has a reel");
        }
        return claimed;
    }

    /**
     * Clears a stuck or failed reel so the cron can pick the blog up again.
     * Only IN_PROGRESS or FAILED can be reset. A POSTED reel is never reset,
     * because that would make a second reel for a blog that already has one.
     * Resetting an IN_PROGRESS reel while its render is still running can
     * start a duplicate, so reset only when that run is known to be dead.
     */
    @PostMapping("/{slug}/reel/reset")
    public Blog resetReel(@PathVariable String slug) {
        Query query = Query.query(Criteria.where("slug").is(slug)
                .and("reelStatus").in(ReelStatus.IN_PROGRESS, ReelStatus.FAILED));
        Blog reset = mongo.findAndModify(
                query,
                new Update().unset("reelStatus"),
                FindAndModifyOptions.options().returnNew(true),
                Blog.class
        );
        if (reset == null) {
            throw new ResponseStatusException(CONFLICT,
                    "Blog \"" + slug + "\" has no IN_PROGRESS or FAILED reel to reset (POSTED reels are never reset)");
        }
        return reset;
    }

    /**
     * Records the reel's final result (POSTED or FAILED). Only valid after a
     * claim — 409 if the blog isn't currently IN_PROGRESS.
     */
    @PatchMapping("/{slug}/reel")
    public Blog setReelStatus(@PathVariable String slug, @Valid @RequestBody ReelStatusRequest body) {
        if (body.status() == ReelStatus.IN_PROGRESS) {
            throw new ResponseStatusException(BAD_REQUEST, "Use POST /reel/claim to start a reel");
        }
        Query query = Query.query(Criteria.where("slug").is(slug)
                .and("reelStatus").is(ReelStatus.IN_PROGRESS));
        Blog updated = mongo.findAndModify(
                query,
                new Update().set("reelStatus", body.status()),
                FindAndModifyOptions.options().returnNew(true),
                Blog.class
        );
        if (updated == null) {
            throw new ResponseStatusException(CONFLICT, "Blog \"" + slug + "\" has no reel in progress");
        }
        return updated;
    }
}
