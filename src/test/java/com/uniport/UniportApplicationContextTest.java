package com.uniport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:uniport-context-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.h2.console.enabled=false",
                "stock.master.import.enabled=false",
                "asset.master.us.import.enabled=false"
        }
)
class UniportApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
