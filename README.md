# Fraud_Detection

- Forked from https://github.com/VritikaMalhotra/Fraud_Detection/tree/main
- Team project developed collaboratively as part of CMPE 272: Enterprise Software Platforms.

Real-time fraud detection system built using Redis and Kafka for high-speed event streaming and in-memory analysis. Deployed on AWS using Docker and Kubernetes for scalability and fault tolerance, enabling instant anomaly detection and automated risk alerts across transactions.

## 🖥️ Analyst Console (React)

A complementary analyst UI lives in `../frontend/`. It visualises live KPIs, recent decisions, alerts, and transactions using the running backend services.

```bash
# in a separate terminal
cd ../frontend
npm install
npm run dev
```

The dev server runs on [http://localhost:5173](http://localhost:5173). Backend endpoints are proxied to:
- `http://localhost:8082` (`fraud-service` REST APIs)
- `http://localhost:8080` (`ingest-api` for transaction submissions)

Override with `VITE_API_BASE` / `VITE_INGEST_BASE` environment variables if your services run elsewhere.

## ⚙️ Architecture Overview

Component | Description
---|---
Ingest API | Receives incoming transactions → validates → pushes JSON into Kafka (`payments.events`).
Fraud Service | Consumes `payments.events` → applies rules & Redis-based context checks → publishes decisions to `fraud.decisions` → saves to Postgres.
Kafka | Message backbone connecting microservices.
Redis | Fast in-memory store for user/device state and recent-activity windows.
PostgreSQL | Persists all fraud-decision records for audit & analytics.
Docker Compose | Spins up Kafka + Redis + Postgres locally with isolated ports.

---

## 🧩 Fraud-Scoring Logic

Every transaction is given a risk score (0–100).
Final decision thresholds:

Score Range | Decision | Description
---|---|---
0–29 | ALLOW | Normal behaviour.
30–59 | REVIEW | Borderline / needs manual check.
60+ | BLOCK | High risk fraud pattern.

### 🔍 Rules (Score Contributors)

Rule | Condition | Score Impact | Reason Tag
---|---:|---:|---
High Amount | amount ≥ 1000 | +60 | high_amount
Invalid Amount | ≤ 0 | +100 | invalid_amount
Bad Currency | currency missing or invalid | +40 | bad_currency
Night Time | hour 00 – 05 UTC | +20 | night_time
Burst | ≥ 3 tx within 60 s (configurable) | +40 | burst_60s
**Spend Spike** | **amount ≥ 5× user's median** | **+30** | **spend_spike**
New Device | first-seen within 7 days (configurable) | +20 | new_device
New IP | first-seen within 7 days (configurable) | +15 | new_ip
Geo-Impossible | Travel speed > 900 km/h (configurable) | +50 | geo_impossible

Total score = sum of triggered rule points (clamped to 100).

**🎛️ Configurable Thresholds:** All fraud rules are now configurable via `application.properties` - no code changes needed to adjust sensitivity!

---

## 🚀 Setup Guide

1️⃣ Clone Repository

```bash
git clone https://github.com/VritikaMalhotra/Fraud_Detection.git
cd Fraud_Detection
```

2️⃣ Environment File

```bash
cp .env.example .env
```

Ensure `.env` contains:

```
COMPOSE_PROJECT_NAME=fraud_detection
PG_PORT=5543
REDIS_PORT=6380
KAFKA_OUTSIDE_PORT=9094
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_DB=fraud
```

3️⃣ Start Infrastructure

```bash
docker compose --env-file .env up -d
docker compose ps
```

Expected containers: `fp-postgres`, `fp-redis`, `fp-kafka`.

4️⃣ Run Services

Fraud Service

```bash
cd services/fraud-service
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 \
DB_URL=jdbc:postgresql://localhost:5543/fraud \
DB_USER=postgres DB_PASS=postgres \
REDIS_HOST=localhost REDIS_PORT=6380 \
mvn spring-boot:run
```

Ingest API

```bash
cd services/ingest-api
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 mvn spring-boot:run
```

---

## ⚙️ Configuring Fraud Rules

All fraud detection thresholds can be tuned in `services/fraud-service/src/main/resources/application.properties`:

```properties
# Burst detection
app.rules.burst.windowSec=60      # Time window in seconds
app.rules.burst.count=3           # Max transactions allowed
app.rules.burst.score=40          # Score penalty

# Spend spike detection
app.rules.spend.multiplier=5.0    # Alert if ≥ 5× user's median
app.rules.spend.score=30
app.rules.spend.historySize=10    # Track last N transactions

# Device/IP freshness
app.rules.device.newWithinDays=7  # Treat as "new" for 7 days
app.rules.device.score=20
app.rules.ip.newWithinDays=7
app.rules.ip.score=15

# Geo-impossibility
app.rules.geo.maxSpeedKmph=900    # Speed threshold
app.rules.geo.score=50
```

**To adjust:** Edit values → Restart fraud-service → Changes apply immediately!

---

## 🧪 Testing the System with cURL

1. Normal Transaction (Allowed)

```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t101","userId":"u1","amount":120,
      "currency":"USD","merchantId":"m1",
      "timestamp":"2025-11-05T12:00:00Z",
      "device":{"id":"dev1","ip":"203.0.113.10"}}'
```

Expected: Decision = ALLOW

2. Borderline Transaction (Review)

```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t102","userId":"u1","amount":800,
      "currency":"USD","merchantId":"m1",
      "timestamp":"2025-11-05T02:30:00Z",
      "device":{"id":"dev1","ip":"203.0.113.10"}}'
```

Triggers: Night time (+20) + moderate amount.
Expected: Decision ≈ REVIEW

3. High-Amount Transaction (Blocked)

```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t103","userId":"u2","amount":5000,
      "currency":"USD","merchantId":"m2",
      "timestamp":"2025-11-05T10:00:00Z",
      "device":{"id":"dev2","ip":"203.0.113.11"}}'
```

Triggers: High amount (+60).
Expected: Decision = BLOCK, reason `high_amount`.

4. Burst Activity (Blocked)

```bash
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/transactions \
   -H "Content-Type: application/json" \
   -d "{\"transactionId\":\"tb$i\",\"userId\":\"u3\",\"amount\":50,
        \"currency\":\"USD\",\"merchantId\":\"m3\",
        \"timestamp\":\"2025-11-05T10:00:00Z\",
        \"device\":{\"id\":\"dev3\",\"ip\":\"203.0.113.12\"}}"
done
```

Triggers: 3 tx in < 60 s (+40).
Expected: Last transaction BLOCK with reason `burst_60s`.

5. New Device or IP (Block / Review)

```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t104","userId":"u4","amount":90,
      "currency":"USD","merchantId":"m4",
      "timestamp":"2025-11-05T12:00:00Z",
      "device":{"id":"newDevice","ip":"203.0.113.200"}}'
```

Triggers: First-seen device (+20) / IP (+15).
Expected: Decision = REVIEW or BLOCK.

6. Geo-Impossible Travel (Blocked)

# 1st tx – New York
```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t105a","userId":"u5","amount":100,
      "currency":"USD","merchantId":"m5",
      "timestamp":"2025-11-05T10:00:00Z",
      "location":{"lat":40.7128,"lon":-74.0060}}'
```

# 2nd tx – Tokyo, only 5 min later
```bash
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"t105b","userId":"u5","amount":80,
      "currency":"USD","merchantId":"m5",
      "timestamp":"2025-11-05T10:05:00Z",
      "location":{"lat":35.6762,"lon":139.6503}}'
```

Triggers: Geo-impossible (+50).
Expected: Decision = BLOCK with reason `geo_impossible`.

7. Spend Spike Detection (Blocked)

```bash
# Build baseline spending pattern
for amt in 25 30 28 35 32; do
  curl -s -X POST http://localhost:8080/transactions \
   -H "Content-Type: application/json" \
   -d "{\"transactionId\":\"base-$amt\",\"userId\":\"u6\",\"amount\":$amt,
        \"currency\":\"USD\",\"merchantId\":\"m6\",
        \"timestamp\":\"2025-11-05T12:00:00Z\",
        \"device\":{\"id\":\"dev6\",\"ip\":\"203.0.113.15\"}}"
  sleep 1
done

# Sudden spike transaction (median ~$30, spike $200 = 6.67×)
curl -X POST http://localhost:8080/transactions \
 -H "Content-Type: application/json" \
 -d '{"transactionId":"spike-1","userId":"u6","amount":200,
      "currency":"USD","merchantId":"m6",
      "timestamp":"2025-11-05T14:00:00Z",
      "device":{"id":"dev6","ip":"203.0.113.15"}}'
```

Triggers: Spend spike (+30).
Expected: Decision = BLOCK with reason `spend_spike`.

---

## 📊 Viewing Results

Kafka Topics

```bash
docker exec -it fp-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic fraud.decisions \
  --from-beginning
```

PostgreSQL Table

```bash
docker exec -it fp-postgres psql -U postgres -d fraud \
  -c "SELECT transaction_id, user_id, decision, score, reasons_csv, evaluated_at FROM fraud_decisions ORDER BY evaluated_at DESC LIMIT 10;"
```

---

## ✅ Expected Decision Distribution (Example)

Transaction Type | Triggered Rules | Approx. Score | Decision
---|---|---:|---
Normal small tx | None | 10 | ALLOW
Late-night moderate tx | night_time | 35 | REVIEW
High-value | high_amount | 60 | BLOCK
**Spending spike** | **spend_spike** | **30 – 60** | **REVIEW / BLOCK**
Burst activity | burst_60s | 70 | BLOCK
New device/IP | new_device, new_ip | 50 – 60 | REVIEW / BLOCK
Geo-impossible | geo_impossible | 90 | BLOCK

---

## 🆕 What's New

**Configurable Fraud Rules:**
- All thresholds now configurable in `application.properties`
- No code changes needed to adjust fraud sensitivity
- Quick iteration: edit config → restart → done!

**Spend Spike Detection:**
- Tracks user's spending history (last 10 transactions)
- Calculates median spending pattern
- Flags transactions ≥ 5× normal spending as suspicious
- Example: User normally spends $30, suddenly $200 → Flagged!

**Smart Device/IP Trust:**
- Devices/IPs are "new" only for first 7 days
- After 7 days, they become "trusted"
- Reduces false positives for regular users
