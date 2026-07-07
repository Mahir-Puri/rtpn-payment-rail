package ca.rtpn.hub.api;

import ca.rtpn.hub.domain.ProcessedMessage;
import ca.rtpn.hub.domain.SettlementAccount;
import ca.rtpn.hub.model.ClearingResult;
import ca.rtpn.hub.model.PaymentMessage;
import ca.rtpn.hub.repository.ProcessedMessageRepository;
import ca.rtpn.hub.repository.SettlementAccountRepository;
import ca.rtpn.hub.service.ClearingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HubController {

    private final ClearingService clearingService;
    private final SettlementAccountRepository accounts;
    private final ProcessedMessageRepository processedMessages;

    public HubController(ClearingService clearingService,
            SettlementAccountRepository accounts,
            ProcessedMessageRepository processedMessages) {
        this.clearingService = clearingService;
        this.accounts = accounts;
        this.processedMessages = processedMessages;
    }

    /** Synchronous submission path — same pipeline as the Kafka rail. */
    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> submitPayment(@RequestBody PaymentMessage msg) {
        ClearingResult result = clearingService.process(msg);
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", msg.getMessageId());
        body.put("status", result.status().name());
        body.put("reason", result.reason());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/payments/{messageId}")
    public ResponseEntity<Map<String, Object>> paymentStatus(@PathVariable String messageId) {
        return processedMessages.findById(messageId)
                .map(this::toStatusBody)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/accounts")
    public List<SettlementAccount> accounts() {
        return accounts.findAll();
    }

    @GetMapping("/accounts/{participantId}")
    public ResponseEntity<SettlementAccount> account(@PathVariable String participantId) {
        return accounts.findById(participantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private Map<String, Object> toStatusBody(ProcessedMessage pm) {
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", pm.getMessageId());
        body.put("debtorParticipant", pm.getDebtorParticipant());
        body.put("creditorParticipant", pm.getCreditorParticipant());
        body.put("amount", pm.getAmount());
        body.put("status", pm.getStatus());
        body.put("processedAt", pm.getCreatedAt().toString());
        return body;
    }
}
