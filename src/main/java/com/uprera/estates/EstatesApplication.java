package com.uprera.estates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * UP RERA Estates backend.
 *
 * Owns ALL business logic: projects, search, curated content, leads, and (later)
 * LLM orchestration. It is the only thing that touches MongoDB. The Next.js
 * frontend is a rendering layer over this API and must not reimplement rules
 * here — see CLAUDE.md, "Java <-> Next boundary".
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EstatesApplication {
    public static void main(String[] args) {
        SpringApplication.run(EstatesApplication.class, args);
    }
}
