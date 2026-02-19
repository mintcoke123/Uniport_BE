-- Vote 엔티티 2-1/2-2 추가 컬럼 (order_strategy, limit_price, trigger_price, trigger_direction, execution_expires_at, executed_at)
-- DB에 해당 컬럼이 없을 때 실행. PostgreSQL / H2 호환.

-- PostgreSQL
ALTER TABLE votes ADD COLUMN IF NOT EXISTS order_strategy VARCHAR(20) NOT NULL DEFAULT 'MARKET';
ALTER TABLE votes ADD COLUMN IF NOT EXISTS limit_price NUMERIC(19,4);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS trigger_price NUMERIC(19,4);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS trigger_direction VARCHAR(10);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS execution_expires_at TIMESTAMP;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS executed_at TIMESTAMP;
