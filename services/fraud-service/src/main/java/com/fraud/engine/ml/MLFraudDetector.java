package com.fraud.engine.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * ML Fraud Detector - calls Python ML service for predictions
 */
@Slf4j
@Component
public class MLFraudDetector {
    
    @Value("${app.ml.service.url:http://localhost:8084}")
    private String mlServiceUrl;
    
    @Value("${app.ml.enabled:true}")
    private boolean mlEnabled;
    
    @Value("${app.ml.weight:0.5}")
    private double mlWeight; // Weight for ML score (0.0 to 1.0)
    
    @Value("${app.ml.timeout:2000}")
    private int timeoutMs;
    
    private final RestTemplate restTemplate;
    
    public MLFraudDetector() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Predict fraud probability using ML model
     * @param features Transaction features
     * @return Fraud probability (0.0 to 1.0), where 1.0 = high fraud risk
     */
    public double predictFraudProbability(TransactionFeatures features) {
        if (!mlEnabled) {
            log.debug("ML fraud detection is disabled");
            return 0.0; // Return neutral if ML is disabled
        }
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("features", features.toArray());
            
            String url = mlServiceUrl + "/predict";
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            
            if (response == null) {
                log.warn("ML service returned null response");
                return 0.0;
            }
            
            Object fraudProbObj = response.get("fraud_probability");
            if (fraudProbObj == null) {
                log.warn("ML service response missing fraud_probability");
                return 0.0;
            }
            
            double fraudProbability;
            if (fraudProbObj instanceof Number) {
                fraudProbability = ((Number) fraudProbObj).doubleValue();
            } else {
                fraudProbability = Double.parseDouble(fraudProbObj.toString());
            }
            
            log.debug("ML prediction: fraud_probability={}", fraudProbability);
            return fraudProbability;
            
        } catch (RestClientException e) {
            log.warn("Error calling ML service: {}", e.getMessage());
            return 0.0; // Return neutral on error
        } catch (Exception e) {
            log.error("Unexpected error in ML prediction", e);
            return 0.0;
        }
    }
    
    /**
     * Convert ML probability to score (0-100)
     */
    public double mlProbabilityToScore(double probability) {
        return probability * 100.0;
    }
    
    /**
     * Check if ML service is healthy
     */
    public boolean isHealthy() {
        if (!mlEnabled) {
            return false;
        }
        
        try {
            String url = mlServiceUrl + "/health";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null && "UP".equals(response.get("status"));
        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isEnabled() {
        return mlEnabled;
    }
    
    public double getMlWeight() {
        return mlWeight;
    }
}

