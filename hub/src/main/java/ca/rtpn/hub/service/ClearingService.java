package ca.rtpn.hub.service;

import ca.rtpn.hub.audit.PaymentAuditRecord;
import ca.rtpn.hub.kafka.PaymentEventPublisher;
import ca.rtpn.hub.model.ClearingResult;
import ca.rtpn.hub.model.ClearingStatus;
import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.repository.PaymentAuditRepository;
import ca.rtpn.hub.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The clearing pipeline: validate -> deduplicate -> risk screen -> settle,
 * then audit and publish the outcome. Duplicates are absorbed silently
 * (no second event) so that redelivery is a true no-op downstream.
 */
@Service
public class ClearingService {

    private static final Logger log = LoggerFactory.getLogger(ClearingService.class);

    private final ValidationService validation;
    private final RiskService risk;
    private final SettlementService settlement;
    private final ProcessedMessageRepository processedMessages;
    private final PaymentAuditRepository auditRepository;
    private final PaymentEventPublisher publisher;

    public ClearingService(ValidationService validation,
            RiskService risk,
            SettlementService settlement,
            ProcessedMessageRepository processedMessages,
            PaymentAuditRepository auditRepository,
            PaymentEventPublisher publisher) {
        this.validation = validation;
        this.risk = risk;
        this.settlement = settlement;
        this.processedMessages = processedMessages;
        this.auditRepository = auditRepository;
        this.publisher = publisher;
    }

    public ClearingResult process(PaymentMessage msg) {
        Optional<String> validationError = validation.validate(msg);
        if (validationError.isPresent()) {
            ClearingResult result = ClearingResult.of(ClearingStatus.REJECTED_VALIDATION, validationError.get());
            if (msg.getMessageId() != null && !msg.getMessageId().isBlank()) {
                result = settlement.recordRejection(msg, result.status(), result.reason());
            }
            finish(msg, result);
            return result;
        }

        if (processedMessages.existsById(msg.getMessageId())) {
            log.info("Duplicate message {} ignored", msg.getMessageId());
            return ClearingResult.of(ClearingStatus.DUPLICATE, "message already processed");
        }

        Optional<String> riskError = risk.check(msg);
        if (riskError.isPresent()) {
            ClearingResult result = settlement.recordRejection(msg, ClearingStatus.REJECTED_RISK, riskError.get());
            finish(msg, result);
            return result;
        }

        ClearingResult result;
        try {
            result = settlement.settle(msg);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent redelivery of the same messageId.
            log.info("Concurrent duplicate for message {} absorbed", msg.getMessageId());
            return ClearingResult.of(ClearingStatus.DUPLICATE, "message already processed");
        }

        finish(msg, result);
        return result;
    }

    private void finish(PaymentMessage msg, ClearingResult result) {
        auditRepository.save(new PaymentAuditRecord(
                msg.getMessageId(), result.status().name(), result.reason(), msg));
        publisher.publish(msg, result);
    }
}
