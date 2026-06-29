package io.curiousoft.izinga.messaging.aiAgent.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryServiceTest {

    @Mock
    private ConversationHistoryRepository repository;

    private ConversationHistoryService service;

    private static final String PHONE = "+27812345678";
    private static final String DRIVER_NAME = "John Driver";

    @BeforeEach
    void setUp() {
        service = new ConversationHistoryService(repository);
    }

    // ─── getOrCreateConversation ──────────────────────────────────────────────

    @Test
    void getOrCreateConversation_returnsExisting_whenConversationExists() {
        // Given
        ConversationHistory existing = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .driverName(DRIVER_NAME)
            .messages(new ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        when(repository.findByDriverPhoneNumberAndArchivedFalse(PHONE))
            .thenReturn(Optional.of(existing));

        // When
        ConversationHistory result = service.getOrCreateConversation(PHONE, DRIVER_NAME);

        // Then
        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(repository, times(1)).findByDriverPhoneNumberAndArchivedFalse(PHONE);
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateConversation_createsNew_whenConversationDoesNotExist() {
        // Given
        when(repository.findByDriverPhoneNumberAndArchivedFalse(PHONE))
            .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            ConversationHistory arg = invocation.getArgument(0);
            arg.setId("1L");
            return arg;
        });

        // When
        ConversationHistory result = service.getOrCreateConversation(PHONE, DRIVER_NAME);

        // Then
        assertNotNull(result);
        assertEquals(PHONE, result.getDriverPhoneNumber());
        assertEquals(DRIVER_NAME, result.getDriverName());
        assertFalse(result.getArchived());
        assertEquals(0, result.getMessages().size());
        verify(repository, times(1)).save(any(ConversationHistory.class));
    }

    // ─── addUserMessage ───────────────────────────────────────────────────────

    @Test
    void addUserMessage_addsMessageAndUpdatesTimestamps() {
        // Given
        ConversationHistory history = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .messages(new ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        when(repository.save(any())).thenReturn(history);

        // When
        service.addUserMessage(history, "How do I register?");

        // Then
        assertEquals(1, history.getMessages().size());
        assertEquals("user", history.getMessages().get(0).getRole());
        assertEquals("How do I register?", history.getMessages().get(0).getContent());
        verify(repository, times(1)).save(history);
    }

    // ─── addAssistantMessage ──────────────────────────────────────────────────

    @Test
    void addAssistantMessage_addsMessageAndUpdatesTimestamps() {
        // Given
        ConversationHistory history = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .messages(new ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        when(repository.save(any())).thenReturn(history);

        // When
        service.addAssistantMessage(history, "To register, start by...");

        // Then
        assertEquals(1, history.getMessages().size());
        assertEquals("assistant", history.getMessages().get(0).getRole());
        assertEquals("To register, start by...", history.getMessages().get(0).getContent());
        verify(repository, times(1)).save(history);
    }

    // ─── getContextMessages ───────────────────────────────────────────────────

    @Test
    void getContextMessages_returnsLastNMessages() {
        // Given
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            messages.add(ConversationMessage.builder()
                .role(i % 2 == 0 ? "user" : "assistant")
                .content("Message " + i)
                .timestamp(Instant.now())
                .build());
        }

        ConversationHistory history = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .messages(messages)
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        // When
        List<ConversationMessage> context = service.getContextMessages(history);

        // Then
        assertEquals(10, context.size());
        assertEquals("Message 5", context.get(0).getContent());
        assertEquals("Message 14", context.get(9).getContent());
    }

    @Test
    void getContextMessages_returnsAllMessages_whenLessThanContextWindow() {
        // Given
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(ConversationMessage.builder()
                .role(i % 2 == 0 ? "user" : "assistant")
                .content("Message " + i)
                .timestamp(Instant.now())
                .build());
        }

        ConversationHistory history = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .messages(messages)
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        // When
        List<ConversationMessage> context = service.getContextMessages(history);

        // Then
        assertEquals(5, context.size());
    }

    // ─── clearConversation ────────────────────────────────────────────────────

    @Test
    void clearConversation_archivesConversation() {
        // Given
        ConversationHistory history = ConversationHistory.builder()
            .id("1L")
            .driverPhoneNumber(PHONE)
            .messages(new ArrayList<>())
            .createdAt(Instant.now())
            .lastMessageAt(Instant.now())
            .lastAccessedAt(Instant.now())
            .archived(false)
            .build();

        when(repository.findByDriverPhoneNumberAndArchivedFalse(PHONE))
            .thenReturn(Optional.of(history));
        when(repository.save(any())).thenReturn(history);

        // When
        service.clearConversation(PHONE);

        // Then
        assertTrue(history.getArchived());
        verify(repository, times(1)).save(history);
    }

    // ─── getActiveConversationCount ───────────────────────────────────────────

    @Test
    void getActiveConversationCount_returnsCountFromRepository() {
        // Given
        when(repository.countByArchivedFalse()).thenReturn(42L);

        // When
        long count = service.getActiveConversationCount();

        // Then
        assertEquals(42L, count);
        verify(repository, times(1)).countByArchivedFalse();
    }
}

