package com.uniport.scheduler;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL 세션 단위 advisory lock 유틸.
 * lock/unlock은 동일 connection에서 호출해야 하므로, 호출부에서 @Transactional 등으로 같은 connection을 유지해야 한다.
 */
public final class PostgresAdvisoryLock {

    private PostgresAdvisoryLock() {
    }

    /**
     * 비차단 lock 시도. 성공 시 true.
     * 같은 connection에서 unlock 호출 필요.
     */
    public static boolean tryLock(JdbcTemplate jdbcTemplate, long key) {
        Boolean result = jdbcTemplate.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 세션에서 해당 key의 advisory lock 해제.
     * 반드시 tryLock으로 획득한 같은 connection에서 호출해야 한다.
     */
    public static void unlock(JdbcTemplate jdbcTemplate, long key) {
        try {
            jdbcTemplate.update("SELECT pg_advisory_unlock(?)", key);
        } catch (Exception e) {
            // 이미 해제됐거나 다른 connection이면 실패할 수 있음. 무시
        }
    }
}
