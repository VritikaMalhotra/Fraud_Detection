package com.fraud.engine.db;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepo extends JpaRepository<TransactionEntity, String> {

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM TransactionEntity t")
    Double sumAmounts();

    TransactionEntity findFirstByOrderByOccurredAtDesc();

    List<TransactionEntity> findByOccurredAtAfterOrderByOccurredAtDesc(Instant since);

    // Sum amounts by userId
    @Query("SELECT COALESCE(SUM(t.amount),0) FROM TransactionEntity t WHERE t.userId = :userId")
    Double sumAmountsByUserId(@Param("userId") String userId);

    // Find first transaction by userId (oldest)
    Optional<TransactionEntity> findFirstByUserIdOrderByOccurredAtAsc(String userId);

    // Find last transaction by userId (newest)
    Optional<TransactionEntity> findFirstByUserIdOrderByOccurredAtDesc(String userId);
}
