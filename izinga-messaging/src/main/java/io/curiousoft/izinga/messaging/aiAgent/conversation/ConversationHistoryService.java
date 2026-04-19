package io.curiousoft.izinga.messaging.aiAgent.conversation;

import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing conversation history.
 * Handles creation, retrieval, persistence, and cleanup of driver conversations.
 */
@Service
public class ConversationHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationHistoryService.class);

    private static final int CONTEXT_WINDOW_SIZE = 10; // Last 10 messages for context
    private static final int RETENTION_DAYS = 30; // Keep conversations for 30 days

    private final ConversationHistoryRepository repository;

    public ConversationHistoryService(ConversationHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Get or create a conversation for a driver
     */
    @Transactional
    public ConversationHistory getOrCreateConversation(String driverPhoneNumber, String driverName) {
        Optional<ConversationHistory> existing = repository
            .findByDriverPhoneNumberAndArchivedFalse(driverPhoneNumber);

        if (existing.isPresent()) {
            LOG.debug("Found existing conversation for driver {}", driverPhoneNumber);
            return existing.get();
        }

        LOG.info("Creating new conversation for driver {}", driverPhoneNumber);
        ConversationHistory history = ConversationHistory.builder()
            .driverPhoneNumber(driverPhoneNumber)
            .driverName(driverName)
            .messages(new java.util.ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        return repository.save(history);
    }

    /**
     * Add user message and save
     */
    @Transactional
    public void addUserMessage(ConversationHistory history, String userMessage) {
        history.addUserMessage(userMessage);
        repository.save(history);
        LOG.debug("Added user message to conversation for {}", history.getDriverPhoneNumber());
    }

    /**
     * Add assistant message and save
     */
    @Transactional
    public void addAssistantMessage(ConversationHistory history, String assistantMessage) {
        history.addAssistantMessage(assistantMessage);
        repository.save(history);
        LOG.debug("Added assistant message to conversation for {}", history.getDriverPhoneNumber());
    }

    /**
     * Get messages for context (last N messages)
     */
    public List<ConversationMessage> getContextMessages(ConversationHistory history) {
        return history.getLastNMessages(CONTEXT_WINDOW_SIZE);
    }

    /**
     * Clear all messages for a driver (soft delete)
     */
    @Transactional
    public void clearConversation(String driverPhoneNumber) {
        Optional<ConversationHistory> history = repository
            .findByDriverPhoneNumberAndArchivedFalse(driverPhoneNumber);

        if (history.isPresent()) {
            history.get().setArchived(true);
            repository.save(history.get());
            LOG.info("Cleared conversation for driver {}", driverPhoneNumber);
        }
    }

    /**
     * Scheduled task: Clean up old conversations (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldConversations() {
        Instant cutoffTime = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        List<ConversationHistory> oldConversations = repository.findOldConversations(cutoffTime);

        if (!oldConversations.isEmpty()) {
            oldConversations.forEach(history -> history.setArchived(true));
            repository.saveAll(oldConversations);
            LOG.info("Archived {} old conversations older than {} days",
                oldConversations.size(), RETENTION_DAYS);
        }
    }

    /**
     * Get conversation statistics
     */
    public long getActiveConversationCount() {
        return repository.countByArchivedFalse();
    }
}

