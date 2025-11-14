# ML Fraud Detection Service

Python microservice that serves XGBoost model predictions for fraud detection.

## Overview

This service loads a pre-trained XGBoost model (pickle format) and provides REST API endpoints for fraud prediction. The fraud-service calls this service to get ML-based fraud probabilities.

## API Endpoints

### `GET /health`
Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "model_loaded": true,
  "model_version": "fraud-xgb-v1-1762986950"
}
```

### `POST /predict`
Predict fraud probability for a transaction.

**Request:**
```json
{
  "features": [150.0, 14, 2, 0.25, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 15.0, 1]
}
```

**Response:**
```json
{
  "fraud_probability": 0.15,
  "prediction": 0,
  "probabilities": [0.85, 0.10, 0.05],
  "model_version": "fraud-xgb-v1-1762986950"
}
```

### `GET /features`
Get expected feature names and model metadata.

**Response:**
```json
{
  "features": ["amount", "hourOfDay", "tx_count_60s", ...],
  "model_version": "fraud-xgb-v1-1762986950",
  "block_threshold": 0.457,
  "roc_auc": 0.829,
  "pr_auc": 0.480
}
```

## Features

The model expects 18 features in this exact order:
1. `amount` - Transaction amount
2. `hourOfDay` - Hour of day (0-23)
3. `tx_count_60s` - Transaction count in last 60 seconds
4. `spend_deviation_ratio` - (amount / median) - 1
5. `required_speed_kmph` - Travel speed if location exists
6. `is_new_device` - 0 or 1
7. `is_new_ip` - 0 or 1
8. `rule_burst_60s` - 0 or 1
9. `rule_spend_spike` - 0 or 1
10. `rule_new_device` - 0 or 1
11. `rule_new_ip` - 0 or 1
12. `rule_geo_impossible` - 0 or 1
13. `rule_odd_hour` - 0 or 1
14. `rule_score` - Rule-based score (0-100)
15. `currency` - Numeric encoding (USD=1, EUR=2, etc.)

## Running Locally

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
export MODEL_PATH=./models/fraud_model_xgb.pkl
export META_PATH=./models/model_meta.json
export PORT=8084

# Run service
python app.py
```

## Docker

```bash
# Build image
docker build -t ml-service .

# Run container
docker run -p 8084:8084 ml-service
```

## Integration

The fraud-service automatically calls this service when `app.ml.enabled=true`. The ML score is combined with rule-based scores using configurable weights.

## Model Files

- `fraud_model_xgb.pkl` - Trained XGBoost model (pickle format)
- `model_meta.json` - Model metadata (features, version, metrics)

