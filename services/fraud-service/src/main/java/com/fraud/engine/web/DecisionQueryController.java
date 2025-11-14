package com.fraud.engine.web;

import com.fraud.engine.db.DecisionEntity;
import com.fraud.engine.db.DecisionRepo;
import com.fraud.engine.db.TransactionEntity;
import com.fraud.engine.db.TransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/decisions")
@RequiredArgsConstructor
public class DecisionQueryController {

    private final DecisionRepo decisionRepo;
    private final TransactionRepo transactionRepo;

    /**
     * Get a decision by transaction ID
     * GET /api/decisions/{transactionId}
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<DecisionEntity> getDecisionByTransactionId(@PathVariable("transactionId") String transactionId) {
        Optional<DecisionEntity> decision = decisionRepo.findById(transactionId);
        return decision.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Query decisions by userId with pagination
     * GET /api/decisions?userId=charlie&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<DecisionEntity>> queryDecisions(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "decision", required = false) String decision,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("evaluatedAt").descending());

        Page<DecisionEntity> results;

        // Query by userId
        if (userId != null && !userId.isBlank()) {
            results = decisionRepo.findByUserId(userId, pageRequest);
        }
        // Query by decision type
        else if (decision != null && !decision.isBlank()) {
            results = decisionRepo.findByDecision(decision.toUpperCase(), pageRequest);
        }
        // Query by date range
        else if (startDate != null && endDate != null) {
            results = decisionRepo.findByDateRange(startDate, endDate, pageRequest);
        }
        // Default: return all decisions
        else {
            results = decisionRepo.findAll(pageRequest);
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Get decisions for a user within a date range
     * GET /api/decisions/user/{userId}/range?startDate=2025-11-01T00:00:00Z&endDate=2025-11-10T23:59:59Z
     */
    @GetMapping("/user/{userId}/range")
    public ResponseEntity<List<DecisionEntity>> getDecisionsByUserAndDateRange(
            @PathVariable("userId") String userId,
            @RequestParam(value = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        List<DecisionEntity> decisions = decisionRepo.findByUserIdAndDateRange(userId, startDate, endDate);
        return ResponseEntity.ok(decisions);
    }

    /**
     * Get high-risk decisions (REVIEW or BLOCK)
     * GET /api/decisions/high-risk?page=0&size=50
     */
    @GetMapping("/high-risk")
    public ResponseEntity<Page<DecisionEntity>> getHighRiskDecisions(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<DecisionEntity> results = decisionRepo.findHighRiskDecisions(pageRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Get detailed decision statistics for a user
     * GET /api/decisions/user/{userId}/stats
     */
    @GetMapping("/user/{userId}/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getUserStats(@PathVariable("userId") String userId) {
        long totalDecisions = decisionRepo.countByUserId(userId);
        long allowCount = decisionRepo.countByUserIdAndDecision(userId, "ALLOW");
        long reviewCount = decisionRepo.countByUserIdAndDecision(userId, "REVIEW");
        long blockCount = decisionRepo.countByUserIdAndDecision(userId, "BLOCK");
        
        // Get average score
        Double avgScore = decisionRepo.averageScoreByUserId(userId);
        
        // Get total transaction volume for this user
        Double totalVolume = transactionRepo.sumAmountsByUserId(userId);
        
        // Get recent decisions (last 10)
        List<DecisionEntity> recentDecisions = decisionRepo.findTop10ByUserIdOrderByEvaluatedAtDesc(userId);
        
        // Get first and last transaction dates
        Instant firstTransactionAt = transactionRepo.findFirstByUserIdOrderByOccurredAtAsc(userId)
            .map(TransactionEntity::getOccurredAt).orElse(null);
        Instant lastTransactionAt = transactionRepo.findFirstByUserIdOrderByOccurredAtDesc(userId)
            .map(TransactionEntity::getOccurredAt).orElse(null);
        
        // Calculate risk rate
        double riskRate = totalDecisions > 0 
            ? ((double)(reviewCount + blockCount) / totalDecisions) * 100 
            : 0.0;
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("userId", userId);
        stats.put("totalDecisions", totalDecisions);
        stats.put("allowCount", allowCount);
        stats.put("reviewCount", reviewCount);
        stats.put("blockCount", blockCount);
        stats.put("riskRate", riskRate);
        stats.put("averageScore", avgScore != null ? avgScore : 0.0);
        stats.put("totalVolume", totalVolume != null ? totalVolume : 0.0);
        stats.put("firstTransactionAt", firstTransactionAt);
        stats.put("lastTransactionAt", lastTransactionAt);
        stats.put("recentDecisions", recentDecisions.stream()
            .map(d -> {
                Map<String, Object> decisionMap = new java.util.HashMap<>();
                decisionMap.put("transactionId", d.getTransactionId());
                decisionMap.put("decision", d.getDecision());
                decisionMap.put("score", d.getScore());
                decisionMap.put("reasons", d.getReasons() != null ? d.getReasons() : List.of());
                decisionMap.put("evaluatedAt", d.getEvaluatedAt());
                return decisionMap;
            })
            .collect(Collectors.toList())
        );
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Health check endpoint
     * GET /api/decisions/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        long count = decisionRepo.count();
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "totalDecisions", String.valueOf(count)
        ));
    }
}
