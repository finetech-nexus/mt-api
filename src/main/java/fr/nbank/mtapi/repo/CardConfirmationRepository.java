package fr.nbank.mtapi.repo;

import fr.nbank.mtapi.domain.CardConfirmation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardConfirmationRepository extends JpaRepository<CardConfirmation, Long> {
    Optional<CardConfirmation> findByTransactionId(String transactionId);

    Optional<CardConfirmation> findFirstByClientReferenceOrderByCreatedAtDesc(String clientReference);

    List<CardConfirmation> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    List<CardConfirmation> findByUserIdAndStatusInOrderByCreatedAtDesc(String userId, Collection<String> statuses);

    @Query("select c from CardConfirmation c where c.userId = :userId and c.status = 'INSUFFICIENT_BALANCE' and (c.seen = false or c.seen is null) order by c.createdAt desc")
    List<CardConfirmation> findOpenNotices(String userId);
}
