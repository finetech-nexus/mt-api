package fr.nbank.mtapi.repo;

import fr.nbank.mtapi.domain.CardConfirmation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardConfirmationRepository extends JpaRepository<CardConfirmation, Long> {
    Optional<CardConfirmation> findByTransactionId(String transactionId);

    List<CardConfirmation> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);
}
