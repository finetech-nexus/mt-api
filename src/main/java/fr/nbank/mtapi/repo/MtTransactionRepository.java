package fr.nbank.mtapi.repo;

import fr.nbank.mtapi.domain.MtTransaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MtTransactionRepository extends JpaRepository<MtTransaction, Long>, JpaSpecificationExecutor<MtTransaction> {
    Optional<MtTransaction> findByExternalId(String externalId);
    Optional<MtTransaction> findFirstByReferenceAndOperation(String reference, String operation);
    Optional<MtTransaction> findFirstByReference(String reference);

    @Query("""
            select distinct t.originalExternalId from MtTransaction t
            where t.originalExternalId in :codes
              and t.operation in ('CAPTURE', 'REFUND', 'VOID')
              and t.status <> 'FAILED'
            """)
    List<String> findFollowUpCodes(@Param("codes") Collection<String> codes);
}
