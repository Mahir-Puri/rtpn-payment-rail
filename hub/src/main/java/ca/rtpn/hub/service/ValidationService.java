package ca.rtpn.hub.service;

import ca.rtpn.hub.model.PaymentMessage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Structural validation of the payment message. Returns a rejection reason,
 * or empty when the message is well-formed.
 */
@Service
public class ValidationService {

    private static final String SUPPORTED_CURRENCY = "CAD";
    private static final int MAX_DECIMAL_PLACES = 2;

    public Optional<String> validate(PaymentMessage msg) {
        if (isBlank(msg.getMessageId())) {
            return Optional.of("messageId is required");
        }
        if (isBlank(msg.getDebtorParticipant()) || isBlank(msg.getCreditorParticipant())) {
            return Optional.of("debtorParticipant and creditorParticipant are required");
        }
        if (msg.getDebtorParticipant().equals(msg.getCreditorParticipant())) {
            return Optional.of("debtor and creditor must be different participants");
        }
        BigDecimal amount = msg.getAmount();
        if (amount == null || amount.signum() <= 0) {
            return Optional.of("amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > MAX_DECIMAL_PLACES) {
            return Optional.of("amount precision exceeds " + MAX_DECIMAL_PLACES + " decimal places");
        }
        if (!SUPPORTED_CURRENCY.equals(msg.getCurrency())) {
            return Optional.of("unsupported currency: " + msg.getCurrency());
        }
        return Optional.empty();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
