package ca.rtpn.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One leg of a double-entry posting. Every settled payment produces exactly
 * two rows: a DEBIT against the debtor participant and a CREDIT to the
 * creditor participant, linked by message_id. Entries are append-only.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public static final String DEBIT = "DEBIT";
    public static final String CREDIT = "CREDIT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "participant_id", nullable = false)
    private String participantId;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(String messageId, String participantId, String direction,
            BigDecimal amount, String currency) {
        this.messageId = messageId;
        this.participantId = participantId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
