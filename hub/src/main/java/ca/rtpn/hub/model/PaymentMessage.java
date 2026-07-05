package ca.rtpn.hub.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Simplified credit-transfer message, modelled loosely on the fields of an
 * ISO 20022 pacs.008 (FI-to-FI customer credit transfer). A production rail
 * would carry the full XML schema; the fields here are the ones the clearing
 * layer actually acts on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentMessage {

    private String messageId; // unique per message; the idempotency key
    private String endToEndId; // originator-assigned reference
    private String debtorParticipant; // sending institution
    private String creditorParticipant;// receiving institution
    private BigDecimal amount;
    private String currency;

    public PaymentMessage() {
    }

    public PaymentMessage(String messageId, String endToEndId, String debtorParticipant,
            String creditorParticipant, BigDecimal amount, String currency) {
        this.messageId = messageId;
        this.endToEndId = endToEndId;
        this.debtorParticipant = debtorParticipant;
        this.creditorParticipant = creditorParticipant;
        this.amount = amount;
        this.currency = currency;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getDebtorParticipant() {
        return debtorParticipant;
    }

    public void setDebtorParticipant(String debtorParticipant) {
        this.debtorParticipant = debtorParticipant;
    }

    public String getCreditorParticipant() {
        return creditorParticipant;
    }

    public void setCreditorParticipant(String creditorParticipant) {
        this.creditorParticipant = creditorParticipant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
