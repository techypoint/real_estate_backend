package com.uprera.estates.repository;

import com.uprera.estates.model.SocialPostLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SocialPostLogRepository extends MongoRepository<SocialPostLog, String> {
    Optional<SocialPostLog> findByIdempotencyKey(String idempotencyKey);
}
