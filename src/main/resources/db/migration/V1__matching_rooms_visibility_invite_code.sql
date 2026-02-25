-- PR3: visibility + invite_code (기존 테이블에 컬럼 추가)
-- Hibernate ddl-auto=update 사용 시 자동 추가되나, 수동 마이그레이션 시 참고용.

-- PostgreSQL
ALTER TABLE matching_rooms
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS invite_code VARCHAR(8) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_matching_rooms_invite_code ON matching_rooms (invite_code) WHERE invite_code IS NOT NULL;

-- 기존 행: visibility는 이미 DEFAULT로 채워짐. invite_code는 NULL 허용(신규 생성 시에만 채움).
-- COMMENT: 신규 방은 애플리케이션에서 항상 inviteCode 생성. 기존 방은 NULL로 두어도 목록(PUBLIC)에는 노출됨.
