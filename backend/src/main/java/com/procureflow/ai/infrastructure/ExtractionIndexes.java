package com.procureflow.ai.infrastructure;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Declares the extraction-history indexes imperatively at startup instead
 * of annotations: a derived store must never fail the boot when Mongo is
 * unreachable, so creation is attempted here inside a catch-and-warn
 * (the repository still works unindexed until Mongo returns).
 */
@Component
public class ExtractionIndexes {

    private static final Logger log = LoggerFactory.getLogger(ExtractionIndexes.class);

    public ExtractionIndexes(MongoTemplate mongo) {
        try {
            mongo.indexOps(ExtractionRecord.class)
                    .ensureIndex(new Index()
                            .on("tenantSlug", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC));
            mongo.indexOps(ExtractionRecord.class)
                    .ensureIndex(new Index()
                            .on("createdAt", Sort.Direction.ASC)
                            .expire(90, TimeUnit.DAYS));
            log.info("extraction history indexes ensured");
        } catch (Exception e) {
            log.warn("extraction history indexes not ensured (mongo unreachable?): {}", e.getMessage());
        }
    }
}
