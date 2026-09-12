package com.uprera.estates.web;

import com.uprera.estates.config.AppProperties;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rewrites every {@code url} value in a Mongo document tree from a bare object
 * storage key into an absolute CDN URL.
 *
 * `projectcontents` and `projects` are read as raw {@link Document}, not typed
 * POJOs (see ProjectRepository) — the schema is the contract, and content
 * shape evolves independently of this service. That means URL composition
 * can't hook a single MediaRef class; instead this walks the generic
 * Map/List tree looking for the key "url", which is the one convention every
 * media reference and document reference in schema/project-schema.json shares.
 *
 * A value already starting with "http://" or "https://" is an external asset
 * (a portal link, a builder-hosted URL) and is left untouched. Everything else
 * is treated as a key relative to {@code app.cdn-base-url} — this is the only
 * place in the codebase that knows the current storage provider, so switching
 * providers is one property change, never a data rewrite.
 */
@Component
public class CdnUrlResolver {

    private final String cdnBaseUrl;

    public CdnUrlResolver(AppProperties props) {
        String base = props.cdnBaseUrl();
        this.cdnBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public void resolve(Document root) {
        walk(root);
    }

    @SuppressWarnings("unchecked")
    private void walk(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if ("url".equals(entry.getKey()) && value instanceof String s && !isAbsolute(s)) {
                    ((Map<String, Object>) map).put("url", toCdnUrl(s));
                } else {
                    walk(value);
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) walk(item);
        }
    }

    private boolean isAbsolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String toCdnUrl(String key) {
        String trimmed = key.startsWith("/") ? key.substring(1) : key;
        return cdnBaseUrl + "/" + trimmed;
    }
}
