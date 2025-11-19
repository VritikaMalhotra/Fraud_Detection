package com.fraud.engine.web;

import com.fraud.engine.db.DecisionEntity;
import com.fraud.engine.db.DecisionRepo;
import com.fraud.engine.db.TransactionEntity;
import com.fraud.engine.db.TransactionRepo;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final DecisionRepo decisionRepo;
    private final TransactionRepo transactionRepo;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> overview() {
        long totalDecisions = decisionRepo.count();
        long allowCount = decisionRepo.countByDecision("ALLOW");
        long reviewCount = decisionRepo.countByDecision("REVIEW");
        long blockCount = decisionRepo.countByDecision("BLOCK");

        long totalTransactions = transactionRepo.count();
        Double totalVolume = transactionRepo.sumAmounts();

        DecisionEntity latestDecision = decisionRepo.findFirstByOrderByEvaluatedAtDesc();
        Instant latestDecisionAt = latestDecision != null ? latestDecision.getEvaluatedAt() : null;

        TransactionEntity latestTx = transactionRepo.findFirstByOrderByOccurredAtDesc();
        Instant latestTransactionAt = latestTx != null ? latestTx.getOccurredAt() : null;

        double blockRate = totalDecisions == 0 ? 0.0 : (double) blockCount / totalDecisions;
        Double reviewLatencyMs = decisionRepo.averageLatencyForDecision("REVIEW");

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("totalDecisions", totalDecisions);
        payload.put("allowCount", allowCount);
        payload.put("reviewCount", reviewCount);
        payload.put("blockCount", blockCount);
        payload.put("blockRate", blockRate);
        payload.put("totalTransactions", totalTransactions);
        payload.put("totalVolume", totalVolume != null ? totalVolume : 0.0);
        payload.put("latestDecisionAt", latestDecisionAt);
        payload.put("latestTransactionAt", latestTransactionAt);
        payload.put("averageReviewLatencyMs", reviewLatencyMs != null ? reviewLatencyMs : 0.0);

        return ResponseEntity.ok(payload);
    }
}
