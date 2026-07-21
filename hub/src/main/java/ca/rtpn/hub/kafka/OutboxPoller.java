package ca.rtpn.hub.kafka;

import ca.rtpn.hub.domain.OutboxEvent;
import ca.rtpn.hub.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox table every second for unpublished events and delivers
 * them to Kafka. Marks each row published only after the send succeeds.
 * If the poller crashes mid-batch the unpublished rows are retried on
 * the next poll — consumers must be idempotent on messageId (they already
 * are: the hub's own consumer ignores duplicates via processed_messages).
 */
@Service
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPoller(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outbox.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                kafka.send(event.getTopic(), event.getMessageId(), event.getPayload()).get();
                event.markPublished();
                outbox.save(event);
            } catch (Exception e) {
                log.warn("Outbox publish failed for message {}, will retry", event.getMessageId(), e);
                break; // stop batch on failure, retry next poll
            }
        }
    }
}