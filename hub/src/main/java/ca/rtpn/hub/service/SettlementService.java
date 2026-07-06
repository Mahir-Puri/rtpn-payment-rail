package ca.rtpn.hub.service;

import ca.rtpn.hub.domain.LedgerEntry;
import ca.rtpn.hub.domain.ProcessedMessage;
import ca.rtpn.hub.domain.SettlementAccount;
import ca.rtpn.hub.model.ClearingResult;
import ca.rtpn.hub.model.ClearingStatus;
import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.repository.LedgerEntryRepository;
import ca.rtpn.hub.repository.ProcessedMessageRepository;
import ca.rtpn.hub.repository.SettlementAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Real-time gross settlement: each payment settles individually and with
 * finality inside a single database transaction. The invariants:
 *
 * 1. Atomicity — the debit, the credit, and the idempotency record commit
 * together or not at all.
 * 2. No overdrafts — a debtor cannot settle beyond its prefunded balance.
 * 3. Exactly-once — the primary key on processed_messages rejects a
 * concurrent duplicate at the database level, not just in application
 * logic.
 *
 * Deadlock avoidance: account row locks are always acquired in lexicographic
 * participant-id order, regardless of payment direction.
 */
@Service
public class SettlementService {

    private final SettlementAccountRepository accounts;
    private final LedgerEntryRepository ledgerEntries;
    private final ProcessedMessageRepository processedMessages;

    public SettlementService(SettlementAccountRepository accounts,
            LedgerEntryRepository ledgerEntries,
            ProcessedMessageRepository processedMessages) {
        this.accounts = accounts;
        this.ledgerEntries = ledgerEntries;
        this.processedMessages = processedMessages;
    }

    @Transactional
    public ClearingResult settle(PaymentMessage msg) {
        String debtorId = msg.getDebtorParticipant();
        String creditorId = msg.getCreditorParticipant();

        String firstLock = debtorId.compareTo(creditorId) <= 0 ? debtorId : creditorId;
        String secondLock = firstLock.equals(debtorId) ? creditorId : debtorId;

        Optional<SettlementAccount> firstAccount = accounts.lockByParticipantId(firstLock);
        Optional<SettlementAccount> secondAccount = accounts.lockByParticipantId(secondLock);

        if (firstAccount.isEmpty() || secondAccount.isEmpty()) {
            return record(msg, ClearingStatus.REJECTED_VALIDATION, "unknown participant");
        }

        SettlementAccount debtor = firstLock.equals(debtorId) ? firstAccount.get() : secondAccount.get();
        SettlementAccount creditor = firstLock.equals(debtorId) ? secondAccount.get() : firstAccount.get();

        if (debtor.getBalance().compareTo(msg.getAmount()) < 0) {
            return record(msg, ClearingStatus.REJECTED_INSUFFICIENT_FUNDS,
                    "insufficient settlement balance for " + debtorId);
        }

        debtor.setBalance(debtor.getBalance().subtract(msg.getAmount()));
        creditor.setBalance(creditor.getBalance().add(msg.getAmount()));
        accounts.save(debtor);
        accounts.save(creditor);

        ledgerEntries.save(new LedgerEntry(msg.getMessageId(), debtorId,
                LedgerEntry.DEBIT, msg.getAmount(), msg.getCurrency()));
        ledgerEntries.save(new LedgerEntry(msg.getMessageId(), creditorId,
                LedgerEntry.CREDIT, msg.getAmount(), msg.getCurrency()));

        return record(msg, ClearingStatus.SETTLED, null);
    }

    /**
     * Persists the terminal status of a message in its own transaction. Used
     * for rejections that happen before settlement so retries of a rejected
     * message stay idempotent too.
     */
    @Transactional
    public ClearingResult recordRejection(PaymentMessage msg, ClearingStatus status, String reason) {
        try {
            return record(msg, status, reason);
        } catch (DataIntegrityViolationException e) {
            return ClearingResult.of(ClearingStatus.DUPLICATE, "message already processed");
        }
    }

    private ClearingResult record(PaymentMessage msg, ClearingStatus status, String reason) {
        // Validation-rejected messages can be missing NOT NULL fields;
        // coalesce so the idempotency record still persists.
        processedMessages.save(new ProcessedMessage(
                msg.getMessageId(),
                msg.getDebtorParticipant() == null ? "UNKNOWN" : msg.getDebtorParticipant(),
                msg.getCreditorParticipant() == null ? "UNKNOWN" : msg.getCreditorParticipant(),
                msg.getAmount() == null ? java.math.BigDecimal.ZERO : msg.getAmount(),
                status.name()));
        return ClearingResult.of(status, reason);
    }
}
