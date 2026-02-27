package com.uniport.runner;

import com.uniport.service.importer.StockMasterImportRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * importer 프로필에서만 StockMasterImportRunner 빈이 등록되는지 검증.
 * 기본 프로필에서는 빈이 없어야 하므로, 기본 프로필로 기동해 runner 빈 부재를 확인.
 */
@SpringBootTest
class StockMasterImportRunnerProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void defaultProfile_runnerBeanNotPresent() {
        boolean hasRunner = context.getBeanNamesForType(StockMasterImportRunner.class).length > 0;
        assertFalse(hasRunner, "StockMasterImportRunner must not be registered when importer profile is not active");
    }
}
