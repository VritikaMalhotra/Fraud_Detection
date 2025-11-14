package com.fraud.engine.ml;

import lombok.Builder;
import lombok.Data;

/**
 * Features extracted from transaction for ML model prediction
 * Features must match the order in model_meta.json
 */
@Data
@Builder
public class TransactionFeatures {
    // Feature order matches model_meta.json:
    // ["amount", "hourOfDay", "tx_count_60s", "spend_deviation_ratio",
    //  "required_speed_kmph", "is_new_device", "is_new_ip", "rule_burst_60s",
    //  "rule_spend_spike", "rule_new_device", "rule_new_ip", "rule_geo_impossible",
    //  "rule_odd_hour", "rule_score", "currency"]
    
    private double amount;
    private int hourOfDay;
    private long txCount60s;
    private double spendDeviationRatio;
    private double requiredSpeedKmph;
    private int isNewDevice;  // 0 or 1
    private int isNewIp;     // 0 or 1
    private int ruleBurst60s;  // 0 or 1
    private int ruleSpendSpike; // 0 or 1
    private int ruleNewDevice;  // 0 or 1
    private int ruleNewIp;      // 0 or 1
    private int ruleGeoImpossible; // 0 or 1
    private int ruleOddHour;    // 0 or 1
    private double ruleScore;
    private int currency;      // Numeric encoding
    
    /**
     * Convert to array for ML model input
     * Order must match model_meta.json features array exactly
     */
    public double[] toArray() {
        return new double[]{
            amount,
            hourOfDay,
            txCount60s,
            spendDeviationRatio,
            requiredSpeedKmph,
            isNewDevice,
            isNewIp,
            ruleBurst60s,
            ruleSpendSpike,
            ruleNewDevice,
            ruleNewIp,
            ruleGeoImpossible,
            ruleOddHour,
            ruleScore,
            currency
        };
    }
}

