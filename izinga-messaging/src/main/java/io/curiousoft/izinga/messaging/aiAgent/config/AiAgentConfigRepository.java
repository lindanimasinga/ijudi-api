package io.curiousoft.izinga.messaging.aiAgent.config;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for AiAgentConfig documents in MongoDB.
 */
@Repository
public interface AiAgentConfigRepository extends MongoRepository<AiAgentConfig, String> {

    /**
     * Find agent config by agent name
     */
    Optional<AiAgentConfig> findByAgentNameAndActiveTrue(String agentName);

    /**
     * Find agent config by agent name (regardless of active status)
     */
    Optional<AiAgentConfig> findByAgentName(String agentName);
}

