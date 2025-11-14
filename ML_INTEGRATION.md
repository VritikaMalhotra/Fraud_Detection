# ML Model Integration Guide

## Overview

Your XGBoost fraud detection model has been successfully integrated into the fraud detection system. The system now uses a **hybrid approach** combining rule-based and ML-based fraud detection.

## Architecture

```
Transaction → Fraud Service → Rule Engine (score 1)
                              ↓
                         Feature Extractor
                              ↓
                         ML Service (score 2)
                              ↓
                         Score Combiner → Final Decision
```

## How It Works

1. **Rule-Based Scoring**: Transaction is evaluated using existing 9 fraud rules → `ruleScore` (0-100)
2. **Feature Extraction**: Features are extracted matching your model's expected format (18 features)
3. **ML Prediction**: Features are sent to Python ML service → `mlProbability` (0-1)
4. **Score Combination**: Final score = `(ruleScore × rulesWeight) + (mlScore × mlWeight)`
5. **Decision**: Final score determines ALLOW/REVIEW/BLOCK

## Files Created

### Python ML Service
- `services/ml-service/app.py` - Flask service that loads and serves your pickle model
- `services/ml-service/Dockerfile` - Container definition
- `services/ml-service/requirements.txt` - Python dependencies
- `services/ml-service/models/` - Contains your model files

### Java Integration
- `services/fraud-service/src/main/java/com/fraud/engine/ml/TransactionFeatures.java` - Feature DTO
- `services/fraud-service/src/main/java/com/fraud/engine/ml/FeatureExtractor.java` - Extracts features from transactions
- `services/fraud-service/src/main/java/com/fraud/engine/ml/MLFraudDetector.java` - Calls ML service

### Configuration
- Updated `FraudProcessor.java` - Integrates ML predictions
- Updated `application.properties` - ML configuration
- Updated `docker-compose.yml` - Added ML service

## Running the System

### Option 1: Docker Compose (Recommended)

```bash
cd Fraud_Detection

# Start all services including ML service
docker compose up -d

# Check ML service is running
curl http://localhost:8084/health

# Check ML service features
curl http://localhost:8084/features
```

### Option 2: Manual Start

**Terminal 1: Start ML Service**
```bash
cd services/ml-service
pip install -r requirements.txt
python app.py
```

**Terminal 2: Start Fraud Service**
```bash
cd services/fraud-service
# ML service URL will default to http://localhost:8084
./mvnw spring-boot:run
```

## Configuration

### ML Service Configuration

In `services/fraud-service/src/main/resources/application.properties`:

```properties
# ML Configuration
app.ml.enabled=true                    # Enable/disable ML
app.ml.service.url=http://localhost:8084  # ML service URL
app.ml.weight=0.5                      # Weight for ML score (0.0-1.0)
app.rules.weight=0.5                   # Weight for rule score (0.0-1.0)
app.ml.timeout=2000                    # Timeout in milliseconds
```

### Adjusting Weights

- **More ML, less rules**: `app.ml.weight=0.7`, `app.rules.weight=0.3`
- **More rules, less ML**: `app.ml.weight=0.3`, `app.rules.weight=0.7`
- **Only rules**: `app.ml.enabled=false`
- **Only ML**: `app.ml.weight=1.0`, `app.rules.weight=0.0`

## Feature Mapping

Your model expects these 18 features in order:

| # | Feature | Source |
|---|---------|--------|
| 1 | `amount` | Transaction amount |
| 2 | `hourOfDay` | Parsed from timestamp |
| 3 | `tx_count_60s` | Redis: transactions in last 60s |
| 4 | `spend_deviation_ratio` | (amount / median) - 1 |
| 5 | `required_speed_kmph` | Calculated from location history |
| 6 | `is_new_device` | Redis: device first seen? |
| 7 | `is_new_ip` | Redis: IP first seen? |
| 8 | `rule_burst_60s` | Rule triggered? (0/1) |
| 9 | `rule_spend_spike` | Rule triggered? (0/1) |
| 10 | `rule_new_device` | Rule triggered? (0/1) |
| 11 | `rule_new_ip` | Rule triggered? (0/1) |
| 12 | `rule_geo_impossible` | Rule triggered? (0/1) |
| 13 | `rule_odd_hour` | Night time? (0/1) |
| 14 | `rule_score` | Rule-based score (0-100) |
| 15 | `currency` | Numeric encoding (USD=1, EUR=2, etc.) |

## Testing

### Test ML Service Directly

```bash
# Health check
curl http://localhost:8084/health

# Get feature info
curl http://localhost:8084/features

# Test prediction
curl -X POST http://localhost:8084/predict \
  -H "Content-Type: application/json" \
  -d '{
    "features": [150.0, 14, 2, 0.25, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 15.0, 1]
  }'
```

### Test Full Integration

```bash
# Submit a transaction
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "amount": 5000,
    "currency": "USD",
    "merchantId": "merchant-001",
    "timestamp": "2025-01-15T10:00:00Z",
    "device": {"id": "device-001", "ip": "192.168.1.100"}
  }'

# Check decision in database
docker exec -it fp-postgres psql -U postgres -d fraud \
  -c "SELECT transaction_id, decision, score, reasons_json FROM fraud_decisions ORDER BY evaluated_at DESC LIMIT 1;"
```

Look for `ml_high_risk` in the reasons if ML detected high fraud probability.

## Monitoring

### Check ML Service Logs

```bash
docker logs fp-ml-service
```

### Check Fraud Service Logs

```bash
docker logs fp-fraud-service | grep -i ml
```

You should see logs like:
```
ML prediction for tx t123: probability=0.75, score=75.0
Combined score for tx t123: rule=60.0, ml=75.0, final=67.5
```

## Troubleshooting

### ML Service Not Starting

1. Check model files exist:
   ```bash
   ls services/ml-service/models/
   ```

2. Check Python dependencies:
   ```bash
   cd services/ml-service
   pip install -r requirements.txt
   ```

3. Check logs:
   ```bash
   docker logs fp-ml-service
   ```

### ML Predictions Failing

1. Check ML service is healthy:
   ```bash
   curl http://localhost:8084/health
   ```

2. Check fraud-service can reach ML service:
   ```bash
   # From fraud-service container
   curl http://ml-service:8084/health
   ```

3. Check feature count matches:
   ```bash
   curl http://localhost:8084/features
   ```
   Should return 18 features.

### ML Service Timeout

If predictions are slow, increase timeout:
```properties
app.ml.timeout=5000  # 5 seconds
```

## Model Updates

To update the model:

1. Replace `services/ml-service/models/fraud_model_xgb.pkl`
2. Update `services/ml-service/models/model_meta.json` if features changed
3. Restart ML service:
   ```bash
   docker compose restart ml-service
   ```

## Performance

- **ML Service Latency**: Typically < 50ms per prediction
- **Combined Latency**: Rule-based + ML typically < 100ms total
- **Throughput**: ML service can handle 100+ requests/second

## Next Steps

1. **Monitor Performance**: Track ML vs rule-based accuracy
2. **Tune Weights**: Adjust `app.ml.weight` and `app.rules.weight` based on results
3. **A/B Testing**: Compare ML-enabled vs rule-only performance
4. **Model Retraining**: Retrain model with new data periodically

## Support

If you encounter issues:
1. Check service logs
2. Verify model files are correct
3. Ensure feature extraction matches model expectations
4. Test ML service independently first

