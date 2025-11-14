# Starting the Application with ML Integration

## Prerequisites

1. **Start Docker Desktop** - Required for infrastructure (PostgreSQL, Redis, Kafka)
2. **Java 21+** - For backend services
3. **Maven** - For building Java services
4. **Node.js 18+** - For frontend
5. **Python 3.11+ with dependencies** - For ML service

## Step-by-Step Startup

### Step 1: Start Docker Desktop
Make sure Docker Desktop is running on your Mac.

### Step 2: Start Infrastructure
```bash
cd "/Users/revatipathrudkar/Desktop/272 Project Fraud Detection System/Fraud_Detection"
docker compose up -d
```

Wait 10-15 seconds for services to initialize.

### Step 3: Verify Infrastructure
```bash
docker compose ps
```

You should see: postgres, redis, kafka, kafka-init, kafka-exporter, prometheus, grafana

### Step 4: Start ML Service

**Option A: Using Docker (Recommended)**
```bash
# ML service is already in docker-compose.yml
# It will start automatically with: docker compose up -d
```

**Option B: Manual Start (if Docker ML service has issues)**
```bash
cd services/ml-service

# Install dependencies (if not already installed)
pip3 install flask flask-cors xgboost numpy scikit-learn joblib

# Install OpenMP for XGBoost (Mac)
brew install libomp

# Start ML service
export MODEL_PATH=$(pwd)/models/fraud_model_xgb.pkl
export META_PATH=$(pwd)/models/model_meta.json
export PORT=8084
python3 app.py
```

Verify ML service:
```bash
curl http://localhost:8084/health
```

### Step 5: Build Common Models
```bash
cd "/Users/revatipathrudkar/Desktop/272 Project Fraud Detection System/Fraud_Detection"
mvn clean install -DskipTests
```

### Step 6: Start Backend Services

**Terminal 1: Ingest API**
```bash
cd services/ingest-api
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 \
REDIS_HOST=localhost REDIS_PORT=6380 \
./mvnw spring-boot:run
```

**Terminal 2: Fraud Service**
```bash
cd services/fraud-service
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 \
DB_URL=jdbc:postgresql://localhost:5543/fraud \
DB_USER=postgres DB_PASS=postgres \
REDIS_HOST=localhost REDIS_PORT=6380 \
ML_SERVICE_URL=http://localhost:8084 \
./mvnw spring-boot:run
```

**Terminal 3: Alerts Service (Optional)**
```bash
cd services/alerts-service
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 \
./mvnw spring-boot:run
```

### Step 7: Start Frontend
```bash
cd "../Fraud_Detection_ frontend"
npm install  # First time only
npm run dev
```

## Testing ML Integration

### Test 1: Check ML Service Health
```bash
curl http://localhost:8084/health
```

Expected:
```json
{
  "status": "UP",
  "model_loaded": true,
  "model_version": "fraud-xgb-v1-..."
}
```

### Test 2: Submit a Transaction
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "amount": 5000,
    "currency": "USD",
    "merchantId": "merchant-001",
    "timestamp": "2025-01-15T10:00:00Z",
    "device": {"id": "device-001", "ip": "192.168.1.100"}
  }'
```

### Test 3: Check Decision (with ML)
```bash
docker exec -it fp-postgres psql -U postgres -d fraud \
  -c "SELECT transaction_id, decision, score, reasons_json FROM fraud_decisions ORDER BY evaluated_at DESC LIMIT 1;"
```

Look for `ml_high_risk` in reasons_json if ML detected fraud.

### Test 4: View in Frontend
Open http://localhost:5173 and navigate to:
- Dashboard → See decision breakdown
- Decisions → See fraud decisions with scores
- Check if ML predictions are being used

## Troubleshooting

### ML Service Not Starting
1. Check Python version: `python3 --version` (should be 3.11+)
2. Install OpenMP: `brew install libomp`
3. Upgrade scikit-learn: `pip3 install --upgrade scikit-learn`
4. Check model file exists: `ls services/ml-service/models/`

### ML Service Returns Errors
- Check logs: `docker logs fp-ml-service` or check terminal output
- Verify model file is not corrupted
- Try reloading model: Restart ML service

### Fraud Service Can't Connect to ML Service
- Check ML service is running: `curl http://localhost:8084/health`
- Check fraud-service logs for connection errors
- Verify `ML_SERVICE_URL` environment variable

### Docker Not Starting
- Start Docker Desktop application
- Check Docker is running: `docker ps`

## Quick Start Script

Save this as `start-all.sh`:

```bash
#!/bin/bash
cd "/Users/revatipathrudkar/Desktop/272 Project Fraud Detection System/Fraud_Detection"

echo "Starting infrastructure..."
docker compose up -d
sleep 15

echo "Building common models..."
mvn clean install -DskipTests

echo "Services ready!"
echo "Start services manually in separate terminals:"
echo "1. Ingest API: cd services/ingest-api && ./mvnw spring-boot:run"
echo "2. Fraud Service: cd services/fraud-service && ./mvnw spring-boot:run"
echo "3. Frontend: cd ../Fraud_Detection_ frontend && npm run dev"
```

