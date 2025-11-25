package com.fraud.engine.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraud.common.model.FraudDecision;
import com.fraud.common.model.Transaction;
import com.fraud.engine.db.DecisionEntity;
import com.fraud.engine.db.DecisionRepo;
import com.fraud.engine.db.TransactionEntity;
import com.fraud.engine.db.TransactionRepo;
import com.fraud.engine.ml.FeatureExtractor;
import com.fraud.engine.ml.MLFraudDetector;
import com.fraud.engine.ml.TransactionFeatures;
import com.fraud.engine.redis.RedisState;
import com.fraud.engine.service.RuleEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class FraudProcessor {

  @Qualifier("decisionKafkaTemplate")
  private final KafkaTemplate<String, FraudDecision> decisionTemplate;
  private final DecisionRepo decisionRepo;
  private final TransactionRepo transactionRepo;
  private final RedisState redisState;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;
  private final MLFraudDetector mlFraudDetector;
  private final FeatureExtractor featureExtractor;

  @Value("${app.topics.out}")
  private String outTopic;

  // ─── Level 4: Configurable Rule Thresholds ───────────────────────────
  @Value("${app.rules.burst.windowSec:60}")
  private int burstWindowSec;

  @Value("${app.rules.burst.count:3}")
  private int burstCount;

  @Value("${app.rules.burst.score:40}")
  private int burstScore;

  @Value("${app.rules.geo.maxSpeedKmph:900}")
  private double geoMaxSpeedKmph;

  @Value("${app.rules.geo.score:50}")
  private int geoScore;

  @Value("${app.rules.device.newWithinDays:7}")
  private int deviceNewWithinDays;

  @Value("${app.rules.device.score:20}")
  private int deviceScore;

  @Value("${app.rules.ip.newWithinDays:7}")
  private int ipNewWithinDays;

  @Value("${app.rules.ip.score:15}")
  private int ipScore;

  @Value("${app.rules.spend.multiplier:5.0}")
  private double spendMultiplier;

  @Value("${app.rules.spend.score:30}")
  private int spendScore;

  @Value("${app.rules.spend.historySize:10}")
  private int spendHistorySize;

  // ML Configuration
  @Value("${app.ml.weight:0.5}")
  private double mlWeight;

  @Value("${app.rules.weight:0.5}")
  private double rulesWeight;

  private Timer decisionLatencyTimer;
  private Counter allowCounter;
  private Counter reviewCounter;
  private Counter blockCounter;
  private Counter duplicateCounter;

  @PostConstruct
  void initMetrics() {
    this.decisionLatencyTimer = Timer.builder("fraud_decision_latency")
        .description("Time spent evaluating a transaction for fraud")
        .publishPercentileHistogram()
        .register(meterRegistry);

    this.allowCounter = Counter.builder("fraud_decisions_total")
        .description("Total decisions by outcome")
        .tag("decision", "ALLOW")
        .register(meterRegistry);

    this.reviewCounter = Counter.builder("fraud_decisions_total")
        .description("Total decisions by outcome")
        .tag("decision", "REVIEW")
        .register(meterRegistry);

    this.blockCounter = Counter.builder("fraud_decisions_total")
        .description("Total decisions by outcome")
        .tag("decision", "BLOCK")
        .register(meterRegistry);

    this.duplicateCounter = Counter.builder("fraud_decision_duplicates_total")
        .description("Events skipped because a decision already exists")
        .register(meterRegistry);
  }

  @Transactional
  @KafkaListener(topics = "${app.topics.in}", groupId = "fraud-service")
  public void onEvent(Transaction tx) {
    if (decisionRepo.existsById(tx.getTransactionId())) {
      duplicateCounter.increment();
      log.debug("Duplicate transaction {} skipped", tx.getTransactionId());
      return;
    }

    Timer.Sample sample = Timer.start(meterRegistry);
    long t0 = System.currentTimeMillis();

    transactionRepo.save(toEntity(tx));

    var res = RuleEngine.evaluate(tx);
    List<String> reasons = new ArrayList<>(res.reasons());
    double score = res.score();

    // ---- Redis-based checks (Level 4: Configurable & Enhanced) ----
    long nowSec = Instant.now().getEpochSecond();
    redisState.recordTransactionTime(tx.getUserId(), nowSec);

    // A) Burst: configurable window and count
    long burstCnt = redisState.recentCount(tx.getUserId(), nowSec, burstWindowSec);
    if (burstCnt >= burstCount) {
      score += burstScore;
      reasons.add("burst_%ds".formatted(burstWindowSec));
    }

    // B) Spend spike: compare to median of last N transactions
    double medianAmount = redisState.getMedianAmount(tx.getUserId());
    if (medianAmount > 0 && tx.getAmount() >= medianAmount * spendMultiplier) {
      score += spendScore;
      reasons.add("spend_spike");
    }
    // Record current amount for future comparisons
    redisState.recordAmount(tx.getUserId(), tx.getAmount(), spendHistorySize);

    // C) Device/IP freshness: treat "new" (first-time) as risky
    // Only flag if device/IP is truly NEW (first time seeing it)
    // Devices/IPs that were seen before (even if within 7 days) are considered known/trusted
    if (tx.getDevice() != null) {
      boolean isNewDevice = redisState.recordDevice(tx.getUserId(), tx.getDevice().getId(), nowSec);
      if (isNewDevice) {
        score += deviceScore;
        reasons.add("new_device");
      }

      boolean isNewIp = redisState.recordIp(tx.getUserId(), tx.getDevice().getIp(), nowSec);
      if (isNewIp) {
        score += ipScore;
        reasons.add("new_ip");
      }
    }

    // D) Geo-impossible: configurable speed threshold
    if (tx.getLocation() != null && tx.getLocation().getLat() != null && tx.getLocation().getLon() != null) {
      var last = redisState.getLastLoc(tx.getUserId());
      if (last != null) {
        double km = RedisState.haversineKm(
            last.lat(), last.lon(),
            tx.getLocation().getLat(), tx.getLocation().getLon());
        long dt = Math.max(1, nowSec - last.epochSec());
        double speed = km / (dt / 3600.0); // km/h
        if (speed > geoMaxSpeedKmph) {
          score += geoScore;
          reasons.add("geo_impossible");
        }
      }
      redisState.setLastLoc(
          tx.getUserId(), tx.getLocation().getLat(), tx.getLocation().getLon(), nowSec);
    }

    // ─── Decision Logic: Rule-based with ML for REVIEW zone ──────────────────
    // Strategy:
    // - Score 0-29: ALLOW (clear legitimate)
    // - Score 60+: BLOCK (clear fraud)
    // - Score 30-59: Use ML model to make final binary decision (ALLOW or BLOCK)

    String decisionStr;
    double finalScore = score;

    // First, determine rule-based decision zone
    String ruleDecision = RuleEngine.toDecision(score);

    if ("REVIEW".equals(ruleDecision) && mlFraudDetector.isEnabled()) {
      // Transaction is in REVIEW zone (30-59) - use ML for final decision
      try {
        // Extract features for ML model
        TransactionFeatures features = featureExtractor.extractFeatures(
            tx, nowSec, score, reasons);

        // Get ML prediction
        double mlProbability = mlFraudDetector.predictFraudProbability(features);

        // Use ML probability to make binary decision
        // ML probability > 0.5 means fraud (BLOCK), otherwise ALLOW
        if (mlProbability > 0.5) {
          decisionStr = "BLOCK";
          reasons.add("ml_flagged_as_fraud");
          // Boost score to reflect ML decision
          finalScore = 60 + (mlProbability * 40); // 60-100 range for BLOCK
        } else {
          decisionStr = "ALLOW";
          reasons.add("ml_approved");
          // Lower score to reflect ML decision
          finalScore = mlProbability * 30; // 0-30 range for ALLOW
        }

        log.info("REVIEW zone transaction {} decided by ML: probability={}, decision={}",
            tx.getTransactionId(), mlProbability, decisionStr);
      } catch (Exception e) {
        log.warn("ML prediction failed for REVIEW transaction {}: {}. Keeping as REVIEW.",
            tx.getTransactionId(), e.getMessage());
        // If ML fails, keep as REVIEW for manual review
        decisionStr = "REVIEW";
        finalScore = score;
      }
    } else {
      // Clear ALLOW or BLOCK based on rules - no ML needed
      decisionStr = ruleDecision;
      finalScore = score;
    }

    double boundedScore = Math.min(finalScore, 100);

    long latency = System.currentTimeMillis() - t0;
    FraudDecision decision = FraudDecision.builder()
        .transactionId(tx.getTransactionId())
        .userId(tx.getUserId())
        .decision(decisionStr)
        .score(boundedScore)
        .reasons(reasons)
        .latencyMs(latency)
        .evaluatedAt(Instant.now())
        .build();

    decisionTemplate.send(outTopic, tx.getUserId(), decision);

    decisionRepo.save(DecisionEntity.builder()
        .transactionId(tx.getTransactionId())
        .userId(tx.getUserId())
        .decision(decisionStr)
        .score(boundedScore)
        .reasons(reasons)
        .latencyMs(latency)
        .evaluatedAt(decision.getEvaluatedAt())
        .build());

    switch (decisionStr) {
      case "ALLOW" -> allowCounter.increment();
      case "REVIEW" -> reviewCounter.increment();
      case "BLOCK" -> blockCounter.increment();
      default -> log.warn("Unknown decision {} for transaction {}", decisionStr, tx.getTransactionId());
    }

    sample.stop(decisionLatencyTimer);
  }

  private TransactionEntity toEntity(Transaction tx) {
    return TransactionEntity.builder()
        .transactionId(tx.getTransactionId())
        .userId(tx.getUserId())
        .amount(tx.getAmount())
        .currency(tx.getCurrency())
        .merchantId(tx.getMerchantId())
        .occurredAt(parseInstant(tx.getTimestamp()))
        .deviceId(tx.getDevice() != null ? tx.getDevice().getId() : null)
        .deviceIp(tx.getDevice() != null ? tx.getDevice().getIp() : null)
        .deviceUserAgent(tx.getDevice() != null ? tx.getDevice().getUserAgent() : null)
        .latitude(tx.getLocation() != null ? tx.getLocation().getLat() : null)
        .longitude(tx.getLocation() != null ? tx.getLocation().getLon() : null)
        .city(tx.getLocation() != null ? tx.getLocation().getCity() : null)
        .country(tx.getLocation() != null ? tx.getLocation().getCountry() : null)
        .rawPayload(writeJson(tx))
        .build();
  }

  private Instant parseInstant(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(timestamp);
    } catch (DateTimeParseException ex) {
      log.debug("Unable to parse timestamp '{}' for transaction", timestamp, ex);
      try {
        return Instant.from(java.time.ZonedDateTime.parse(timestamp));
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private String writeJson(Transaction tx) {
    try {
      return objectMapper.writeValueAsString(tx);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialise transaction {} for audit", tx.getTransactionId(), e);
      return null;
    }
  }
}
