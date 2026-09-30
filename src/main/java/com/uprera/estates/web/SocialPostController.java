package com.uprera.estates.web;

import com.uprera.estates.dto.SocialPostRequest;
import com.uprera.estates.model.SocialPostLog;
import com.uprera.estates.repository.SocialPostLogRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

/**
 * Publishes a single social post via Buffer (see {@link BufferClient}),
 * called by the content pipeline's Publisher step after a human-approved
 * draft is ready. One call per platform — see {@link SocialPostRequest}.
 *
 * Always 200s, even when nothing actually posted: "Buffer not configured"
 * or "no channel for this platform" are expected, recoverable outcomes for
 * the Publisher agent (it falls back to surfacing captions for manual
 * posting — see its prompt in agentic_ai_workflow), not server errors.
 *
 * Idempotent per {@code idempotencyKey}: a repeated call after a successful
 * post returns that same recorded outcome instead of posting to Buffer
 * again — see {@link SocialPostLog} for why only successes are remembered.
 * See ORCHESTRATION_IMPROVEMENTS.md item 4 in agentic_ai_workflow for the
 * incident (§5b there) this guards against.
 */
@RestController
@RequestMapping("/api/social-posts")
public class SocialPostController {

    private final BufferClient buffer;
    private final SocialPostLogRepository logs;

    public SocialPostController(BufferClient buffer, SocialPostLogRepository logs) {
        this.buffer = buffer;
        this.logs = logs;
    }

    @PostMapping
    public ResponseEntity<BufferClient.PostResult> create(@Valid @RequestBody SocialPostRequest body) {
        Optional<SocialPostLog> existing = logs.findByIdempotencyKey(body.idempotencyKey());
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get().toResult());
        }

        BufferClient.PostResult result = buffer.createPost(body.platform(), body.text(), body.imageUrl());

        if (result.posted()) {
            // Only a real, side-effecting outcome is worth remembering — an
            // unconfigured/failed attempt has nothing to protect against
            // repeating (see SocialPostLog's class comment).
            try {
                logs.save(new SocialPostLog(null, body.idempotencyKey(), body.platform(), result.postId(), Instant.now()));
            } catch (DuplicateKeyException e) {
                // A concurrent request with the same key won the race and
                // already recorded an outcome — return that one, not ours,
                // so two different results are never seen for one key.
                return logs.findByIdempotencyKey(body.idempotencyKey())
                        .map(SocialPostLog::toResult)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.ok(result));
            }
        }

        return ResponseEntity.ok(result);
    }
}
