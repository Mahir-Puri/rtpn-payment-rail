package ca.rtpn.hub.repository;

import ca.rtpn.hub.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByMessageId(String messageId);
}
