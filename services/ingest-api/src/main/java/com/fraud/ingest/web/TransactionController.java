package com.fraud.ingest.web;

import com.fraud.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.time.Duration;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final KafkaTemplate<String, Transaction> kafkaTemplate;
    private final StringRedisTemplate redis;

    @Value("${app.kafka.topic}")
    private String topic;

    // how long to remember seen IDs (tune as you like)
    private static final Duration IDEMP_TTL = Duration.ofHours(48);

    @PostMapping
    public ResponseEntity<?> ingest(@Validated @RequestBody Transaction tx) {
        // 1) Generate ID if missing/blank
        if (tx.getTransactionId() == null || tx.getTransactionId().isBlank()) {
            tx.setTransactionId(UUID.randomUUID().toString()); // or ULID if you prefer
        }

        // 1) Dedupe check in Redis: SET key if not exists with TTL
        String key = "txid:" + tx.getTransactionId();
        Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", IDEMP_TTL);
        if (Boolean.FALSE.equals(firstTime)) {
            // already seen -> reject to avoid duplicate processing
            return ResponseEntity.status(409) // Conflict
                    .body(java.util.Map.of(
                            "error", "Duplicate transactionId",
                            "transactionId", tx.getTransactionId()));
        }

        // 2) Publish to Kafka (partition by userId)
        kafkaTemplate.send(topic, tx.getUserId(), tx);

        // 3) Return 202 + echo the ID so clients can log it
        return ResponseEntity.accepted()
                .header("X-Transaction-Id", tx.getTransactionId())
                .build();
    }
}
