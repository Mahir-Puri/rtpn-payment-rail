package ca.rtpn.hub.audit;

import ca.rtpn.hub.model.PaymentMessage;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Immutable audit trail of every message the hub has seen, including
 * rejections. Stored in MongoDB as documents because audit records are
 * write-once, schema-flexible (real ISO 20022 payloads vary), and queried
 * by pattern rather than joined.
 */
@Document("payment_audit")
public class PaymentAuditRecord {

    @Id
    private String id;
    private String messageId;
    private String status;
    private String reason;
    private PaymentMessage payload;
    private Instant processedAt;

    public PaymentAuditRecord() {
    }

    public PaymentAuditRecord(String messageId, String status, String reason, PaymentMessage payload) {
        this.messageId = messageId;
        this.status = status;
        this.reason = reason;
        this.payload = payload;
        this.processedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public PaymentMessage getPayload() {
        return payload;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
