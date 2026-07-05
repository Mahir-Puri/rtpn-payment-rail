package ca.rtpn.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A participant's prefunded settlement account at the hub. The cached
 * {@code balance} is the fast path; the ledger entries remain the source of
 * truth, and the reconciliation job verifies the two never diverge.
 */
@Entity
@Table(name = "settlement_accounts")
public class SettlementAccount {

    @Id
    @Column(name = "participant_id")
    private String participantId;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "currency", nullable = false)
    private String currency;

    protected SettlementAccount() {
    }

    public SettlementAccount(String participantId, BigDecimal balance,
            BigDecimal openingBalance, String currency) {
        this.participantId = participantId;
        this.balance = balance;
        this.openingBalance = openingBalance;
        this.currency = currency;
    }

    public String getParticipantId() {
        return participantId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public String getCurrency() {
        return currency;
    }
}
