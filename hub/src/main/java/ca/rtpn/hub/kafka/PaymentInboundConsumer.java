package ca.rtpn.hub.kafka;

import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.service.ClearingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Entry point for the payment rail: consumes credit-transfer messages from
 * payments.inbound and hands them to the clearing pipeline. Unparseable
 * payloads are logged and skipped — they carry no valid messageId, so there
 * is nothing to settle or reject downstream.
 */
@Service
public class PaymentInboundConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentInboundConsumer.class);

    private final ClearingService clearingService;
    private final ObjectMapper objectMapper;

    public PaymentInboundConsumer(ClearingService clearingService, ObjectMapper objectMapper) {
        this.clearingService = clearingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${rtpn.topics.inbound}")
    public void onMessage(String value) {
        PaymentMessage msg;
        try {
            msg = objectMapper.readValue(value, PaymentMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("Discarding unparseable payment message: {}", value, e);
            return;
        }
        clearingService.process(msg);
    }
}
