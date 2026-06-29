package io.curiousoft.izinga.messaging.aiAgent.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ConversationHistory documents in MongoDB.
 */
@Repository
public interface ConversationHistoryRepository extends MongoRepository<ConversationHistory, String> {

    /**
     * Find conversation by driver phone number
     */
    Optional<ConversationHistory> findByDriverPhoneNumberAndArchivedFalse(String driverPhoneNumber);

    /**
     * Find all active conversations
     */
    List<ConversationHistory> findByArchivedFalse();

    /**
     * Find conversations not accessed for a given duration (for cleanup)
     */
    @Query("{ 'lastAccessedAt': { $lt: ?0 }, 'archived': false }")
    List<ConversationHistory> findOldConversations(Instant cutoffTime);

    /**
     * Count active conversations
     */
    long countByArchivedFalse();
}

