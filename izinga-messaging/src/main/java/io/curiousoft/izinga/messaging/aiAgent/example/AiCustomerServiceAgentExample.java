package io.curiousoft.izinga.messaging.aiAgent.example;

import io.curiousoft.izinga.messaging.aiAgent.AiCustomerServiceAgent;
import io.curiousoft.izinga.messaging.whatsapp.webhooks.WhatsappWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example usage of AiCustomerServiceAgent with conversation memory.
 *
 * This shows how the AI agent maintains conversation context across multiple messages,
 * allowing it to understand driver questions in the context of previous interactions.
 */
@Component
public class AiCustomerServiceAgentExample {

    private static final Logger LOG = LoggerFactory.getLogger(AiCustomerServiceAgentExample.class);
    private final AiCustomerServiceAgent aiCustomerServiceAgent;

    public AiCustomerServiceAgentExample(AiCustomerServiceAgent aiCustomerServiceAgent) {
        this.aiCustomerServiceAgent = aiCustomerServiceAgent;
    }

    /**
     * Example: Handle a sequence of related messages from a driver.
     *
     * The AI agent remembers the context of previous messages:
     *
     * 1. Driver: "How do I register?"
     *    AI: "To register, start by verifying your phone number..."
     *
     * 2. Driver: "I verified my number, what's next?"
     *    AI: (Remembers step 1) "Great! Now complete your personal profile..."
     *
     * 3. Driver: "How long does approval take?"
     *    AI: (Remembers entire flow) "After submission, approval usually takes..."
     */
    public String handleConversationalFlow() {
        String driverPhoneNumber = "+27812345678";
        String driverName = "John Mkhize";

        // Message 1: Initial question
        LOG.info("=== Message 1: How do I register? ===");
        var message1 = buildMessage("How do I register?");
        var reply1 = aiCustomerServiceAgent.handleWhatsappQuery(message1, driverPhoneNumber, driverName);
        LOG.info("Reply 1: {}", reply1);
        // Database now contains: [user: "How do I register?", assistant: reply1]

        // Message 2: Follow-up question (AI remembers first interaction)
        LOG.info("\n=== Message 2: I verified my number, what's next? ===");
        var message2 = buildMessage("I verified my number, what's next?");
        var reply2 = aiCustomerServiceAgent.handleWhatsappQuery(message2, driverPhoneNumber, driverName);
        LOG.info("Reply 2: {}", reply2);
        // Database now contains: [
        //   user: "How do I register?",
        //   assistant: reply1,
        //   user: "I verified my number, what's next?",
        //   assistant: reply2
        // ]

        // Message 3: Another follow-up (AI has full context)
        LOG.info("\n=== Message 3: How long does approval take? ===");
        var message3 = buildMessage("How long does approval take?");
        var reply3 = aiCustomerServiceAgent.handleWhatsappQuery(message3, driverPhoneNumber, driverName);
        LOG.info("Reply 3: {}", reply3);
        // Database now contains all 6 messages

        // When OpenAI is called for message 3, the request includes:
        // - System prompt (customer service persona)
        // - Last 10 messages from this driver (in this case, all 6)
        // - Current message ("How long does approval take?")
        //
        // This allows OpenAI to provide a contextually-aware response that:
        // - Remembers the registration flow discussed
        // - Knows the driver has already verified their number
        // - Can reference specific steps from the conversation

        return reply3;
    }

    /**
     * Example: How the agent handles recovery from previous conversation.
     *
     * Imagine a driver comes back 3 days later and asks:
     * "Where was I in the registration process?"
     *
     * The AI can look back at the database and say:
     * "Based on our last conversation, you had verified your phone number
     *  and were about to complete your personal profile."
     */
    public String handleConversationRecovery() {
        String driverPhoneNumber = "+27812345678";
        String driverName = "John Mkhize";

        // Driver returns 3 days later
        LOG.info("=== Driver returns after 3 days ===");
        var message = buildMessage("I'm back, where were we?");
        var reply = aiCustomerServiceAgent.handleWhatsappQuery(message, driverPhoneNumber, driverName);
        LOG.info("Reply: {}", reply);

        // The reply can reference the entire previous conversation
        // because the last 10 messages from this driver are loaded from DB

        return reply;
    }

    /**
     * Example: Multiple drivers with separate conversation threads.
     *
     * Each driver has their own conversation thread, so:
     * - Driver A's questions don't affect Driver B's responses
     * - Each driver gets personalized context based on their own history
     * - The system scales to thousands of drivers
     */
    public void handleMultipleDrivers() {
        String driver1Phone = "+27812345678";
        String driver1Name = "John";

        String driver2Phone = "+27814567890";
        String driver2Name = "Maria";

        // Driver 1 asks about payouts
        LOG.info("=== Driver 1: Asking about payouts ===");
        var msg1 = buildMessage("How do payouts work?");
        var reply1 = aiCustomerServiceAgent.handleWhatsappQuery(msg1, driver1Phone, driver1Name);
        LOG.info("Reply to Driver 1: {}", reply1);

        // Driver 2 asks about registration
        LOG.info("\n=== Driver 2: Asking about registration ===");
        var msg2 = buildMessage("How do I register?");
        var reply2 = aiCustomerServiceAgent.handleWhatsappQuery(msg2, driver2Phone, driver2Name);
        LOG.info("Reply to Driver 2: {}", reply2);

        // Database maintains separate conversation threads:
        // - ai_conversation_histories[driver1Phone] -> [payouts question, payouts answer]
        // - ai_conversation_histories[driver2Phone] -> [registration question, registration answer]
        //
        // When Driver 1 follows up later, only Driver 1's context is loaded.
    }

    /**
     * Example: Conversation cleanup (automatic).
     *
     * Every day at 2 AM, a scheduled task runs:
     *
     *   @Scheduled(cron = "0 0 2 * * *")
     *   public void cleanupOldConversations()
     *
     * This archives conversations not accessed in 30 days.
     * The conversation is soft-deleted (archived = true) but remains in DB for audit.
     *
     * For a new driver registering 40 days later, a fresh conversation is created.
     */
    public void demonstrateCleanup() {
        LOG.info("Every day at 2 AM, conversations > 30 days old are archived");
        LOG.info("This keeps the active conversation database lean");
        LOG.info("Old conversations remain for audit/compliance purposes");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private WhatsappWebhookPayload.Value.Message buildMessage(String body) {
        var message = new WhatsappWebhookPayload.Value.Message();
        var text = new WhatsappWebhookPayload.Value.Message.Text();
        text.setBody(body);
        message.setText(text);
        return message;
    }
}

