package ca.rtpn.hub.service;

import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.repository.ProcessedMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pre-settlement risk screening. Currently a sliding-window velocity check
 * per debtor participant; the seam where sanctions screening, amount caps,
 * or anomaly scoring would plug in on a real rail.
 */
@Service
public class RiskService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ProcessedMessageRepository processedMessages;
    private final int maxPaymentsPerMinute;

    public RiskService(ProcessedMessageRepository processedMessages,
            @Value("${rtpn.risk.max-payments-per-minute}") int maxPaymentsPerMinute) {
        this.processedMessages = processedMessages;
        this.maxPaymentsPerMinute = maxPaymentsPerMinute;
    }

    public Optional<String> check(PaymentMessage msg) {
        Instant windowStart = Instant.now().minus(WINDOW);
        long recent = processedMessages.countByDebtorParticipantAndCreatedAtAfter(
                msg.getDebtorParticipant(), windowStart);
        if (recent >= maxPaymentsPerMinute) {
            return Optional.of("velocity limit exceeded: " + recent
                    + " payments from " + msg.getDebtorParticipant() + " in the last minute");
        }
        return Optional.empty();
    }
}
