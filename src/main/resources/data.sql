-- 샘플 데이터: 개발/테스트용 (비밀번호: password). CommandLineRunner가 비밀번호 동기화.
INSERT INTO users (student_id, username, password, nickname, total_assets, investment_amount, profit_loss, profit_loss_rate) VALUES
('25000002', '25000002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test User', 10000000, 10000000, 0, 0);

-- 종목 검색(API)용 stock_master 시드. 비어 있으면 검색 결과 없음. 전체 목록은 importer 프로필 1회 실행으로 적재.
-- H2: MERGE로 재기동 시에도 중복 오류 없음.
MERGE INTO stock_master (code, std_code, name_kr, market, updated_at) KEY(code) VALUES
('005930', NULL, '삼성전자', 'KOSPI', CURRENT_TIMESTAMP),
('000660', NULL, 'SK하이닉스', 'KOSPI', CURRENT_TIMESTAMP),
('035420', NULL, 'NAVER', 'KOSPI', CURRENT_TIMESTAMP),
('051910', NULL, 'LG화학', 'KOSPI', CURRENT_TIMESTAMP),
('006400', NULL, '삼성SDI', 'KOSPI', CURRENT_TIMESTAMP),
('035720', NULL, '카카오', 'KOSPI', CURRENT_TIMESTAMP),
('068270', NULL, '셀트리온', 'KOSPI', CURRENT_TIMESTAMP),
('207940', NULL, '삼성바이오로직스', 'KOSPI', CURRENT_TIMESTAMP),
('005380', NULL, '현대차', 'KOSPI', CURRENT_TIMESTAMP),
('000270', NULL, '기아', 'KOSPI', CURRENT_TIMESTAMP),
('105560', NULL, 'KB금융', 'KOSPI', CURRENT_TIMESTAMP),
('055550', NULL, '신한지주', 'KOSPI', CURRENT_TIMESTAMP),
('032830', NULL, '삼성생명', 'KOSPI', CURRENT_TIMESTAMP),
('003550', NULL, 'LG', 'KOSPI', CURRENT_TIMESTAMP),
('012330', NULL, '현대모비스', 'KOSPI', CURRENT_TIMESTAMP),
('066570', NULL, 'LG전자', 'KOSPI', CURRENT_TIMESTAMP),
('000810', NULL, '삼성화재', 'KOSPI', CURRENT_TIMESTAMP),
('009150', NULL, '삼성전기', 'KOSPI', CURRENT_TIMESTAMP),
('247540', NULL, '에코플라스틱', 'KOSDAQ', CURRENT_TIMESTAMP),
('086520', NULL, '에코프로비엠', 'KOSDAQ', CURRENT_TIMESTAMP);
