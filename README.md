# Fraud_Detection

Real-time fraud detection system built using Redis and Kafka for high-speed event streaming and in-memory analysis. Deployed on AWS using Docker and Kubernetes for scalability and fault tolerance, enabling instant anomaly detection and automated risk alerts across transactions.

## Setup Guide

### 1️⃣ Repository Setup

```bash
# Clone the repository (only once)
git clone https://github.com/your-team/fraud-detection-system.git

# Move into the project folder
cd Fraud_Detection

# Fetch and switch to Renuka's branch
git fetch origin renuka
git checkout renuka

# Get latest changes (whenever needed)
git pull origin renuka
```

### 2️⃣ Python Environment (Optional but Recommended)

This keeps Python packages (for ML or data scripts) separate from your system.

```bash
# Create virtual environment
python3 -m venv .venv

# Activate (Mac/Linux)
source .venv/bin/activate

# To deactivate when done
deactivate
```

### 3️⃣ Environment Configuration

We use `.env` for local settings. This file is not committed to Git for security.

1. Create your local environment file:
   ```bash
   cp .env.example .env
   ```

2. Ensure your `.env` contains:
   ```
   COMPOSE_PROJECT_NAME=fraud_detection
   PG_PORT=5543
   REDIS_PORT=6380
   KAFKA_OUTSIDE_PORT=9094
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
   POSTGRES_DB=fraud
   ```
   *Note: Modify ports if they conflict with existing services on your machine.*

### 4️⃣ Infrastructure Setup

Start all services:
```bash
docker compose --env-file .env up -d
```

Check running containers:
```bash
docker compose ps
```

Expected Services:
| Service | Host Port | Status |
|---------|-----------|--------|
| fp-postgres | 5543 → 5432 | Up |
| fp-redis | 6380 → 6379 | Up |
| fp-kafka | 9094 → 9094 | Up |
| fp-kafka-init | (exits after creating topics) | Done |

### 5️⃣ Verify Services

1. **PostgreSQL**:
   ```bash
   psql -h localhost -p 5543 -U postgres -d fraud -c "SELECT 1;"
   ```
   *Should display a table with 1*

2. **Redis**:
   ```bash
   redis-cli -p 6380 PING
   ```
   *Should reply with: `PONG`*

3. **Kafka** (optional):
   ```bash
   docker exec -it fp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
   ```
   *Expected topics:*
   - payments.events
   - fraud.decisions
   - payments.dlq

### 6️⃣ Management Commands

```bash
# Stop services (preserve data)
docker compose down

# Restart services
docker compose --env-file .env up -d

# Full reset (delete all data)
docker compose down -v
```