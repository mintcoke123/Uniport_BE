package com.uniport.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class MatchingRoomMemberSchemaMaintenance implements ApplicationRunner {

    static final String ADD_JOINED_AT_SQL =
            "ALTER TABLE IF EXISTS matching_room_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP WITH TIME ZONE";
    static final String ADD_LAST_READ_AT_SQL =
            "ALTER TABLE IF EXISTS matching_room_members ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMP WITH TIME ZONE";
    static final String FILL_JOINED_AT_SQL =
            "UPDATE matching_room_members SET joined_at = COALESCE(joined_at, NOW()) WHERE joined_at IS NULL";
    static final String FILL_LAST_READ_AT_SQL =
            "UPDATE matching_room_members SET last_read_at = COALESCE(last_read_at, joined_at, NOW()) WHERE last_read_at IS NULL";
    static final String JOINED_AT_NOT_NULL_SQL =
            "ALTER TABLE IF EXISTS matching_room_members ALTER COLUMN joined_at SET NOT NULL";

    private static final Logger log = LoggerFactory.getLogger(MatchingRoomMemberSchemaMaintenance.class);

    private final JdbcTemplate jdbcTemplate;

    public MatchingRoomMemberSchemaMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        apply(ADD_JOINED_AT_SQL);
        apply(ADD_LAST_READ_AT_SQL);
        apply(FILL_JOINED_AT_SQL);
        apply(FILL_LAST_READ_AT_SQL);
        apply(JOINED_AT_NOT_NULL_SQL);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("[matching-room-member-schema] applied correction: {}", sql);
        } catch (DataAccessException e) {
            log.warn("[matching-room-member-schema] correction failed: sql={} error={}", sql, e.getMessage());
        }
    }
}
