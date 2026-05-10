-- 통합 자산 마스터 테이블.
-- 국내 주식은 stock_master에서 동기화하고, 미국 주식/채권/현금은 이 테이블을 기준으로 ETF 검색/검증한다.

CREATE TABLE IF NOT EXISTS asset_master (
    asset_id VARCHAR(80) PRIMARY KEY,
    asset_type VARCHAR(20) NOT NULL,
    name VARCHAR(160) NOT NULL,
    symbol VARCHAR(40) NOT NULL,
    market VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    backtest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    price_source_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    last_price_verified_at TIMESTAMP NULL,
    last_price_error VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE asset_master ADD COLUMN IF NOT EXISTS backtest_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE asset_master ADD COLUMN IF NOT EXISTS price_source_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_VERIFICATION';
ALTER TABLE asset_master ADD COLUMN IF NOT EXISTS last_price_verified_at TIMESTAMP NULL;
ALTER TABLE asset_master ADD COLUMN IF NOT EXISTS last_price_error VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS idx_asset_master_search
    ON asset_master (asset_type, market, symbol, name);

CREATE INDEX IF NOT EXISTS idx_asset_master_active
    ON asset_master (active);

CREATE INDEX IF NOT EXISTS idx_asset_master_backtest_enabled
    ON asset_master (backtest_enabled, price_source_status);

CREATE TABLE IF NOT EXISTS asset_alias (
    id BIGSERIAL PRIMARY KEY,
    asset_id VARCHAR(80) NOT NULL,
    alias VARCHAR(160) NOT NULL,
    locale VARCHAR(20) NOT NULL,
    source VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_alias_asset_alias UNIQUE (asset_id, alias)
);

CREATE INDEX IF NOT EXISTS idx_asset_alias_lookup
    ON asset_alias (alias);

CREATE INDEX IF NOT EXISTS idx_asset_alias_asset
    ON asset_alias (asset_id);

CREATE TABLE IF NOT EXISTS asset_price_daily (
    id BIGSERIAL PRIMARY KEY,
    asset_id VARCHAR(80) NOT NULL,
    trade_date DATE NOT NULL,
    close_krw NUMERIC(20, 6) NOT NULL,
    close_native NUMERIC(20, 6) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    source VARCHAR(60) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_price_daily_asset_date UNIQUE (asset_id, trade_date)
);

CREATE INDEX IF NOT EXISTS idx_asset_price_daily_lookup
    ON asset_price_daily (asset_id, trade_date);

CREATE INDEX IF NOT EXISTS idx_asset_price_daily_date
    ON asset_price_daily (trade_date);

CREATE TABLE IF NOT EXISTS fx_rate_daily (
    id BIGSERIAL PRIMARY KEY,
    currency VARCHAR(10) NOT NULL,
    rate_date DATE NOT NULL,
    krw_rate NUMERIC(20, 6) NOT NULL,
    source VARCHAR(60) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fx_rate_daily_currency_date UNIQUE (currency, rate_date)
);

CREATE INDEX IF NOT EXISTS idx_fx_rate_daily_lookup
    ON fx_rate_daily (currency, rate_date);
