package com.fraud.engine.db;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DecisionRepo extends JpaRepository<DecisionEntity, String> {

    // Find decisions by userId
    Page<DecisionEntity> findByUserId(String userId, Pageable pageable);

    // Find decisions by decision type (ALLOW, REVIEW, BLOCK)
    Page<DecisionEntity> findByDecision(String decision, Pageable pageable);

    // Find decisions by userId and date range
    @Query("SELECT d FROM DecisionEntity d WHERE d.userId = :userId AND d.evaluatedAt BETWEEN :startDate AND :endDate ORDER BY d.evaluatedAt DESC")
    List<DecisionEntity> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );

    // Find decisions by date range
    @Query("SELECT d FROM DecisionEntity d WHERE d.evaluatedAt BETWEEN :startDate AND :endDate ORDER BY d.evaluatedAt DESC")
    Page<DecisionEntity> findByDateRange(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );

    // Find high-risk decisions (REVIEW or BLOCK)
    @Query("SELECT d FROM DecisionEntity d WHERE d.decision IN ('REVIEW', 'BLOCK') ORDER BY d.evaluatedAt DESC")
    Page<DecisionEntity> findHighRiskDecisions(Pageable pageable);

    // Count decisions by userId
    long countByUserId(String userId);

    long countByDecision(String decision);

    DecisionEntity findFirstByOrderByEvaluatedAtDesc();

    @Query("SELECT COALESCE(AVG(d.latencyMs),0) FROM DecisionEntity d WHERE d.decision = :decision")
    Double averageLatencyForDecision(@Param("decision") String decision);

    // Count decisions by userId and decision type
    long countByUserIdAndDecision(String userId, String decision);

    // Get average score by userId
    @Query("SELECT COALESCE(AVG(d.score),0) FROM DecisionEntity d WHERE d.userId = :userId")
    Double averageScoreByUserId(@Param("userId") String userId);

    // Get top N recent decisions for a user
    List<DecisionEntity> findTop10ByUserIdOrderByEvaluatedAtDesc(String userId);
}