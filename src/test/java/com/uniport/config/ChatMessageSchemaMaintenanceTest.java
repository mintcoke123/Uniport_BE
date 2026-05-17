package com.uniport.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessageSchemaMaintenanceTest {

    @Test
    void appliesChatMessageColumnCorrectionsOnStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChatMessageSchemaMaintenance maintenance = new ChatMessageSchemaMaintenance(jdbcTemplate);

        maintenance.run(null);

        verify(jdbcTemplate).execute(ChatMessageSchemaMaintenance.MESSAGE_TEXT_SQL);
        verify(jdbcTemplate).execute(ChatMessageSchemaMaintenance.USER_ID_NULLABLE_SQL);
    }

    @Test
    void continuesWithNextCorrectionWhenOneStatementFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doThrow(new DataAccessResourceFailureException("not available"))
                .when(jdbcTemplate)
                .execute(ChatMessageSchemaMaintenance.MESSAGE_TEXT_SQL);
        ChatMessageSchemaMaintenance maintenance = new ChatMessageSchemaMaintenance(jdbcTemplate);

        maintenance.run(null);

        verify(jdbcTemplate).execute(ChatMessageSchemaMaintenance.USER_ID_NULLABLE_SQL);
    }
}
