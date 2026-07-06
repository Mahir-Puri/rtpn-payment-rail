package ca.rtpn.hub.repository;

import ca.rtpn.hub.audit.PaymentAuditRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentAuditRepository extends MongoRepository<PaymentAuditRecord, String> {

    List<PaymentAuditRecord> findByMessageId(String messageId);
}
