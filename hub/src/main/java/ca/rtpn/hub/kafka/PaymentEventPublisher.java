package ca.rtpn.hub.kafka;

import ca.rtpn.hub.model.ClearingResult;
import ca.rtpn.hub.model.ClearingStatus;
import ca.rtpn.hub.model.PaymentMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes the outcome of clearing to payments.cleared or payments.rejected,
 * keyed by messageId. Downstream consumers (notification, analytics, the
 * receiving participant) subscribe to these instead of coupling to the hub.
 */
@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String clearedTopic;
    private final String rejectedTopic;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${rtpn.topics.cleared}") String clearedTopic,
            @Value("${rtpn.topics.rejected}") String rejectedTopic) {
        this.kafkaTemplate = kafkaTemplate;
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
            kafkaTemplate.send(topic, msg.getMessageId(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outcome event for message {}", msg.getMessageId(), e);
        }
    }
}
