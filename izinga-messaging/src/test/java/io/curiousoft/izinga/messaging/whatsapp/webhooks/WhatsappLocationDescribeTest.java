package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WhatsappWebhookPayload.Value.Message.Location#describe()} — the
 * natural-language description fed to the AI agent's conversation history and persisted
 * to Firestore for an inbound WhatsApp location share.
 */
class WhatsappLocationDescribeTest {

    @Test
    void describe_includesNameAddressAndCoordinates_whenAllPresent() {
        var location = location("Sandton City", "83 Rivonia Rd, Sandton", -26.1076, 28.0526);

        String description = location.describe();

        assertEquals(
                "Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton (latitude: -26.1076, longitude: 28.0526)",
                description
        );
    }

    @Test
    void describe_omitsLabel_whenNameAndAddressAreAbsent() {
        var location = location(null, null, -26.1076, 28.0526);

        String description = location.describe();

        assertEquals("Customer shared their location (latitude: -26.1076, longitude: 28.0526)", description);
        assertFalse(description.contains("location:"), "no name/address label should be rendered");
    }

    @Test
    void describe_usesNameOnly_whenAddressIsAbsent() {
        var location = location("Sandton City", null, -26.1076, 28.0526);

        String description = location.describe();

        assertEquals("Customer shared their location: Sandton City (latitude: -26.1076, longitude: 28.0526)", description);
    }

    @Test
    void describe_usesAddressOnly_whenNameIsAbsent() {
        var location = location(null, "83 Rivonia Rd, Sandton", -26.1076, 28.0526);

        String description = location.describe();

        assertEquals("Customer shared their location: 83 Rivonia Rd, Sandton (latitude: -26.1076, longitude: 28.0526)", description);
    }

    @Test
    void describe_omitsCoordinates_whenLatitudeOrLongitudeIsMissing() {
        var location = location("Sandton City", "83 Rivonia Rd, Sandton", null, 28.0526);

        String description = location.describe();

        assertEquals("Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton", description);
        assertFalse(description.contains("latitude"));
    }

    @Test
    void describe_blankNameAndAddress_treatedAsAbsent() {
        var location = location("  ", "", -26.1076, 28.0526);

        String description = location.describe();

        assertTrue(description.startsWith("Customer shared their location ("),
                "blank name/address must not produce an empty label");
    }

    /**
     * AC-03: when both latitude AND longitude are null the coordinate clause is omitted entirely
     * and no NullPointerException is thrown (the guard is {@code latitude != null && longitude != null}).
     */
    @Test
    void describe_omitsCoordinates_whenBothLatitudeAndLongitudeAreNull() {
        var location = location("Sandton City", "83 Rivonia Rd, Sandton", null, null);

        String description = location.describe();

        assertEquals("Customer shared their location: Sandton City, 83 Rivonia Rd, Sandton", description);
        assertFalse(description.contains("latitude"), "no coordinate clause when both lat and lon are null");
        assertFalse(description.contains("longitude"), "no coordinate clause when both lat and lon are null");
    }

    private WhatsappWebhookPayload.Value.Message.Location location(String name, String address, Double lat, Double lon) {
        var location = new WhatsappWebhookPayload.Value.Message.Location();
        location.setName(name);
        location.setAddress(address);
        location.setLatitude(lat);
        location.setLongitude(lon);
        return location;
    }
}
