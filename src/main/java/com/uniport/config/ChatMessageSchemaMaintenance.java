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
public class ChatMessageSchemaMaintenance implements ApplicationRunner {

    static final String MESSAGE_TEXT_SQL = "ALTER TABLE IF EXISTS chat_messages ALTER COLUMN message TYPE TEXT";
    static final String USER_ID_NULLABLE_SQL = "ALTER TABLE IF EXISTS chat_messages ALTER COLUMN user_id DROP NOT NULL";

    private static final Logger log = LoggerFactory.getLogger(ChatMessageSchemaMaintenance.class);

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageSchemaMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        apply(MESSAGE_TEXT_SQL);
        apply(USER_ID_NULLABLE_SQL);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("[chat-message-schema] applied correction: {}", sql);
        } catch (DataAccessException e) {
            log.warn("[chat-message-schema] correction failed: sql={} error={}", sql, e.getMessage());
        }
    }
}
