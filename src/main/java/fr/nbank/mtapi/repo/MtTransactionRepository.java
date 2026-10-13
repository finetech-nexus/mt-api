package fr.nbank.mtapi.repo;

import fr.nbank.mtapi.domain.MtTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MtTransactionRepository extends JpaRepository<MtTransaction, Long>, JpaSpecificationExecutor<MtTransaction> {
    Optional<MtTransaction> findByExternalId(String externalId);
    Optional<MtTransaction> findFirstByReferenceAndOperation(String reference, String operation);
    Optional<MtTransaction> findFirstByReference(String reference);
}
