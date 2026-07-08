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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementAccountRepository accounts;
    @Mock
    private LedgerEntryRepository ledgerEntries;
    @Mock
    private ProcessedMessageRepository processedMessages;

    private SettlementService settlement;

    private SettlementAccount alpha;
    private SettlementAccount beta;

    @BeforeEach
    void setUp() {
        settlement = new SettlementService(accounts, ledgerEntries, processedMessages);
        alpha = new SettlementAccount("ALPHA_BANK", new BigDecimal("1000.00"),
                new BigDecimal("1000.00"), "CAD");
        beta = new SettlementAccount("BETA_BANK", new BigDecimal("500.00"),
                new BigDecimal("500.00"), "CAD");
    }

    private PaymentMessage payment(String debtor, String creditor, String amount) {
        return new PaymentMessage("MSG-1", "E2E-1", debtor, creditor,
                new BigDecimal(amount), "CAD");
    }

    @Test
    void settlesAndPostsBalancedDoubleEntry() {
        when(accounts.lockByParticipantId("ALPHA_BANK")).thenReturn(Optional.of(alpha));
        when(accounts.lockByParticipantId("BETA_BANK")).thenReturn(Optional.of(beta));

        ClearingResult result = settlement.settle(payment("ALPHA_BANK", "BETA_BANK", "250.00"));

        assertEquals(ClearingStatus.SETTLED, result.status());
        assertEquals(new BigDecimal("750.00"), alpha.getBalance());
        assertEquals(new BigDecimal("750.00"), beta.getBalance());

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntries, org.mockito.Mockito.times(2)).save(entryCaptor.capture());
        List<LedgerEntry> entries = entryCaptor.getAllValues();

        LedgerEntry debit = entries.stream()
                .filter(e -> LedgerEntry.DEBIT.equals(e.getDirection())).findFirst().orElseThrow();
        LedgerEntry credit = entries.stream()
                .filter(e -> LedgerEntry.CREDIT.equals(e.getDirection())).findFirst().orElseThrow();

        assertEquals("ALPHA_BANK", debit.getParticipantId());
        assertEquals("BETA_BANK", credit.getParticipantId());
        assertEquals(debit.getAmount(), credit.getAmount()); // every debit has its credit
        assertEquals("MSG-1", debit.getMessageId());
        assertEquals("MSG-1", credit.getMessageId());

        ArgumentCaptor<ProcessedMessage> pmCaptor = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processedMessages).save(pmCaptor.capture());
        assertEquals(ClearingStatus.SETTLED.name(), pmCaptor.getValue().getStatus());
    }

    @Test
    void rejectsWhenDebtorLacksLiquidity() {
        when(accounts.lockByParticipantId("ALPHA_BANK")).thenReturn(Optional.of(alpha));
        when(accounts.lockByParticipantId("BETA_BANK")).thenReturn(Optional.of(beta));

        ClearingResult result = settlement.settle(payment("BETA_BANK", "ALPHA_BANK", "9999.00"));

        assertEquals(ClearingStatus.REJECTED_INSUFFICIENT_FUNDS, result.status());
        assertEquals(new BigDecimal("1000.00"), alpha.getBalance()); // untouched
        assertEquals(new BigDecimal("500.00"), beta.getBalance());
        verify(ledgerEntries, never()).save(any());
    }

    @Test
    void rejectsUnknownParticipantWithoutPosting() {
        when(accounts.lockByParticipantId("ALPHA_BANK")).thenReturn(Optional.of(alpha));
        when(accounts.lockByParticipantId("ZETA_BANK")).thenReturn(Optional.empty());

        ClearingResult result = settlement.settle(payment("ALPHA_BANK", "ZETA_BANK", "10.00"));

        assertEquals(ClearingStatus.REJECTED_VALIDATION, result.status());
        assertTrue(result.reason().contains("unknown participant"));
        verify(ledgerEntries, never()).save(any());
    }

    @Test
    void acquiresLocksInLexicographicOrderRegardlessOfDirection() {
        when(accounts.lockByParticipantId("ALPHA_BANK")).thenReturn(Optional.of(alpha));
        when(accounts.lockByParticipantId("BETA_BANK")).thenReturn(Optional.of(beta));

        // BETA pays ALPHA, but ALPHA_BANK must still be locked first.
        settlement.settle(payment("BETA_BANK", "ALPHA_BANK", "50.00"));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(accounts);
        inOrder.verify(accounts).lockByParticipantId("ALPHA_BANK");
        inOrder.verify(accounts).lockByParticipantId("BETA_BANK");
    }
}
