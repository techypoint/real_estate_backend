package com.uprera.estates.web;

import com.uprera.estates.dto.PageResponse;
import com.uprera.estates.dto.ProjectSummary;
import com.uprera.estates.dto.PublishedProjectRef;
import com.uprera.estates.dto.StatsResponse;
import com.uprera.estates.repository.ProjectRepository;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 24;

    private final ProjectRepository repository;
    private final CdnUrlResolver cdnUrlResolver;

    public ProjectController(ProjectRepository repository, CdnUrlResolver cdnUrlResolver) {
        this.repository = repository;
        this.cdnUrlResolver = cdnUrlResolver;
    }

    /** Listing page: text search, facet filters, pagination. */
    @GetMapping
    public PageResponse<ProjectSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean hasDetail,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit
    ) {
        int pageNum = Math.max(1, page);
        int limitNum = Math.min(MAX_LIMIT, Math.max(1, limit));

        List<ProjectSummary> items = repository.search(q, district, type, hasDetail, pageNum, limitNum);
        long total = repository.count(q, district, type, hasDetail);
        return PageResponse.of(items, total, pageNum, limitNum);
    }

    @GetMapping("/meta/districts")
    public List<String> districts() {
        return repository.distinctDistricts();
    }

    @GetMapping("/meta/stats")
    public StatsResponse stats() {
        return new StatsResponse(
                repository.countAll(),
                repository.countWithDetail(),
                repository.countListed(),
                repository.distinctDistricts().size()
        );
    }

    /**
     * Registration number + display name of every published project.
     *
     * Exists for the frontend's generateStaticParams and its
     * "{@code name-REGISTRATION_NO}" URL slug — Next needs the full list at
     * build time to pre-render project pages.
     */
    @GetMapping("/meta/published")
    public List<PublishedProjectRef> published() {
        return repository.publishedProjectRefs();
    }

    /**
     * Detail page: the RERA record with the curated brochure layer attached as
     * {@code content}.
     *
     * The two live in separate collections so that re-running the RERA importer
     * can never clobber hand-curated data; they are joined only here, on read.
     * {@code content} is omitted entirely when absent or unpublished — the
     * frontend's section registry keys off presence, so an unpublished project
     * simply renders its RERA sections and nothing else.
     */
    @GetMapping("/{registrationNo}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String registrationNo) {
        Document project = repository.findByRegistrationNo(registrationNo)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));

        repository.findPublishedContent(registrationNo).ifPresent(content -> project.put("content", content));
        cdnUrlResolver.resolve(project);
        return ResponseEntity.ok(project);
    }
}
