-- Initial schema for fraud decisions
CREATE TABLE IF NOT EXISTS fraud_decisions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    decision VARCHAR(50) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    reasons_json TEXT,
    latency_ms BIGINT NOT NULL,
    evaluated_at TIMESTAMP NOT NULL
);

-- Create index on user_id for faster queries
CREATE INDEX idx_fraud_decisions_user_id ON fraud_decisions(user_id);

-- Create index on decision for filtering REVIEW/BLOCK
CREATE INDEX idx_fraud_decisions_decision ON fraud_decisions(decision);

-- Create index on evaluated_at for time-based queries
CREATE INDEX idx_fraud_decisions_evaluated_at ON fraud_decisions(evaluated_at DESC);

-- Create composite index for user + date range queries
CREATE INDEX idx_fraud_decisions_user_date ON fraud_decisions(user_id, evaluated_at DESC);
