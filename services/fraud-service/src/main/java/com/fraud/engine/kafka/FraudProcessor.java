package com.fraud.engine.kafka;

import com.fraud.common.model.Transaction;
import com.fraud.engine.db.*;
import com.fraud.engine.model.Decision;
import com.fraud.engine.service.RuleEngine;
import com.fraud.engine.redis.RedisState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@RequiredArgsConstructor
@Component
public class FraudProcessor {
  private final KafkaTemplate<String, Decision> decisionTemplate;
  private final DecisionRepo repo;
  private final RedisState redisState;

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

  @KafkaListener(topics = "${app.topics.in}", groupId = "fraud-service")
  public void onEvent(Transaction tx) {
    long t0 = System.currentTimeMillis();
    if (repo.existsById(tx.getTransactionId()))
      return;

    var res = RuleEngine.evaluate(tx);
    var reasons = new java.util.ArrayList<>(res.reasons()); // editable
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

    // C) Device/IP freshness: treat "new within X days" as risky
    if (tx.getDevice() != null) {
      boolean isNewDevice = redisState.recordDevice(tx.getUserId(), tx.getDevice().getId(), nowSec);
      if (isNewDevice || redisState.deviceSeenWithinDays(tx.getUserId(), tx.getDevice().getId(), nowSec, deviceNewWithinDays)) {
        score += deviceScore;
        reasons.add("new_device");
      }

      boolean isNewIp = redisState.recordIp(tx.getUserId(), tx.getDevice().getIp(), nowSec);
      if (isNewIp || redisState.ipSeenWithinDays(tx.getUserId(), tx.getDevice().getIp(), nowSec, ipNewWithinDays)) {
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

    // Final decision
    String decisionStr = RuleEngine.toDecision(Math.min(score, 100));

    long latency = System.currentTimeMillis() - t0;
    var decision = Decision.builder()
        .transactionId(tx.getTransactionId())
        .userId(tx.getUserId())
        .decision(decisionStr)
        .score(Math.min(score, 100))
        .reasons(reasons)
        .latencyMs(latency)
        .evaluatedAt(Instant.now())
        .build();

    decisionTemplate.send(outTopic, tx.getUserId(), decision);

    var csv = new java.util.StringJoiner("|");
    reasons.forEach(csv::add);
    repo.save(DecisionEntity.builder()
        .transactionId(tx.getTransactionId())
        .userId(tx.getUserId())
        .decision(decisionStr)
        .score(Math.min(score, 100))
        .reasonsCsv(csv.toString())
        .latencyMs(latency)
        .evaluatedAt(decision.getEvaluatedAt())
        .build());
  }
}
