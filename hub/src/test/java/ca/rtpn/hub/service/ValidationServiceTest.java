package ca.rtpn.hub.service;

import ca.rtpn.hub.model.PaymentMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServiceTest {

    private final ValidationService validation = new ValidationService();

    private PaymentMessage valid() {
        return new PaymentMessage("MSG-1", "E2E-1", "ALPHA_BANK", "BETA_BANK",
                new BigDecimal("100.00"), "CAD");
    }

    @Test
    void acceptsWellFormedMessage() {
        assertTrue(validation.validate(valid()).isEmpty());
    }

    @Test
    void rejectsMissingMessageId() {
        PaymentMessage msg = valid();
        msg.setMessageId("  ");
        assertEquals("messageId is required", validation.validate(msg).orElseThrow());
    }

    @Test
    void rejectsSelfPayment() {
        PaymentMessage msg = valid();
        msg.setCreditorParticipant("ALPHA_BANK");
        assertTrue(validation.validate(msg).orElseThrow().contains("different participants"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        PaymentMessage msg = valid();
        msg.setAmount(new BigDecimal("-5.00"));
        assertTrue(validation.validate(msg).orElseThrow().contains("positive"));

        msg.setAmount(BigDecimal.ZERO);
        assertTrue(validation.validate(msg).orElseThrow().contains("positive"));
    }

    @Test
    void rejectsSubCentPrecision() {
        PaymentMessage msg = valid();
        msg.setAmount(new BigDecimal("10.001"));
        assertTrue(validation.validate(msg).orElseThrow().contains("precision"));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        PaymentMessage msg = valid();
        msg.setCurrency("USD");
        assertTrue(validation.validate(msg).orElseThrow().contains("unsupported currency"));
    }
}
