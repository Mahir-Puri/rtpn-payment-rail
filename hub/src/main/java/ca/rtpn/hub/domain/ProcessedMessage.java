package ca.rtpn.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Idempotency record. The primary-key constraint on message_id is what makes
 * exactly-once settlement hold even under concurrent redelivery: two threads
 * racing on the same message cannot both commit.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "debtor_participant", nullable = false)
    private String debtorParticipant;

    @Column(name = "creditor_participant", nullable = false)
    private String creditorParticipant;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String messageId, String debtorParticipant, String creditorParticipant,
            BigDecimal amount, String status) {
        this.messageId = messageId;
        this.debtorParticipant = debtorParticipant;
        this.creditorParticipant = creditorParticipant;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getDebtorParticipant() {
        return debtorParticipant;
    }

    public String getCreditorParticipant() {
        return creditorParticipant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
