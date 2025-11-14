package com.fraud.engine.web;

import com.fraud.engine.db.DecisionEntity;
import com.fraud.engine.db.DecisionRepo;
import com.fraud.engine.db.TransactionRepo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionQueryController {

    private final TransactionRepo transactionRepo;
    private final DecisionRepo decisionRepo;

    @Value
    @Builder
    public static class TransactionView {
        String id;
        String transactionId;
        String userId;
        Double amount;
        String currency;
        String merchantId;
        Instant occurredAt;
        String decision;
        Double score;
        List<String> reasons;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TransactionView>> recentTransactions(
        @RequestParam(value = "limit", defaultValue = "25") int limit
    ) {
        int pageSize = Math.min(Math.max(limit, 1), 200);
        var page = transactionRepo.findAll(
            PageRequest.of(0, pageSize, Sort.by("occurredAt").descending())
        );
        List<TransactionView> views = page.getContent().stream()
            .map(tx -> toView(tx.getTransactionId(), tx.getUserId(), tx.getAmount(), tx.getCurrency(), tx.getMerchantId(), tx.getOccurredAt()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    @GetMapping("/since")
    public ResponseEntity<List<TransactionView>> transactionsSince(
        @RequestParam(value = "minutes", defaultValue = "60") int minutes
    ) {
        int window = Math.max(minutes, 1);
        Instant since = Instant.now().minus(window, ChronoUnit.MINUTES);
        List<TransactionView> results = transactionRepo.findByOccurredAtAfterOrderByOccurredAtDesc(since).stream()
            .map(tx -> toView(tx.getTransactionId(), tx.getUserId(), tx.getAmount(), tx.getCurrency(), tx.getMerchantId(), tx.getOccurredAt()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    private TransactionView toView(String transactionId, String userId, Double amount, String currency, String merchantId, Instant occurredAt) {
        DecisionEntity decision = decisionRepo.findById(transactionId).orElse(null);
        return TransactionView.builder()
            .id(transactionId)
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .merchantId(merchantId)
            .occurredAt(occurredAt)
            .decision(decision != null ? decision.getDecision() : "ALLOW")
            .score(decision != null ? decision.getScore() : 0.0)
            .reasons(decision != null ? decision.getReasons() : List.of())
            .build();
    }
}
