package ca.rtpn.hub.kafka;

import ca.rtpn.hub.domain.OutboxEvent;
import ca.rtpn.hub.model.ClearingResult;
import ca.rtpn.hub.model.ClearingStatus;
import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes outcome events to the outbox table instead of publishing to Kafka
 * directly. The OutboxPoller delivers them asynchronously. This guarantees
 * the event is never lost: the outbox row commits atomically with the
 * settlement in the same Postgres transaction, so a crash between commit
 * and publish is safe — the poller retries on restart.
 */
@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final String clearedTopic;
    private final String rejectedTopic;

    public PaymentEventPublisher(OutboxEventRepository outbox,
            ObjectMapper objectMapper,
            @Value("${rtpn.topics.cleared}") String clearedTopic,
            @Value("${rtpn.topics.rejected}") String rejectedTopic) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clearedTopic = clearedTopic;
        this.rejectedTopic = rejectedTopic;
    }

    public void publish(PaymentMessage msg, ClearingResult result) {
        String topic = result.status() == ClearingStatus.SETTLED ? clearedTopic : rejectedTopic;

        Map<String, Object> event = new HashMap<>();
        event.put("messageId", msg.getMessageId());
        event.put("endToEndId", msg.getEndToEndId());
        event.put("debtorParticipant", msg.getDebtorParticipant());
        event.put("creditorParticipant", msg.getCreditorParticipant());
        event.put("amount", msg.getAmount());
        event.put("currency", msg.getCurrency());
        event.put("status", result.status().name());
        event.put("reason", result.reason());
        event.put("occurredAt", Instant.now().toString());

        try {
            String payload = objectMapper.writeValueAsString(event);
            outbox.save(new OutboxEvent(msg.getMessageId(), topic, payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for message {}", msg.getMessageId(), e);
        }
    }
}