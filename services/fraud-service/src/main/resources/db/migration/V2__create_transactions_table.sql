-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    currency VARCHAR(3),
    merchant_id VARCHAR(255),
    occurred_at TIMESTAMP,
    device_id VARCHAR(255),
    device_ip VARCHAR(45),
    device_user_agent TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    city VARCHAR(255),
    country VARCHAR(255),
    raw_payload TEXT
);

-- Create indexes (with IF NOT EXISTS to avoid conflicts)
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_occurred_at ON transactions(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, occurred_at DESC);