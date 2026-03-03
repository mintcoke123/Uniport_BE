-- 배포 DB votes 테이블에 누락 컬럼 추가 (Postgres).
-- 실행: psql $DATABASE_URL -f scripts/add-vote-columns-prod.sql
-- 또는 배포 플랫폼 DB 콘솔에서 실행.

ALTER TABLE votes ADD COLUMN IF NOT EXISTS order_strategy varchar(20) NOT NULL DEFAULT 'MARKET';
ALTER TABLE votes ADD COLUMN IF NOT EXISTS limit_price numeric(19,4);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS trigger_price numeric(19,4);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS trigger_direction varchar(10);
ALTER TABLE votes ADD COLUMN IF NOT EXISTS execution_expires_at timestamptz;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS executed_at timestamptz;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS execution_price numeric(19,4);

-- 채팅 메시지-투표 1:1 매칭 (중복 카드 방지). chat_messages 테이블에 vote_id 추가.
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS vote_id bigint;
