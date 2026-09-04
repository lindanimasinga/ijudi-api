package io.curiousoft.izinga.ordermanagement.auth;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * Implements the atomic OTP operations via MongoTemplate.findAndModify().
 *
 * SEC-03: atomicMarkUsed uses a single findAndModify with filter { _id, used: false }.
 * There is no read-then-write — if two concurrent verify calls arrive, only one will
 * match the filter and receive the pre-update document; the other will get null and fail.
 */
@Repository
public class WhatsAppOtpRepositoryImpl implements WhatsAppOtpRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public WhatsAppOtpRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public WhatsAppOtpDocument atomicMarkUsed(String id) {
        var query = Query.query(
                Criteria.where("id").is(id).and("used").is(false));
        var update = new Update().set("used", true);
        // returnDocument = BEFORE: returns the document as it was (used=false),
        // so we can confirm a successful claim. Null = already used or not found.
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(false),
                WhatsAppOtpDocument.class);
    }

    @Override
    public WhatsAppOtpDocument atomicIncrementAttempt(String id) {
        var query = Query.query(Criteria.where("id").is(id));
        var update = new Update().inc("attemptCount", 1);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WhatsAppOtpDocument.class);
    }
}
