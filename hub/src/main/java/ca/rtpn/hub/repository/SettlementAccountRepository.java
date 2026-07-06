package ca.rtpn.hub.repository;

import ca.rtpn.hub.domain.SettlementAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, String> {

    /**
     * SELECT ... FOR UPDATE. Callers must acquire locks in a globally
     * consistent order (lexicographic participant id) to avoid deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SettlementAccount a where a.participantId = :id")
    Optional<SettlementAccount> lockByParticipantId(@Param("id") String id);
}
