package ca.rtpn.hub.repository;

import ca.rtpn.hub.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    long countByDebtorParticipantAndCreatedAtAfter(String debtorParticipant, Instant after);
}
