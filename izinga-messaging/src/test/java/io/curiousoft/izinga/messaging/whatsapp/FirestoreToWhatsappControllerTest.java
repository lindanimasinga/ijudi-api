package io.curiousoft.izinga.messaging.whatsapp;

import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.firebase.FirestoreService;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTextRequest;
import io.curiousoft.izinga.messaging.whatsapp.templates.WhatsappTextResponse;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import retrofit2.Call;
import retrofit2.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirestoreToWhatsappControllerTest {

    @Mock private FirestoreService firestoreService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private WhatsappConfig whatsappConfig;
    @Mock private ConversationHistoryService conversationHistoryService;

    @SuppressWarnings("unchecked")
    private final Call<WhatsappTextResponse> callMock = mock(Call.class);

    private FirestoreToWhatsappController controller;

    private static final String SESSION_ID  = "session123";
    private static final String MSG_ID      = "msg456";
    private static final String PHONE_ID    = "phoneId001";
    private static final String RAW_PHONE   = "0821234567";
    private static final String NORM_PHONE  = "+27821234567";
    private static final String CUST_NAME   = "Thabo";
    private static final String MSG_TEXT    = "Please send me your location";

    @BeforeEach
    void setUp() {
        controller = new FirestoreToWhatsappController(
                firestoreService, whatsAppService, whatsappConfig, conversationHistoryService);
    }

    // ─── guard: session not found ────────────────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_sessionNotFound_returns404() throws Exception {
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(null);

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(whatsAppService, conversationHistoryService);
    }

    // ─── guard: message not found ────────────────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_messageNotFound_returns404() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(null);

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(whatsAppService, conversationHistoryService);
    }

    // ─── guard: non-TEXT message type ────────────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_nonTextMessageType_returns400() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        FireStoreMessage msg = message(FireStoreMessage.MessageType.IMAGE, MSG_TEXT);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(msg);

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(whatsAppService, conversationHistoryService);
    }

    // ─── WhatsApp send fails ──────────────────────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_whatsappSendFails_returns500_andSkipsPostSendSteps() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        FireStoreMessage msg = message(FireStoreMessage.MessageType.TEXT, MSG_TEXT);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(msg);
        when(whatsappConfig.phoneId()).thenReturn(PHONE_ID);
        when(whatsAppService.sendTextMessage(eq(PHONE_ID), any())).thenReturn(callMock);
        when(callMock.execute()).thenReturn(Response.error(500,
                ResponseBody.create(MediaType.get("application/json"), "")));

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verifyNoInteractions(conversationHistoryService);
    }

    // ─── happy path: success ─────────────────────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_success_returns200_normalizePhone_andInvokesServices() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        FireStoreMessage msg = message(FireStoreMessage.MessageType.TEXT, MSG_TEXT);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(msg);
        when(whatsappConfig.phoneId()).thenReturn(PHONE_ID);
        when(whatsAppService.sendTextMessage(eq(PHONE_ID), any())).thenReturn(callMock);
        when(callMock.execute()).thenReturn(Response.success(new WhatsappTextResponse()));

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(conversationHistoryService).recordHumanCorrection(NORM_PHONE, CUST_NAME, MSG_TEXT);

        ArgumentCaptor<WhatsappTextRequest> reqCaptor = ArgumentCaptor.forClass(WhatsappTextRequest.class);
        verify(whatsAppService).sendTextMessage(eq(PHONE_ID), reqCaptor.capture());
        assertEquals(NORM_PHONE, reqCaptor.getValue().getTo());
        assertEquals(MSG_TEXT, reqCaptor.getValue().getText().getBody());
    }

    // ─── null message type treated as TEXT ───────────────────────────────────

    @Test
    void forwardMessageToWhatsapp_nullMessageType_treatedAsText_returns200() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        FireStoreMessage msg = message(null, MSG_TEXT);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(msg);
        when(whatsappConfig.phoneId()).thenReturn(PHONE_ID);
        when(whatsAppService.sendTextMessage(eq(PHONE_ID), any())).thenReturn(callMock);
        when(callMock.execute()).thenReturn(Response.success(new WhatsappTextResponse()));

        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(conversationHistoryService).recordHumanCorrection(NORM_PHONE, CUST_NAME, MSG_TEXT);
    }

    // ─── post-send non-blocking: history service throws ──────────────────────

    @Test
    void forwardMessageToWhatsapp_historyServiceThrows_stillReturns200() throws Exception {
        ChatSession session = chatSession(RAW_PHONE, CUST_NAME);
        FireStoreMessage msg = message(FireStoreMessage.MessageType.TEXT, MSG_TEXT);
        when(firestoreService.getChatSessionById(SESSION_ID)).thenReturn(session);
        when(firestoreService.getMessageForSession(SESSION_ID, MSG_ID)).thenReturn(msg);
        when(whatsappConfig.phoneId()).thenReturn(PHONE_ID);
        when(whatsAppService.sendTextMessage(eq(PHONE_ID), any())).thenReturn(callMock);
        when(callMock.execute()).thenReturn(Response.success(new WhatsappTextResponse()));
        doThrow(new RuntimeException("DB error")).when(conversationHistoryService)
                .recordHumanCorrection(anyString(), anyString(), anyString());


        ResponseEntity<Object> response = controller.forwardMessageToWhatsapp(SESSION_ID, MSG_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private ChatSession chatSession(String phone, String name) {
        return ChatSession.builder().customerMobileNumber(phone).customerName(name).build();
    }

    private FireStoreMessage message(FireStoreMessage.MessageType type, String text) {
        return FireStoreMessage.builder().messageType(type).message(text).build();
    }
}
