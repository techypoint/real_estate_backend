package com.uprera.estates.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finds one attributable stock photo for a topic — see {@link UnsplashClient}.
 * Used by the content pipeline's Publisher step for Pipeline B (topic-digest)
 * social posts; Pipeline A (listing announcements) uses the real listing's
 * own photos instead and never calls this.
 */
@RestController
@RequestMapping("/api/images")
public class ImageSearchController {

    private final UnsplashClient unsplash;

    public ImageSearchController(UnsplashClient unsplash) {
        this.unsplash = unsplash;
    }

    /**
     * Always 200s — an image is optional, never blocking. No "url" means no
     * image; the "error" field says why ("rate_limited", "no_match",
     * "not_configured", ...) so the caller can tell "nothing exists for this
     * topic" apart from "try again later," which look identical without it.
     */
    @GetMapping("/search")
    public UnsplashClient.Result search(@RequestParam String q) {
        return unsplash.search(q);
    }
}
