-- stock_master 테이블 생성 (PostgreSQL).
-- 실행: psql $DATABASE_URL -f scripts/stock_master.sql
-- 또는 배포 플랫폼 DB 콘솔에서 실행.
-- 적용 순서: 1) 본 파일 → 2) (선택) scripts/stock_master_trgm.sql

CREATE TABLE IF NOT EXISTS stock_master (
    code     VARCHAR(6)  NOT NULL PRIMARY KEY,
    std_code VARCHAR(12) NULL,
    name_kr  VARCHAR    NOT NULL,
    market   VARCHAR(10) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE stock_master IS '종목 마스터 (6자리 단축코드 기준). market: KOSPI, KOSDAQ';
COMMENT ON COLUMN stock_master.code IS '6자리 단축코드 PK';
COMMENT ON COLUMN stock_master.std_code IS '12자리 표준코드(선택)';
COMMENT ON COLUMN stock_master.name_kr IS '종목명(한글)';
COMMENT ON COLUMN stock_master.market IS '시장 구분: KOSPI, KOSDAQ';
COMMENT ON COLUMN stock_master.updated_at IS '최종 반영 시각';

CREATE INDEX IF NOT EXISTS idx_stock_master_name_kr ON stock_master (name_kr);
