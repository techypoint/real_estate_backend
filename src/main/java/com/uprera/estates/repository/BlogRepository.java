package com.uprera.estates.repository;

import com.uprera.estates.model.Blog;
import com.uprera.estates.model.BlogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BlogRepository extends MongoRepository<Blog, String> {
    Optional<Blog> findBySlug(String slug);

    Page<Blog> findByStatus(BlogStatus status, Pageable pageable);
}
