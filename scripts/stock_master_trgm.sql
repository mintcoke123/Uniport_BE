-- [옵션] pg_trgm 확장 + name_kr 부분일치 검색용 GIN 인덱스.
-- 확장/인덱스 생성 권한이 없으면 실패할 수 있으므로, 기본 스키마(scripts/stock_master.sql) 적용과 분리하여 실행.
-- 실패해도 전체 스키마 적용이 막히지 않도록 별도 파일로 제공.
-- 실행: psql $DATABASE_URL -f scripts/stock_master_trgm.sql

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_stock_master_name_kr_gin
 ON stock_master USING gin (name_kr gin_trgm_ops);
