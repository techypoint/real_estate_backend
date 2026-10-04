package com.uprera.estates.web;

import com.uprera.estates.model.Blog;
import com.uprera.estates.model.ReelStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Marks a blog's reel POSTED after its Instagram post succeeds. Called from
 * {@link SocialPostController}. Only moves a blog that is IN_PROGRESS, so a
 * stray call can't overwrite a reel that was never claimed.
 */
@Component
public class BlogReelStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(BlogReelStatusUpdater.class);

    private final MongoTemplate mongo;

    public BlogReelStatusUpdater(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void markReelPosted(String slug) {
        Query query = Query.query(Criteria.where("slug").is(slug)
                .and("reelStatus").is(ReelStatus.IN_PROGRESS));
        var result = mongo.updateFirst(query, new Update().set("reelStatus", ReelStatus.POSTED), Blog.class);
        if (result.getModifiedCount() == 0) {
            // The post itself already succeeded, so don't fail the request over bookkeeping.
            log.warn("Reel for blog \"{}\" posted, but no IN_PROGRESS reel was found to mark POSTED", slug);
        }
    }
}
