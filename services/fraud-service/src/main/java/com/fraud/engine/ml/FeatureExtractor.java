package com.fraud.engine.ml;

import com.fraud.common.model.Transaction;
import com.fraud.engine.redis.RedisState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts features from transaction for ML model prediction
 * Features match the order in model_meta.json
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureExtractor {
    
    private final RedisState redisState;
    
    // Currency encoding map
    private static final Map<String, Integer> CURRENCY_MAP = new HashMap<>();
    static {
        CURRENCY_MAP.put("USD", 1);
        CURRENCY_MAP.put("EUR", 2);
        CURRENCY_MAP.put("GBP", 3);
        CURRENCY_MAP.put("CAD", 4);
        CURRENCY_MAP.put("AUD", 5);
        // Default to 0 for unknown currencies
    }
    
    /**
     * Extract features from transaction for ML model
     * @param tx Transaction
     * @param currentTimeSec Current timestamp in seconds
     * @param ruleScore Rule-based score (0-100)
     * @param triggeredRules List of triggered rule names
     * @return TransactionFeatures for ML prediction
     */
    public TransactionFeatures extractFeatures(
            Transaction tx, 
            long currentTimeSec,
            double ruleScore,
            java.util.List<String> triggeredRules) {
        
        // Parse timestamp
        Instant txInstant = parseTimestamp(tx.getTimestamp());
        ZonedDateTime txDateTime = txInstant != null ? 
            ZonedDateTime.ofInstant(txInstant, java.time.ZoneId.of("UTC")) : 
            ZonedDateTime.now();
        
        int hourOfDay = txDateTime.getHour();
        boolean isOddHour = hourOfDay >= 0 && hourOfDay <= 5; // Night time
        
        // 1. amount
        double amount = tx.getAmount();
        
        // 2. hourOfDay
        int hour = hourOfDay;
        
        // 3. tx_count_60s - transactions in last 60 seconds
        long txCount60s = redisState.recentCount(tx.getUserId(), currentTimeSec, 60);
        
        // 4. spend_deviation_ratio - (amount / median) - 1, or 0 if no median
        double medianAmount = redisState.getMedianAmount(tx.getUserId());
        double spendDeviationRatio = medianAmount > 0 ? (amount / medianAmount) - 1.0 : 0.0;
        
        // 5. required_speed_kmph - travel speed if location exists
        double requiredSpeedKmph = 0.0;
        if (tx.getLocation() != null && tx.getLocation().getLat() != null && 
            tx.getLocation().getLon() != null) {
            var lastLoc = redisState.getLastLoc(tx.getUserId());
            if (lastLoc != null) {
                double distanceKm = RedisState.haversineKm(
                    lastLoc.lat(), lastLoc.lon(),
                    tx.getLocation().getLat(), tx.getLocation().getLon());
                long timeDiff = currentTimeSec - lastLoc.epochSec();
                if (timeDiff > 0) {
                    requiredSpeedKmph = distanceKm / (timeDiff / 3600.0);
                }
            }
        }
        
        // 6. is_new_device - 0 or 1
        boolean isNewDevice = false;
        if (tx.getDevice() != null && tx.getDevice().getId() != null) {
            isNewDevice = redisState.recordDevice(tx.getUserId(), tx.getDevice().getId(), currentTimeSec);
        }
        
        // 7. is_new_ip - 0 or 1
        boolean isNewIp = false;
        if (tx.getDevice() != null && tx.getDevice().getIp() != null) {
            isNewIp = redisState.recordIp(tx.getUserId(), tx.getDevice().getIp(), currentTimeSec);
        }
        
        // 8-12. Rule flags (0 or 1) - check if rule was triggered
        int ruleBurst60s = triggeredRules.contains("burst_60s") ? 1 : 0;
        int ruleSpendSpike = triggeredRules.contains("spend_spike") ? 1 : 0;
        int ruleNewDevice = triggeredRules.contains("new_device") ? 1 : 0;
        int ruleNewIp = triggeredRules.contains("new_ip") ? 1 : 0;
        int ruleGeoImpossible = triggeredRules.contains("geo_impossible") ? 1 : 0;
        
        // 13. rule_odd_hour - 0 or 1
        int ruleOddHour = isOddHour ? 1 : 0;
        
        // 14. rule_score - rule-based score (0-100)
        double ruleScoreValue = ruleScore;
        
        // 15. currency - numeric encoding
        int currency = CURRENCY_MAP.getOrDefault(tx.getCurrency(), 0);
        
        return TransactionFeatures.builder()
            .amount(amount)
            .hourOfDay(hour)
            .txCount60s(txCount60s)
            .spendDeviationRatio(spendDeviationRatio)
            .requiredSpeedKmph(requiredSpeedKmph)
            .isNewDevice(isNewDevice ? 1 : 0)
            .isNewIp(isNewIp ? 1 : 0)
            .ruleBurst60s(ruleBurst60s)
            .ruleSpendSpike(ruleSpendSpike)
            .ruleNewDevice(ruleNewDevice)
            .ruleNewIp(ruleNewIp)
            .ruleGeoImpossible(ruleGeoImpossible)
            .ruleOddHour(ruleOddHour)
            .ruleScore(ruleScoreValue)
            .currency(currency)
            .build();
    }
    
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            try {
                return Instant.from(ZonedDateTime.parse(timestamp));
            } catch (Exception ex) {
                log.warn("Failed to parse timestamp: {}", timestamp);
                return Instant.now();
            }
        }
    }
}

