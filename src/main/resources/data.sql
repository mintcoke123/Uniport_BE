-- 샘플 데이터: 개발/테스트용 기본 사용자 (비밀번호: password)
-- 명세 §1: email 로그인, nickname 표시. 기동 시 CommandLineRunner가 있으면 비밀번호 동기화.
INSERT INTO users (email, password, nickname, total_assets, investment_amount, profit_loss, profit_loss_rate, username) VALUES
('test@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test User', 10000000, 10000000, 0, 0, 'test@example.com');
